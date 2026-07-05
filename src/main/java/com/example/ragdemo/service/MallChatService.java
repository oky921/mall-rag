package com.example.ragdemo.service;

import com.example.ragdemo.dto.ChatRequest;
import com.example.ragdemo.dto.ChatResponse;
import com.example.ragdemo.dto.MallChatRequest;
import com.example.ragdemo.dto.MallChatResponse;
import com.example.ragdemo.dto.MallImageSearchRequest;
import com.example.ragdemo.dto.MallImageSearchResult;
import com.example.ragdemo.dto.MallImageSearchResponse;
import com.example.ragdemo.dto.RagChatResponse;
import com.example.ragdemo.dto.RagQueryRequest;
import com.example.ragdemo.exception.AiServiceException;
import com.example.ragdemo.exception.BadRequestException;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MallChatService {

    private static final int DEFAULT_TOP_K = 5;

    private static final double MIN_VISIBLE_SCORE = 0.4;

    private final ObjectProvider<ImageRagService> imageRagServiceProvider;

    private final ObjectProvider<RagService> ragServiceProvider;

    private final ObjectProvider<ChatService> chatServiceProvider;

    public MallChatService(ObjectProvider<ImageRagService> imageRagServiceProvider,
            ObjectProvider<RagService> ragServiceProvider,
            ObjectProvider<ChatService> chatServiceProvider) {
        this.imageRagServiceProvider = imageRagServiceProvider;
        this.ragServiceProvider = ragServiceProvider;
        this.chatServiceProvider = chatServiceProvider;
    }

    public MallChatResponse chat(MallChatRequest request) {
        String message = normalizeMessage(request);
        if (isProductSearchIntent(message)) {
            return searchProductsByText(message, normalizeTopK(request));
        }
        return ragOrPlain(request, message);
    }

    public MallChatResponse chatWithImage(String message, String imagePath, Integer topK) {
        if (!StringUtils.hasText(imagePath)) {
            throw new BadRequestException("image cannot be empty");
        }

        MallImageSearchRequest searchRequest = new MallImageSearchRequest();
        searchRequest.setImageUrl(imagePath.trim());
        searchRequest.setTopK(topK == null ? DEFAULT_TOP_K : topK);
        MallImageSearchResponse searchResponse = searchImagesOrNull(searchRequest);
        if (searchResponse == null) {
            return imageRagUnavailableResponse();
        }
        List<MallImageSearchResult> visibleResults = visibleResults(searchResponse.getResults());
        String answer = buildImageUploadAnswer(normalizeOptionalMessage(message), visibleResults);
        return MallChatResponse.imageSearch(answer, visibleResults);
    }

    private MallChatResponse searchProductsByText(String message, int topK) {
        MallImageSearchRequest searchRequest = new MallImageSearchRequest();
        searchRequest.setQuery(toProductSearchQuery(message));
        searchRequest.setTopK(topK);
        MallImageSearchResponse searchResponse = searchImagesOrNull(searchRequest);
        if (searchResponse == null) {
            return imageRagUnavailableResponse();
        }
        List<MallImageSearchResult> visibleResults = visibleResults(searchResponse.getResults());
        String answer = buildTextProductSearchAnswer(visibleResults);
        return MallChatResponse.imageSearch(answer, visibleResults);
    }

    private MallChatResponse ragOrPlain(MallChatRequest request, String message) {
        RagService ragService = ragServiceProvider.getIfAvailable();
        if (ragService == null) {
            return plainChat(message);
        }

        RagQueryRequest ragRequest = new RagQueryRequest();
        ragRequest.setMessage(message);
        ragRequest.setTopK(request == null ? DEFAULT_TOP_K : request.getTopK());
        ragRequest.setHistory(request == null ? null : request.getHistory());
        RagChatResponse response = ragService.chat(ragRequest);
        return MallChatResponse.rag(response.getContent(), response.getSources(), response.isUsedKnowledgeBase());
    }

    private MallImageSearchResponse searchImagesOrNull(MallImageSearchRequest request) {
        ImageRagService imageRagService = imageRagServiceProvider.getIfAvailable();
        if (imageRagService == null) {
            return null;
        }
        try {
            return imageRagService.search(request);
        } catch (AiServiceException ex) {
            return null;
        }
    }

    private MallChatResponse imageRagUnavailableResponse() {
        return MallChatResponse.plain(
                "\u5546\u54c1\u5411\u91cf\u68c0\u7d22\u670d\u52a1\u6682\u65f6\u4e0d\u53ef\u7528\uff1a\u8bf7\u786e\u8ba4\u5df2\u8bbe\u7f6e APP_IMAGE_RAG_ENABLED=true\uff0c\u5e76\u4e14 Milvus \u5bb9\u5668 milvus-standalone \u6b63\u5728\u8fd0\u884c\u3002\u666e\u901a\u804a\u5929\u548c\u6587\u672c\u77e5\u8bc6\u5e93\u95ee\u7b54\u4ecd\u53ef\u4f7f\u7528\u3002");
    }

    private MallChatResponse plainChat(String message) {
        ChatService chatService = chatServiceProvider.getIfAvailable();
        if (chatService == null) {
            return MallChatResponse.plain(
                    "\u6211\u53ef\u4ee5\u5e2e\u4f60\u8fdb\u884c\u666e\u901a\u5bf9\u8bdd\uff0c\u4e5f\u53ef\u4ee5\u5728\u5411\u91cf\u5e93\u542f\u7528\u540e\u8fdb\u884c\u77e5\u8bc6\u5e93\u95ee\u7b54\u548c\u5546\u54c1\u68c0\u7d22\u3002");
        }
        ChatRequest chatRequest = new ChatRequest();
        chatRequest.setMessage(message);
        ChatResponse response = chatService.chat(chatRequest);
        return MallChatResponse.plain(response.getContent());
    }

    private String buildTextProductSearchAnswer(List<MallImageSearchResult> results) {
        if (results.isEmpty()) {
            return "\u6682\u65f6\u6ca1\u6709\u627e\u5230\u8db3\u591f\u76f8\u4f3c\u7684\u5546\u54c1\u3002\u53ef\u4ee5\u6362\u4e00\u4e2a\u66f4\u5177\u4f53\u7684\u8bf4\u6cd5\uff0c\u6bd4\u5982\u624b\u673a\u3001\u7fbd\u7ed2\u670d\u3001\u5316\u5986\u54c1\u6216\u88d9\u5b50\u3002";
        }
        return "\u5df2\u4e3a\u4f60\u627e\u5230\u76f8\u5173\u5546\u54c1\uff0c\u8bf7\u67e5\u770b\u4e0b\u65b9\u7ed3\u679c\u3002";
    }

    private String buildImageUploadAnswer(String message, List<MallImageSearchResult> results) {
        if (results.isEmpty()) {
            return "\u5df2\u8bfb\u53d6\u4f60\u4e0a\u4f20\u7684\u56fe\u7247\uff0c\u4f46\u6682\u65f6\u6ca1\u6709\u627e\u5230\u8db3\u591f\u76f8\u4f3c\u7684\u5546\u54c1\u3002";
        }
        if (looksLikeQuestionAboutImage(message)) {
            return "\u8fd9\u5f20\u56fe\u4e0e\u5546\u54c1\u5e93\u4e2d\u7684\u4e00\u4e9b\u5546\u54c1\u8f83\u4e3a\u76f8\u4f3c\uff0c\u8bf7\u67e5\u770b\u4e0b\u65b9\u7ed3\u679c\u3002";
        }
        return "\u5df2\u6839\u636e\u4f60\u4e0a\u4f20\u7684\u56fe\u7247\u627e\u5230\u76f8\u4f3c\u5546\u54c1\uff0c\u8bf7\u67e5\u770b\u4e0b\u65b9\u7ed3\u679c\u3002";
    }

    private List<MallImageSearchResult> visibleResults(List<MallImageSearchResult> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        return results.stream()
                .filter(result -> result.getScore() != null && result.getScore() >= MIN_VISIBLE_SCORE)
                .toList();
    }

    private boolean isProductSearchIntent(String message) {
        String text = message.toLowerCase(Locale.ROOT);
        boolean mentionsMedia = text.contains("\u56fe\u7247") || text.contains("\u56fe\u50cf")
                || text.contains("\u7167\u7247") || text.contains("\u76f8\u4f3c")
                || text.contains("image") || text.contains("photo") || text.contains("picture")
                || text.contains("similar");
        boolean mentionsProductCategory = text.contains("\u624b\u673a") || text.contains("\u8863\u670d")
                || text.contains("\u7fbd\u7ed2\u670d") || text.contains("\u5916\u5957")
                || text.contains("\u5316\u5986") || text.contains("\u62a4\u80a4")
                || text.contains("\u88d9") || text.contains("phone") || text.contains("iphone")
                || text.contains("jacket") || text.contains("clothes") || text.contains("cosmetic")
                || text.contains("skincare") || text.contains("dress");
        boolean wantsSearch = text.contains("\u627e") || text.contains("\u641c\u7d22")
                || text.contains("\u63a8\u8350") || text.contains("\u7c7b\u4f3c")
                || text.contains("find") || text.contains("search") || text.contains("show");
        return (mentionsMedia || mentionsProductCategory) && wantsSearch;
    }

    private boolean looksLikeQuestionAboutImage(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String text = message.toLowerCase(Locale.ROOT);
        return text.contains("\u662f\u4ec0\u4e48") || text.contains("\u8fd9\u5f20")
                || text.contains("\u8fd9\u4e2a") || text.contains("what") || text.contains("describe");
    }

    private String toProductSearchQuery(String message) {
        String text = message.toLowerCase(Locale.ROOT);
        if (text.contains("\u624b\u673a") || text.contains("iphone") || text.contains("phone")) {
            return "phone iphone smartphone product image";
        }
        if (text.contains("\u7fbd\u7ed2\u670d") || text.contains("\u5916\u5957")
                || text.contains("\u8863\u670d") || text.contains("jacket") || text.contains("clothes")) {
            return "down jacket winter warm clothes product image";
        }
        if (text.contains("\u5316\u5986") || text.contains("\u62a4\u80a4")
                || text.contains("\u9762\u971c") || text.contains("cosmetic")
                || text.contains("skincare") || text.contains("cream")) {
            return "cosmetics skincare cream beauty product image";
        }
        if (text.contains("\u88d9") || text.contains("dress")) {
            return "dress skirt womenswear product image";
        }
        return message;
    }

    private String normalizeMessage(MallChatRequest request) {
        if (request == null || !StringUtils.hasText(request.getMessage())) {
            throw new BadRequestException("message cannot be empty");
        }
        return request.getMessage().trim();
    }

    private String normalizeOptionalMessage(String message) {
        return StringUtils.hasText(message) ? message.trim() : "";
    }

    private int normalizeTopK(MallChatRequest request) {
        Integer topK = request == null ? null : request.getTopK();
        if (topK == null) {
            return DEFAULT_TOP_K;
        }
        if (topK < 1 || topK > 10) {
            throw new BadRequestException("topK must be between 1 and 10");
        }
        return topK;
    }
}
