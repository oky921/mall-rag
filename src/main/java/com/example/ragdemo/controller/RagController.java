package com.example.ragdemo.controller;

import com.example.ragdemo.exception.BadRequestException;
import com.example.ragdemo.dto.LocalMarkdownIngestRequest;
import com.example.ragdemo.dto.LocalMarkdownIngestResponse;
import com.example.ragdemo.dto.RagChatResponse;
import com.example.ragdemo.dto.RagDocumentRequest;
import com.example.ragdemo.dto.RagIngestResponse;
import com.example.ragdemo.dto.RagQueryRequest;
import com.example.ragdemo.dto.RagSearchResponse;
import com.example.ragdemo.service.MarkdownKnowledgeIngestService;
import com.example.ragdemo.service.RagService;
import java.io.IOException;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/rag")
@ConditionalOnProperty(name = "spring.ai.vectorstore.type", havingValue = "milvus")
public class RagController {

    private final RagService ragService;

    private final MarkdownKnowledgeIngestService markdownKnowledgeIngestService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public RagController(RagService ragService, MarkdownKnowledgeIngestService markdownKnowledgeIngestService) {
        this.ragService = ragService;
        this.markdownKnowledgeIngestService = markdownKnowledgeIngestService;
    }

    @PostMapping("/documents")
    public RagIngestResponse ingest(@RequestBody RagDocumentRequest request) {
        return ragService.ingest(request);
    }

    @PostMapping("/documents/batch")
    public RagIngestResponse ingestBatch(@RequestBody JsonNode payload) {
        return ragService.ingestAll(parseDocumentPayload(payload));
    }

    @PostMapping(value = "/documents/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RagIngestResponse importDocuments(@RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("file cannot be empty");
        }

        try {
            return ragService.ingestAll(parseDocumentPayload(objectMapper.readTree(file.getInputStream())));
        } catch (IOException ex) {
            throw new BadRequestException("file must be a valid JSON document");
        }
    }

    @PostMapping("/documents/ingest-local-md")
    public LocalMarkdownIngestResponse ingestLocalMarkdown(@RequestBody(required = false) LocalMarkdownIngestRequest request) {
        String directory = request == null ? null : request.getDirectory();
        return markdownKnowledgeIngestService.ingest(directory);
    }

    @PostMapping("/search")
    public RagSearchResponse search(@RequestBody RagQueryRequest request) {
        return ragService.search(request);
    }

    @PostMapping("/chat")
    public RagChatResponse chat(@RequestBody RagQueryRequest request) {
        return ragService.chat(request);
    }

    private List<RagDocumentRequest> parseDocumentPayload(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            throw new BadRequestException("documents payload cannot be empty");
        }

        JsonNode documentsNode = payload.has("documents") ? payload.get("documents") : payload;
        try {
            if (documentsNode.isArray()) {
                return objectMapper.convertValue(documentsNode, new TypeReference<>() {
                });
            }
            if (documentsNode.isObject()) {
                return List.of(objectMapper.convertValue(documentsNode, RagDocumentRequest.class));
            }
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("documents payload format is invalid");
        }

        throw new BadRequestException("documents payload must be an object, an array, or {\"documents\": [...] }");
    }
}
