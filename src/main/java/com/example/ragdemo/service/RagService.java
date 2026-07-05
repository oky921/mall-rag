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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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

    private final VectorStore vectorStore;

    private final ChatClient chatClient;

    private final RerankModel rerankModel;

    private final double maxDistance;

    public RagService(VectorStore vectorStore, ChatClient chatClient, RerankModel rerankModel,
            @Value("${app.rag.max-distance:0.35}") double maxDistance) {
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
            vectorStore.add(documents);
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
        String prompt = usedKnowledgeBase
                ? buildRagPrompt(question, history, relevantSources)
                : buildPlainPrompt(question, history);

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
            List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .build());

            List<RerankDocument> rerankDocuments = documents.stream()
                    .map(document -> new RerankDocument(document.getText(), document.getMetadata()))
                    .toList();

            return rerankModel.rerank(query, rerankDocuments, topK).stream()
                    .map(this::toSearchResult)
                    .collect(Collectors.toList());
        } catch (RuntimeException ex) {
            throw new AiServiceException("Failed to search Milvus. Check Milvus connection and embedding settings.", ex);
        }
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

                Conversation history:
                %s

                Knowledge snippets:
                %s

                Latest user question:
                %s
                """.formatted(formatHistoryForPrompt(history), context.toString().trim(), question);
    }

    private String buildPlainPrompt(String question, List<RagChatTurn> history) {
        if (history.isEmpty()) {
            return question;
        }

        return """
                Use the conversation history to understand the latest user question and answer naturally.

                Conversation history:
                %s

                Latest user question:
                %s
                """.formatted(formatHistoryForPrompt(history), question);
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

    private String formatMetadataForPrompt(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "";
        }

        List<String> lines = new ArrayList<>();
        addPromptMetadataLine(lines, metadata, "title", "Title");
        addPromptMetadataLine(lines, metadata, "type", "Type");
        addPromptMetadataLine(lines, metadata, "source", "Source");
        addPromptMetadataLine(lines, metadata, "product_id", "Product ID");
        addPromptMetadataLine(lines, metadata, "link", "Link");
        return lines.isEmpty() ? "" : String.join("\n", lines) + "\n";
    }

    private void addPromptMetadataLine(List<String> lines, Map<String, Object> metadata, String key, String label) {
        Object value = metadata.get(key);
        if (value != null && StringUtils.hasText(value.toString())) {
            lines.add(label + ": " + value);
        }
    }

    private boolean isRelevant(RagSearchResult result) {
        Object distance = result.getMetadata().get("distance");
        if (distance instanceof Number number) {
            return number.doubleValue() <= maxDistance;
        }
        return false;
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
