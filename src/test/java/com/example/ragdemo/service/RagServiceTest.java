package com.example.ragdemo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ragdemo.dto.RagChatResponse;
import com.example.ragdemo.dto.RagDocumentRequest;
import com.example.ragdemo.dto.RagQueryRequest;
import com.example.ragdemo.dto.RagSearchResponse;
import com.example.ragdemo.rerank.PassThroughRerankModel;
import com.example.ragdemo.rerank.RerankModel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

class RagServiceTest {

    @Test
    void ingestAllSplitsEmbeddingRequestsIntoBatchesOfTen() {
        VectorStore vectorStore = mock(VectorStore.class);
        RagService service = new RagService(
                vectorStore, mock(ChatClient.class), mock(RerankModel.class), 0.55);
        List<RagDocumentRequest> requests = new ArrayList<>();
        for (int index = 0; index < 24; index++) {
            RagDocumentRequest request = new RagDocumentRequest();
            request.setContent("document-" + index);
            requests.add(request);
        }

        service.ingestAll(requests);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, org.mockito.Mockito.times(3)).add(captor.capture());
        assertEquals(List.of(10, 10, 4), captor.getAllValues().stream().map(List::size).toList());
    }

    @Test
    void chatReturnsGroundedFallbackWithoutCallingChatModel() {
        VectorStore vectorStore = mock(VectorStore.class);
        RerankModel rerankModel = mock(RerankModel.class);
        ChatClient chatClient = mock(ChatClient.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(rerankModel.rerank(anyString(), anyList(), eq(4))).thenReturn(List.of());
        RagService service = new RagService(vectorStore, chatClient, rerankModel, 0.55);
        RagQueryRequest request = new RagQueryRequest();
        request.setMessage("推荐一款未收录的商品");

        RagChatResponse response = service.chat(request);

        assertFalse(response.isUsedKnowledgeBase());
        assertTrue(response.getSources().isEmpty());
        assertTrue(response.getContent().contains("当前知识库"));
        verify(chatClient, never()).prompt();
    }

    @Test
    void searchBoostsExactProductIdMatches() {
        VectorStore vectorStore = mock(VectorStore.class);
        Document p10001 = productDocument("P10001", "digital", 2999, "P10001 蓝色智能手机");
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenAnswer(invocation -> {
            SearchRequest searchRequest = invocation.getArgument(0);
            if (hasFilter(searchRequest, "product_id")) {
                return List.of(p10001);
            }
            return List.of(
                    document("商品推荐与预算规则", "policy", "recommendation", null, null, "推荐规则"),
                    productDocument("P30001", "beauty", 199, "P30001 保湿护肤套装"),
                    productDocument("P40001", "home", 159, "P40001 智能护眼台灯"),
                    productDocument("P50001", "baby", 49, "P50001 婴儿湿巾"));
        });
        RagService service = new RagService(
                vectorStore, mock(ChatClient.class), new PassThroughRerankModel(), 0.55);
        RagQueryRequest request = new RagQueryRequest();
        request.setMessage("P10001 这款商品适合什么用户？");

        RagSearchResponse response = service.search(request);

        assertEquals("P10001", response.getResults().getFirst().getMetadata().get("product_id"));
        assertEquals("product_id", response.getResults().getFirst().getMetadata().get("businessMatch"));
    }

    @Test
    void searchBoostsProductsMatchingCategoryAndBudget() {
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenAnswer(invocation -> {
            SearchRequest searchRequest = invocation.getArgument(0);
            if (hasFilter(searchRequest, "clothing")) {
                return List.of(productDocument("P20001", "clothing", 399, "P20001 黄色轻薄羽绒服"));
            }
            if (hasFilter(searchRequest, "shoes")) {
                return List.of(productDocument("P20002", "shoes", 269, "P20002 白色休闲运动鞋"));
            }
            return List.of(
                    document("商品推荐与预算规则", "policy", "recommendation", null, null, "推荐规则"),
                    document("商品分类总览", "mall", "mall", null, null, "分类总览"),
                    productDocument("P50001", "baby", 49, "P50001 婴儿湿巾"),
                    productDocument("P30001", "beauty", 199, "P30001 保湿护肤套装"));
        });
        RagService service = new RagService(
                vectorStore, mock(ChatClient.class), new PassThroughRerankModel(), 0.55);
        RagQueryRequest request = new RagQueryRequest();
        request.setMessage("有没有 500 元以内的服饰鞋包商品？");

        RagSearchResponse response = service.search(request);

        List<Object> topProductIds = response.getResults().stream()
                .limit(2)
                .map(result -> result.getMetadata().get("product_id"))
                .toList();
        assertEquals(List.of("P20001", "P20002"), topProductIds);
        assertEquals("category_budget", response.getResults().getFirst().getMetadata().get("businessMatch"));
    }

    @Test
    void chatUsesSpecificOutOfScopeFallbacks() {
        VectorStore vectorStore = mock(VectorStore.class);
        ChatClient chatClient = mock(ChatClient.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        RagService service = new RagService(
                vectorStore, chatClient, new PassThroughRerankModel(), 0.55);
        RagQueryRequest request = new RagQueryRequest();
        request.setMessage("你能帮我做医疗诊断吗？");

        RagChatResponse response = service.chat(request);

        assertFalse(response.isUsedKnowledgeBase());
        assertTrue(response.getContent().contains("医疗诊断不属于当前商品知识库范围"));
        verify(chatClient, never()).prompt();
    }

    @Test
    void searchFiltersLocalMarkdownByPlannedDocumentType() {
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenAnswer(invocation -> {
            SearchRequest searchRequest = invocation.getArgument(0);
            if (hasFilter(searchRequest, "local-md") && hasFilter(searchRequest, "manual")) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("source", "local-md");
                metadata.put("doc_type", "manual");
                metadata.put("topic", "首次空烧");
                metadata.put("distance", 0.9);
                return List.of(Document.builder()
                        .text("首次使用前，空气炸锅应设置 180℃、10分钟 进行首次空烧。")
                        .metadata(metadata)
                        .build());
            }
            return List.of(productDocument("P10001", "digital", 2999, "P10001 蓝色智能手机"));
        });
        RagService service = new RagService(
                vectorStore, mock(ChatClient.class), new PassThroughRerankModel(), 0.55);
        RagQueryRequest request = new RagQueryRequest();
        request.setMessage("空气炸锅第一次怎么用？");

        RagSearchResponse response = service.search(request);

        assertEquals("manual", response.getResults().getFirst().getMetadata().get("doc_type"));
        assertEquals("local_doc_type", response.getResults().getFirst().getMetadata().get("businessMatch"));
        assertEquals("manual_usage", response.getResults().getFirst().getMetadata().get("queryIntent"));
    }

    @Test
    void promptMetadataIncludesProductFilterFields() {
        RagService service = new RagService(
                mock(VectorStore.class), mock(ChatClient.class), mock(RerankModel.class), 0.55);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("product_id", "P30001");
        metadata.put("category", "beauty");
        metadata.put("price", 199);
        metadata.put("color", "red");
        metadata.put("distance", 0.2);

        String formatted = service.formatMetadataForPrompt(metadata);

        assertTrue(formatted.contains("Product ID: P30001"));
        assertTrue(formatted.contains("Category: beauty"));
        assertTrue(formatted.contains("Price: 199"));
        assertTrue(formatted.contains("Color: red"));
        assertFalse(formatted.contains("distance"));
    }

    private boolean hasFilter(SearchRequest searchRequest, String expected) {
        return searchRequest.hasFilterExpression()
                && searchRequest.getFilterExpression().toString().contains(expected);
    }

    private Document productDocument(String productId, String category, int price, String text) {
        return document(text, "product", category, productId, price, text);
    }

    private Document document(String title, String type, String category, String productId, Integer price, String text) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("title", title);
        metadata.put("type", type);
        metadata.put("category", category);
        metadata.put("distance", 0.7);
        if (productId != null) {
            metadata.put("product_id", productId);
        }
        if (price != null) {
            metadata.put("price", price);
        }
        return Document.builder()
                .text(text)
                .metadata(metadata)
                .build();
    }
}
