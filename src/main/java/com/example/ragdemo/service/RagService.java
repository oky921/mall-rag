package com.example.ragdemo.service;

import com.example.ragdemo.dto.RagChatResponse;
import com.example.ragdemo.dto.RagChatTurn;
import com.example.ragdemo.dto.RagDocumentRequest;
import com.example.ragdemo.dto.RagIngestResponse;
import com.example.ragdemo.dto.RagQueryRequest;
import com.example.ragdemo.dto.RagSearchResponse;
import com.example.ragdemo.dto.RagSearchResult;
import com.example.ragdemo.exception.AiServiceException;
import com.example.ragdemo.exception.BadRequestException;
import com.example.ragdemo.rerank.RerankDocument;
import com.example.ragdemo.rerank.RerankModel;
import com.example.ragdemo.rerank.RerankResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnProperty(name = "spring.ai.vectorstore.type", havingValue = "milvus")
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private static final int DEFAULT_TOP_K = 4;

    private static final int MAX_TOP_K = 10;

    private static final int MAX_HISTORY_TURNS = 8;

    private static final int EMBEDDING_BATCH_SIZE = 10;

    private static final Pattern PRODUCT_ID_PATTERN = Pattern.compile("\\bP\\d{5}\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern BUDGET_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*元?\\s*(以内|以下|之内|内)");

    private static final String NO_KNOWLEDGE_RESPONSE =
            "当前知识库中没有找到足够相关的信息，暂时无法确认。请补充更具体的商品名称、编号或需求。";

    private final VectorStore vectorStore;

    private final ChatClient chatClient;

    private final RerankModel rerankModel;

    private final double maxDistance;

    public RagService(VectorStore vectorStore, ChatClient chatClient, RerankModel rerankModel,
            @Value("${app.rag.max-distance:0.55}") double maxDistance) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
        this.rerankModel = rerankModel;
        this.maxDistance = maxDistance;
    }

    public RagIngestResponse ingest(RagDocumentRequest request) {
        return ingestAll(List.of(request));
    }

    public RagIngestResponse ingestAll(List<RagDocumentRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new BadRequestException("documents cannot be empty");
        }

        List<Document> documents = requests.stream()
                .map(this::toDocument)
                .toList();
        try {
            for (int start = 0; start < documents.size(); start += EMBEDDING_BATCH_SIZE) {
                int end = Math.min(start + EMBEDDING_BATCH_SIZE, documents.size());
                vectorStore.add(documents.subList(start, end));
            }
            return RagIngestResponse.ok(documents.size());
        } catch (RuntimeException ex) {
            throw new AiServiceException("Failed to write document to Milvus. Check Milvus connection and embedding settings.", ex);
        }
    }

    public RagSearchResponse search(RagQueryRequest request) {
        return RagSearchResponse.ok(searchDocuments(request));
    }

    public RagChatResponse chat(RagQueryRequest request) {
        String question = normalizeMessage(request);
        List<RagChatTurn> history = normalizeHistory(request);
        String retrievalQuery = buildRetrievalQuery(question, history);
        List<RagSearchResult> relevantSources = searchDocumentsForChat(request, retrievalQuery).stream()
                .filter(this::isRelevant)
                .collect(Collectors.toList());
        boolean usedKnowledgeBase = !relevantSources.isEmpty();
        if (!usedKnowledgeBase) {
            return RagChatResponse.ok(buildNoKnowledgeResponse(question), List.of(), false);
        }
        String prompt = buildRagPrompt(question, history, relevantSources);

        try {
            String content = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            if (!StringUtils.hasText(content)) {
                throw new AiServiceException("Model returned empty content.", null);
            }

            return RagChatResponse.ok(content, relevantSources, usedKnowledgeBase);
        } catch (AiServiceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AiServiceException("RAG chat failed. Check model routing, embedding, Milvus, and network settings.", ex);
        }
    }

    private List<RagSearchResult> searchDocumentsForChat(RagQueryRequest request, String retrievalQuery) {
        try {
            return searchDocuments(request, retrievalQuery);
        } catch (AiServiceException ex) {
            log.warn("RAG retrieval failed; falling back to normal chat without knowledge-base context.", ex);
            return List.of();
        }
    }

    private List<RagSearchResult> searchDocuments(RagQueryRequest request) {
        String message = normalizeMessage(request);
        return searchDocuments(request, message);
    }

    private List<RagSearchResult> searchDocuments(RagQueryRequest request, String query) {
        int topK = normalizeTopK(request);

        try {
            List<Document> documents = new ArrayList<>();
            documents.addAll(searchVectorStore(expandRetrievalQuery(query), MAX_TOP_K, null));
            documents.addAll(searchExactProductDocuments(query));
            documents.addAll(searchCategoryDocuments(query));
            documents = mergeDocuments(documents);

            List<RerankDocument> rerankDocuments = documents.stream()
                    .map(document -> new RerankDocument(document.getText(), document.getMetadata()))
                    .toList();

            int rerankTopK = Math.min(MAX_TOP_K, rerankDocuments.size());
            if (rerankTopK == 0) {
                return List.of();
            }

            return prioritizeBusinessMatches(query, rerankModel.rerank(query, rerankDocuments, rerankTopK).stream()
                    .map(this::toSearchResult)
                    .collect(Collectors.toList())).stream()
                    .limit(topK)
                    .collect(Collectors.toList());
        } catch (RuntimeException ex) {
            throw new AiServiceException("Failed to search Milvus. Check Milvus connection and embedding settings.", ex);
        }
    }

    private List<Document> searchVectorStore(String query, int topK, String filterExpression) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(topK);
        if (StringUtils.hasText(filterExpression)) {
            builder.filterExpression(filterExpression);
        }
        return vectorStore.similaritySearch(builder.build());
    }

    private List<Document> searchExactProductDocuments(String query) {
        List<Document> documents = new ArrayList<>();
        for (String productId : extractProductIds(query)) {
            try {
                documents.addAll(searchVectorStore(productId, 1, "product_id == '" + productId + "'"));
            } catch (RuntimeException ex) {
                log.debug("Exact product metadata search failed for {}; continuing with semantic candidates.", productId, ex);
            }
        }
        return documents;
    }

    private List<Document> searchCategoryDocuments(String query) {
        Set<String> categories = detectRequestedCategories(query);
        if (categories.isEmpty()) {
            return List.of();
        }

        List<Document> documents = new ArrayList<>();
        for (String category : categories) {
            try {
                documents.addAll(searchVectorStore(query, MAX_TOP_K, "category == '" + category + "'"));
            } catch (RuntimeException ex) {
                log.debug("Category metadata search failed for {}; continuing with semantic candidates.", category, ex);
            }
        }
        return documents;
    }

    private List<Document> mergeDocuments(List<Document> documents) {
        Map<String, Document> merged = new LinkedHashMap<>();
        for (Document document : documents) {
            merged.putIfAbsent(documentKey(document), document);
        }
        return new ArrayList<>(merged.values());
    }

    private String documentKey(Document document) {
        Object productId = document.getMetadata().get("product_id");
        if (productId != null && StringUtils.hasText(productId.toString())) {
            return "product:" + productId.toString().trim().toUpperCase();
        }
        Object title = document.getMetadata().get("title");
        if (title != null && StringUtils.hasText(title.toString())) {
            return "title:" + title.toString().trim();
        }
        return "content:" + document.getText();
    }

    private List<RagSearchResult> prioritizeBusinessMatches(String query, List<RagSearchResult> results) {
        Set<String> productIds = extractProductIds(query);
        Set<String> categories = detectRequestedCategories(query);
        Double budget = detectBudget(query);

        return results.stream()
                .peek(result -> applyBusinessMatchMetadata(result, productIds, categories, budget))
                .sorted(Comparator.comparingInt((RagSearchResult result) -> businessMatchScore(result, productIds, categories, budget))
                        .reversed())
                .toList();
    }

    private void applyBusinessMatchMetadata(RagSearchResult result, Set<String> productIds, Set<String> categories, Double budget) {
        if (matchesProductId(result, productIds)) {
            result.getMetadata().put("businessMatch", "product_id");
            return;
        }
        if (matchesCategoryAndBudget(result, categories, budget)) {
            result.getMetadata().put("businessMatch", "category_budget");
        }
    }

    private int businessMatchScore(RagSearchResult result, Set<String> productIds, Set<String> categories, Double budget) {
        int score = 0;
        if (matchesProductId(result, productIds)) {
            score += 100;
        }
        if (matchesCategory(result, categories)) {
            score += 30;
        }
        if (budget != null && priceWithinBudget(result, budget)) {
            score += 20;
        }
        if (isProduct(result)) {
            score += 5;
        }
        return score;
    }

    private RagSearchResult toSearchResult(RerankResult result) {
        Map<String, Object> metadata = new HashMap<>(result.document().metadata());
        metadata.put("rerankScore", result.score());
        metadata.put("rerankOriginalIndex", result.originalIndex());
        return new RagSearchResult(result.document().content(), metadata);
    }

    private String buildRagPrompt(String question, List<RagChatTurn> history, List<RagSearchResult> sources) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < sources.size(); i++) {
            RagSearchResult source = sources.get(i);
            context.append("Document ")
                    .append(i + 1)
                    .append(":\n")
                    .append(formatMetadataForPrompt(source.getMetadata()))
                    .append("Content:\n")
                    .append(source.getContent())
                    .append("\n\n");
        }
        return """
                You are a reliable e-commerce RAG assistant. Use the conversation history to understand follow-up questions.
                Answer the latest user question primarily from the knowledge snippets. If you add general knowledge, clearly separate it from knowledge-base facts.
                Keep the answer consistent with previous turns and do not ask the user to repeat context that is already in the history.
                For product recommendations, strictly honor explicit product IDs, categories, budget, color, season, scene, and feature constraints.
                If a user asks for a specific category, do not recommend products from other categories unless you clearly say they are outside the requested category.

                Conversation history:
                %s

                Knowledge snippets:
                %s

                Latest user question:
                %s
                """.formatted(formatHistoryForPrompt(history), context.toString().trim(), question);
    }

    private String buildRetrievalQuery(String question, List<RagChatTurn> history) {
        if (history.isEmpty()) {
            return question;
        }

        List<String> userMessages = history.stream()
                .filter(turn -> "user".equals(normalizeRole(turn.getRole())))
                .map(RagChatTurn::getContent)
                .filter(StringUtils::hasText)
                .toList();
        String recentUserContext = userMessages.stream()
                .skip(Math.max(0, userMessages.size() - 4L))
                .collect(Collectors.joining("\n"));
        if (!StringUtils.hasText(recentUserContext)) {
            return question;
        }
        return recentUserContext + "\n" + question;
    }

    private List<RagChatTurn> normalizeHistory(RagQueryRequest request) {
        if (request == null || request.getHistory() == null || request.getHistory().isEmpty()) {
            return List.of();
        }

        List<RagChatTurn> normalized = request.getHistory().stream()
                .filter(turn -> turn != null && StringUtils.hasText(turn.getContent()))
                .map(this::normalizeHistoryTurn)
                .filter(turn -> StringUtils.hasText(turn.getContent()))
                .toList();
        if (normalized.size() <= MAX_HISTORY_TURNS) {
            return normalized;
        }
        return normalized.subList(normalized.size() - MAX_HISTORY_TURNS, normalized.size());
    }

    private RagChatTurn normalizeHistoryTurn(RagChatTurn turn) {
        RagChatTurn normalized = new RagChatTurn();
        normalized.setRole(normalizeRole(turn.getRole()));
        normalized.setContent(turn.getContent().trim());
        return normalized;
    }

    private String normalizeRole(String role) {
        if ("assistant".equalsIgnoreCase(role)) {
            return "assistant";
        }
        return "user";
    }

    private String formatHistoryForPrompt(List<RagChatTurn> history) {
        if (history.isEmpty()) {
            return "(empty)";
        }

        return history.stream()
                .map(turn -> ("%s: %s").formatted(normalizeRole(turn.getRole()), turn.getContent()))
                .collect(Collectors.joining("\n"));
    }

    private Document toDocument(RagDocumentRequest request) {
        return Document.builder()
                .text(normalizeContent(request))
                .metadata(buildMetadata(request))
                .build();
    }

    private Map<String, Object> buildMetadata(RagDocumentRequest request) {
        Map<String, Object> metadata = new HashMap<>();
        if (request.getMetadata() != null) {
            request.getMetadata().forEach((key, value) -> putMetadata(metadata, key, value));
        }

        putMetadataIfText(metadata, "source", normalizeSource(request));
        putMetadataIfText(metadata, "type", request.getType());
        putMetadataIfText(metadata, "title", request.getTitle());
        putMetadataIfText(metadata, "product_id", request.getProductId());
        putMetadataIfText(metadata, "link", request.getLink());
        return metadata;
    }

    private void putMetadataIfText(Map<String, Object> metadata, String key, String value) {
        if (StringUtils.hasText(value)) {
            metadata.put(key, value.trim());
        }
    }

    private void putMetadata(Map<String, Object> metadata, String key, Object value) {
        if (!StringUtils.hasText(key) || value == null) {
            return;
        }

        Object normalizedValue = normalizeMetadataValue(value);
        if (normalizedValue != null) {
            metadata.put(key.trim(), normalizedValue);
        }
    }

    private Object normalizeMetadataValue(Object value) {
        if (value instanceof String text) {
            return StringUtils.hasText(text) ? text.trim() : null;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        return value.toString();
    }

    String formatMetadataForPrompt(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "";
        }

        List<String> lines = new ArrayList<>();
        addPromptMetadataLine(lines, metadata, "title", "Title");
        addPromptMetadataLine(lines, metadata, "type", "Type");
        addPromptMetadataLine(lines, metadata, "source", "Source");
        addPromptMetadataLine(lines, metadata, "product_id", "Product ID");
        addPromptMetadataLine(lines, metadata, "link", "Link");
        addPromptMetadataLine(lines, metadata, "category", "Category");
        addPromptMetadataLine(lines, metadata, "brand", "Brand");
        addPromptMetadataLine(lines, metadata, "price", "Price");
        addPromptMetadataLine(lines, metadata, "color", "Color");
        addPromptMetadataLine(lines, metadata, "season", "Season");
        addPromptMetadataLine(lines, metadata, "scene", "Scene");
        addPromptMetadataLine(lines, metadata, "effect", "Effect");
        addPromptMetadataLine(lines, metadata, "tags", "Tags");
        return lines.isEmpty() ? "" : String.join("\n", lines) + "\n";
    }

    private void addPromptMetadataLine(List<String> lines, Map<String, Object> metadata, String key, String label) {
        Object value = metadata.get(key);
        if (value != null && StringUtils.hasText(value.toString())) {
            lines.add(label + ": " + value);
        }
    }

    private boolean isRelevant(RagSearchResult result) {
        Object businessMatch = result.getMetadata().get("businessMatch");
        if ("product_id".equals(businessMatch) || "category_budget".equals(businessMatch)) {
            return true;
        }
        Object distance = result.getMetadata().get("distance");
        if (distance instanceof Number number) {
            return number.doubleValue() <= maxDistance;
        }
        return false;
    }

    private Set<String> extractProductIds(String query) {
        Matcher matcher = PRODUCT_ID_PATTERN.matcher(query);
        return matcher.results()
                .map(result -> result.group().toUpperCase())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private Double detectBudget(String query) {
        Matcher matcher = BUDGET_PATTERN.matcher(query);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Double.parseDouble(matcher.group(1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Set<String> detectRequestedCategories(String query) {
        java.util.LinkedHashSet<String> categories = new java.util.LinkedHashSet<>();
        if (containsAny(query, "数码", "手机", "笔记本", "电脑", "耳机")) {
            categories.add("digital");
        }
        if (containsAny(query, "服饰鞋包", "服饰", "衣服", "服装", "羽绒服", "秋冬", "保暖")) {
            categories.add("clothing");
        }
        if (containsAny(query, "服饰鞋包", "鞋包", "鞋子", "运动鞋", "步行")) {
            categories.add("shoes");
        }
        if (containsAny(query, "美妆", "护肤", "口红", "彩妆", "皮肤")) {
            categories.add("beauty");
        }
        if (containsAny(query, "家居", "台灯", "学习", "办公", "阅读")) {
            categories.add("home");
        }
        if (containsAny(query, "母婴", "婴儿", "湿巾")) {
            categories.add("baby");
        }
        return categories;
    }

    private boolean containsAny(String text, String... candidates) {
        for (String candidate : candidates) {
            if (text.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String expandRetrievalQuery(String query) {
        Set<String> categories = detectRequestedCategories(query);
        if (categories.isEmpty()) {
            return query;
        }

        List<String> expansions = new ArrayList<>();
        for (String category : categories) {
            switch (category) {
                case "digital" -> expansions.add("数码类商品 手机 笔记本 耳机");
                case "clothing" -> expansions.add("服饰鞋包 衣服 服装 羽绒服 秋冬 保暖");
                case "shoes" -> expansions.add("服饰鞋包 鞋子 运动鞋 步行 通勤");
                case "beauty" -> expansions.add("美妆个护 护肤 口红 彩妆");
                case "home" -> expansions.add("家居生活 台灯 学习 办公 阅读");
                case "baby" -> expansions.add("食品母婴 婴儿 湿巾 清洁");
                default -> {
                }
            }
        }
        return query + "\n" + String.join(" ", expansions);
    }

    private boolean matchesProductId(RagSearchResult result, Set<String> productIds) {
        if (productIds.isEmpty()) {
            return false;
        }
        Object productId = result.getMetadata().get("product_id");
        return productId != null && productIds.contains(productId.toString().trim().toUpperCase());
    }

    private boolean matchesCategoryAndBudget(RagSearchResult result, Set<String> categories, Double budget) {
        return matchesCategory(result, categories) && (budget == null || priceWithinBudget(result, budget));
    }

    private boolean matchesCategory(RagSearchResult result, Set<String> categories) {
        if (categories.isEmpty()) {
            return false;
        }
        Object category = result.getMetadata().get("category");
        return category != null && categories.contains(category.toString().trim());
    }

    private boolean priceWithinBudget(RagSearchResult result, double budget) {
        Object price = result.getMetadata().get("price");
        if (price instanceof Number number) {
            return number.doubleValue() <= budget;
        }
        if (price != null) {
            try {
                return Double.parseDouble(price.toString()) <= budget;
            } catch (NumberFormatException ex) {
                return false;
            }
        }
        return false;
    }

    private boolean isProduct(RagSearchResult result) {
        Object type = result.getMetadata().get("type");
        return type != null && "product".equals(type.toString());
    }

    private String buildNoKnowledgeResponse(String question) {
        if (containsAny(question, "医疗", "诊断", "病情", "处方")) {
            return "医疗诊断不属于当前商品知识库范围，无法基于当前知识库提供诊断。";
        }
        if (containsAny(question, "法律", "代理", "诉讼", "律师")) {
            return "法律代理不属于当前商品知识库范围，无法从当前知识库确认相关服务。";
        }
        if (containsAny(question, "汽车保险", "车险", "保险")) {
            return "当前商品知识库没有汽车保险相关商品或服务，无法从当前知识库确认。";
        }
        if (containsAny(question, "房产", "中介", "租房", "买房")) {
            return "房产中介不属于当前商品知识库范围，无法从当前知识库确认相关服务。";
        }
        return NO_KNOWLEDGE_RESPONSE;
    }

    private String normalizeContent(RagDocumentRequest request) {
        if (request == null || !StringUtils.hasText(request.getContent())) {
            throw new BadRequestException("content cannot be empty");
        }
        return request.getContent().trim();
    }

    private String normalizeSource(RagDocumentRequest request) {
        if (request == null || !StringUtils.hasText(request.getSource())) {
            return "manual";
        }
        return request.getSource().trim();
    }

    private String normalizeMessage(RagQueryRequest request) {
        if (request == null || !StringUtils.hasText(request.getMessage())) {
            throw new BadRequestException("message cannot be empty");
        }
        return request.getMessage().trim();
    }

    private int normalizeTopK(RagQueryRequest request) {
        Integer topK = request.getTopK();
        if (topK == null) {
            return DEFAULT_TOP_K;
        }
        if (topK < 1 || topK > MAX_TOP_K) {
            throw new BadRequestException("topK must be between 1 and " + MAX_TOP_K);
        }
        return topK;
    }
}
