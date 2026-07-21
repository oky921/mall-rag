package com.example.ragdemo.service;

import com.example.ragdemo.dto.ProductSearchResult;
import com.example.ragdemo.dto.RagQueryRequest;
import com.example.ragdemo.dto.RagSearchResult;
import com.example.ragdemo.store.StoreProduct;
import com.example.ragdemo.store.StoreProductRepository;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class StoreProductRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(StoreProductRecommendationService.class);
    private static final int SEMANTIC_TOP_K = 10;
    private static final Pattern BUDGET_PATTERN = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*(?:元|块)?\\s*(?:以内|以下|之内|不超过|最多)");
    private static final List<String> SEARCH_TERMS = List.of(
            "耳机", "手机", "键盘", "腕表", "羽绒服", "外套", "跑鞋", "鞋", "护肤", "美妆", "咖啡",
            "通勤", "降噪", "续航", "办公", "游戏", "保暖", "运动", "步行", "保湿", "学习");

    private final StoreProductRepository productRepository;
    private final ObjectProvider<RagService> ragServiceProvider;

    public StoreProductRecommendationService(StoreProductRepository productRepository,
            ObjectProvider<RagService> ragServiceProvider) {
        this.productRepository = productRepository;
        this.ragServiceProvider = ragServiceProvider;
    }

    public List<ProductSearchResult> recommend(String query, int topK) {
        Map<String, Double> semanticScores = semanticCandidates(query);
        Map<String, StoreProduct> candidates = new LinkedHashMap<>();

        if (!semanticScores.isEmpty()) {
            productRepository.findByCodeIn(semanticScores.keySet()).stream()
                    .filter(product -> !Boolean.FALSE.equals(product.getActive()))
                    .sorted(Comparator.comparingDouble((StoreProduct product) ->
                            semanticScores.getOrDefault(product.getCode(), 0.0)).reversed())
                    .forEach(product -> candidates.put(product.getCode(), product));
        }

        productRepository.findAll().stream()
                .filter(product -> !Boolean.FALSE.equals(product.getActive()))
                .sorted(fallbackComparator(query))
                .forEach(product -> candidates.putIfAbsent(product.getCode(), product));

        BigDecimal budget = extractBudget(query);
        return candidates.values().stream()
                .filter(product -> budget == null || product.getPrice().compareTo(budget) <= 0)
                .filter(product -> matchesRequestedKind(query, product))
                .sorted(combinedComparator(query, semanticScores))
                .limit(topK)
                .map(product -> toResult(product, semanticScores.get(product.getCode())))
                .toList();
    }

    public boolean isProductShoppingIntent(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String text = message.toLowerCase(Locale.ROOT);
        boolean shoppingVerb = containsAny(text, "想买", "购买", "推荐", "找", "搜索", "展示", "看看", "有没有",
                "buy", "recommend", "find", "search");
        boolean productSignal = SEARCH_TERMS.stream().anyMatch(text::contains)
                || containsAny(text, "商品", "预算", "元以内", "元以下", "不超过", "价格");
        return shoppingVerb && productSignal;
    }

    private Map<String, Double> semanticCandidates(String query) {
        RagService ragService = ragServiceProvider.getIfAvailable();
        if (ragService == null) {
            return Map.of();
        }
        try {
            RagQueryRequest request = new RagQueryRequest();
            request.setMessage(query);
            request.setTopK(SEMANTIC_TOP_K);
            Map<String, Double> scores = new LinkedHashMap<>();
            int position = 0;
            for (RagSearchResult result : ragService.search(request).getResults()) {
                Object type = result.getMetadata().get("type");
                Object productId = result.getMetadata().get("product_id");
                if (productId == null || (type != null && !"product".equals(type.toString()))) {
                    continue;
                }
                scores.putIfAbsent(productId.toString(), semanticScore(result, position++));
            }
            return scores;
        } catch (RuntimeException ex) {
            log.warn("Product semantic retrieval is unavailable; using MySQL fallback matching.", ex);
            return Map.of();
        }
    }

    private double semanticScore(RagSearchResult result, int position) {
        Object rerankScore = result.getMetadata().get("rerankScore");
        if (rerankScore instanceof Number number) {
            return number.doubleValue();
        }
        return Math.max(0.0, 1.0 - position * 0.05);
    }

    private Comparator<StoreProduct> combinedComparator(String query, Map<String, Double> semanticScores) {
        return Comparator
                .comparingDouble((StoreProduct product) -> semanticScores.containsKey(product.getCode()) ? 1.0 : 0.0)
                .thenComparingDouble(product -> semanticScores.getOrDefault(product.getCode(), 0.0))
                .thenComparingInt(product -> lexicalScore(query, product))
                .thenComparing(product -> Boolean.TRUE.equals(product.getFeatured()))
                .thenComparing(StoreProduct::getSales)
                .reversed();
    }

    private Comparator<StoreProduct> fallbackComparator(String query) {
        return Comparator.comparingInt((StoreProduct product) -> lexicalScore(query, product))
                .thenComparing(product -> Boolean.TRUE.equals(product.getFeatured()))
                .thenComparing(StoreProduct::getSales)
                .reversed();
    }

    private int lexicalScore(String query, StoreProduct product) {
        String text = productText(product);
        int score = 0;
        for (String term : SEARCH_TERMS) {
            if (query.contains(term) && text.contains(term)) {
                score += 10;
            }
        }
        for (String token : query.split("[\\s,，。！？、]+")) {
            if (token.length() >= 2 && text.contains(token)) {
                score += 2;
            }
        }
        return score;
    }

    private boolean matchesRequestedKind(String query, StoreProduct product) {
        String text = productText(product);
        List<Set<String>> productKinds = List.of(
                Set.of("耳机", "headphone", "headset"),
                Set.of("手机", "phone", "iphone"),
                Set.of("键盘", "keyboard"),
                Set.of("腕表", "手表", "watch"),
                Set.of("羽绒服", "外套", "jacket"),
                Set.of("跑鞋", "鞋", "shoe"),
                Set.of("护肤", "美妆", "化妆", "skincare", "cosmetic"),
                Set.of("咖啡", "coffee"));
        for (Set<String> kind : productKinds) {
            boolean requested = kind.stream().anyMatch(query.toLowerCase(Locale.ROOT)::contains);
            if (requested) {
                return kind.stream().anyMatch(text::contains);
            }
        }
        return true;
    }

    private String productText(StoreProduct product) {
        return String.join(" ", product.getName(), product.getSubtitle(), product.getDescription(), product.getCategory())
                .toLowerCase(Locale.ROOT);
    }

    private BigDecimal extractBudget(String query) {
        Matcher matcher = BUDGET_PATTERN.matcher(query);
        if (!matcher.find()) {
            return null;
        }
        try {
            return new BigDecimal(matcher.group(1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private ProductSearchResult toResult(StoreProduct product, Double score) {
        return new ProductSearchResult(product.getId(), product.getCode(), product.getName(), product.getSubtitle(),
                product.getCategory(), product.getPrice(), product.getOriginalPrice(), product.getImageUrl(),
                "/mall/products/" + product.getId(), score);
    }

    private boolean containsAny(String text, String... candidates) {
        for (String candidate : candidates) {
            if (text.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
