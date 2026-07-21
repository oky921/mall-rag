package com.example.ragdemo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ragdemo.dto.LocalMarkdownIngestResponse;
import com.example.ragdemo.dto.RagDocumentRequest;
import com.example.ragdemo.dto.RagIngestResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MarkdownKnowledgeIngestServiceTest {

    @Test
    void ingestBuildsTypedChunksFromLocalMarkdownFiles() {
        RagService ragService = mock(RagService.class);
        when(ragService.ingestAll(anyList())).thenReturn(RagIngestResponse.ok(1));
        MarkdownKnowledgeIngestService service = new MarkdownKnowledgeIngestService(ragService, "text");

        LocalMarkdownIngestResponse response = service.ingest("text");

        assertEquals(3, response.getDocuments());
        assertTrue(response.getChunks() > 40);
        assertTrue(response.getChunksByType().containsKey("manual"));
        assertTrue(response.getChunksByType().containsKey("brand_story"));
        assertTrue(response.getChunksByType().containsKey("promotion_rule"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RagDocumentRequest>> captor = ArgumentCaptor.forClass(List.class);
        verify(ragService).ingestAll(captor.capture());
        List<Map<String, Object>> metadata = captor.getValue().stream()
                .map(RagDocumentRequest::getMetadata)
                .toList();
        assertTrue(metadata.stream().anyMatch(item -> "manual".equals(item.get("doc_type"))));
        assertTrue(metadata.stream().anyMatch(item -> "brand_story".equals(item.get("doc_type"))));
        assertTrue(metadata.stream().anyMatch(item -> "promotion_rule".equals(item.get("doc_type"))));
        assertTrue(captor.getValue().stream().anyMatch(request -> request.getContent().contains("主题标签")));
    }
}
