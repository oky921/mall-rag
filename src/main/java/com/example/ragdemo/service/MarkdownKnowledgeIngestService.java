package com.example.ragdemo.service;

import com.example.ragdemo.dto.LocalMarkdownIngestResponse;
import com.example.ragdemo.dto.RagDocumentRequest;
import com.example.ragdemo.exception.BadRequestException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnProperty(name = "spring.ai.vectorstore.type", havingValue = "milvus")
public class MarkdownKnowledgeIngestService {

    private static final Pattern TOPIC_PATTERN = Pattern.compile("^【([^】]+)】\\s*(.*)$", Pattern.DOTALL);

    private static final String SOURCE = "local-md";

    private final RagService ragService;

    private final Path defaultDirectory;

    public MarkdownKnowledgeIngestService(RagService ragService,
            @Value("${app.rag.local-md-directory:text}") String defaultDirectory) {
        this.ragService = ragService;
        this.defaultDirectory = Paths.get(defaultDirectory);
    }

    public LocalMarkdownIngestResponse ingest(String directory) {
        Path directoryPath = resolveDirectory(directory);
        List<Path> markdownFiles = listMarkdownFiles(directoryPath);
        if (markdownFiles.isEmpty()) {
            throw new BadRequestException("no markdown files found in " + directoryPath);
        }

        List<RagDocumentRequest> chunks = new ArrayList<>();
        Map<String, Integer> chunksByType = new LinkedHashMap<>();
        for (Path markdownFile : markdownFiles) {
            LocalMarkdownDocument document = readMarkdown(markdownFile);
            List<RagDocumentRequest> documentChunks = chunkDocument(document);
            chunks.addAll(documentChunks);
            chunksByType.merge(document.docType(), documentChunks.size(), Integer::sum);
        }

        ragService.ingestAll(chunks);
        return LocalMarkdownIngestResponse.ok(markdownFiles.size(), chunks.size(), chunksByType);
    }

    List<RagDocumentRequest> chunkDocument(LocalMarkdownDocument document) {
        return switch (document.docType()) {
            case "manual" -> chunkSingleParagraphs(document, 500, 80, "manual-small-topic");
            case "promotion_rule" -> chunkSingleParagraphs(document, 450, 60, "promotion-rule-atomic");
            case "brand_story" -> chunkMergedParagraphs(document, 900, 120, "brand-story-merged");
            default -> chunkSingleParagraphs(document, 600, 80, "default-topic");
        };
    }

    private Path resolveDirectory(String directory) {
        Path path = StringUtils.hasText(directory) ? Paths.get(directory.trim()) : defaultDirectory;
        if (!path.isAbsolute()) {
            path = Paths.get("").toAbsolutePath().resolve(path);
        }
        path = path.normalize();
        if (!Files.isDirectory(path)) {
            throw new BadRequestException("markdown directory does not exist: " + path);
        }
        return path;
    }

    private List<Path> listMarkdownFiles(Path directory) {
        try (var stream = Files.list(directory)) {
            return stream
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".md"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        } catch (IOException ex) {
            throw new BadRequestException("failed to list markdown directory: " + directory);
        }
    }

    private LocalMarkdownDocument readMarkdown(Path file) {
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            List<String> blocks = splitParagraphs(raw);
            if (blocks.isEmpty()) {
                throw new BadRequestException("markdown file is empty: " + file);
            }

            String title = blocks.getFirst().trim();
            List<MarkdownParagraph> paragraphs = new ArrayList<>();
            String documentSummary = "";
            for (int i = 1; i < blocks.size(); i++) {
                MarkdownParagraph paragraph = parseParagraph(blocks.get(i));
                if ("内容摘要".equals(paragraph.topic())) {
                    documentSummary = paragraph.body();
                }
                paragraphs.add(paragraph);
            }

            return new LocalMarkdownDocument(title, file.getFileName().toString(), detectDocType(file.getFileName().toString()),
                    documentSummary, paragraphs);
        } catch (IOException ex) {
            throw new BadRequestException("failed to read markdown file: " + file);
        }
    }

    private List<String> splitParagraphs(String raw) {
        return Pattern.compile("\\R\\s*\\R")
                .splitAsStream(raw)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private MarkdownParagraph parseParagraph(String text) {
        Matcher matcher = TOPIC_PATTERN.matcher(text.trim());
        if (!matcher.matches()) {
            return new MarkdownParagraph("正文", "", text.trim());
        }
        String topic = matcher.group(1).trim();
        String body = matcher.group(2).trim();
        return new MarkdownParagraph(topic, summarize(body), text.trim());
    }

    private String summarize(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        int end = firstPositiveIndex(body.indexOf('。'), body.indexOf('；'), body.indexOf(';'));
        if (end >= 0 && end < 120) {
            return body.substring(0, end + 1);
        }
        return body.length() <= 120 ? body : body.substring(0, 120);
    }

    private int firstPositiveIndex(int... indexes) {
        int result = -1;
        for (int index : indexes) {
            if (index >= 0 && (result < 0 || index < result)) {
                result = index;
            }
        }
        return result;
    }

    private String detectDocType(String fileName) {
        if (fileName.contains("使用说明书") || fileName.contains("空气炸锅")) {
            return "manual";
        }
        if (fileName.contains("品牌故事")) {
            return "brand_story";
        }
        if (fileName.contains("活动规则") || fileName.contains("促销")) {
            return "promotion_rule";
        }
        return "local_markdown";
    }

    private List<RagDocumentRequest> chunkSingleParagraphs(LocalMarkdownDocument document, int maxChars, int overlap,
            String strategy) {
        List<RagDocumentRequest> chunks = new ArrayList<>();
        int chunkIndex = 0;
        for (MarkdownParagraph paragraph : document.paragraphs()) {
            for (String content : splitLongText(paragraph.rawText(), maxChars, overlap)) {
                chunks.add(toRequest(document, List.of(paragraph), content, chunkIndex++, strategy));
            }
        }
        return chunks;
    }

    private List<RagDocumentRequest> chunkMergedParagraphs(LocalMarkdownDocument document, int maxChars, int overlap,
            String strategy) {
        List<RagDocumentRequest> chunks = new ArrayList<>();
        List<MarkdownParagraph> window = new ArrayList<>();
        int chunkIndex = 0;
        int currentLength = 0;
        for (MarkdownParagraph paragraph : document.paragraphs()) {
            int nextLength = paragraph.rawText().length();
            if (!window.isEmpty() && currentLength + nextLength > maxChars) {
                chunks.add(toRequest(document, window, joinParagraphs(window), chunkIndex++, strategy));
                window = overlapWindow(window, overlap);
                currentLength = window.stream().mapToInt(item -> item.rawText().length()).sum();
            }
            window.add(paragraph);
            currentLength += nextLength;
        }
        if (!window.isEmpty()) {
            chunks.add(toRequest(document, window, joinParagraphs(window), chunkIndex, strategy));
        }
        return chunks;
    }

    private List<MarkdownParagraph> overlapWindow(List<MarkdownParagraph> paragraphs, int overlap) {
        if (paragraphs.isEmpty() || overlap <= 0) {
            return new ArrayList<>();
        }
        List<MarkdownParagraph> result = new ArrayList<>();
        int length = 0;
        for (int i = paragraphs.size() - 1; i >= 0; i--) {
            MarkdownParagraph paragraph = paragraphs.get(i);
            result.add(0, paragraph);
            length += paragraph.rawText().length();
            if (length >= overlap) {
                break;
            }
        }
        return result;
    }

    private List<String> splitLongText(String text, int maxChars, int overlap) {
        if (text.length() <= maxChars) {
            return List.of(text);
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxChars, text.length());
            if (end < text.length()) {
                int sentenceEnd = Math.max(text.lastIndexOf('。', end), text.lastIndexOf('；', end));
                if (sentenceEnd > start + maxChars / 2) {
                    end = sentenceEnd + 1;
                }
            }
            chunks.add(text.substring(start, end).trim());
            if (end >= text.length()) {
                break;
            }
            start = Math.max(0, end - overlap);
        }
        return chunks;
    }

    private String joinParagraphs(List<MarkdownParagraph> paragraphs) {
        return paragraphs.stream()
                .map(MarkdownParagraph::rawText)
                .collect(Collectors.joining("\n\n"));
    }

    private RagDocumentRequest toRequest(LocalMarkdownDocument document, List<MarkdownParagraph> paragraphs,
            String body, int chunkIndex, String strategy) {
        String topics = paragraphs.stream()
                .map(MarkdownParagraph::topic)
                .distinct()
                .collect(Collectors.joining(","));
        String summaries = paragraphs.stream()
                .map(MarkdownParagraph::summary)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.joining("；"));

        String content = """
                文档类型：%s
                文档标题：%s
                来源文件：%s
                主题标签：%s
                摘要：%s
                正文：
                %s
                """.formatted(toChineseDocType(document.docType()), document.title(), document.sourceFile(), topics, summaries, body).trim();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("doc_type", document.docType());
        metadata.put("source_file", document.sourceFile());
        metadata.put("topic", topics);
        metadata.put("summary", summaries);
        metadata.put("document_summary", document.documentSummary());
        metadata.put("chunk_index", chunkIndex);
        metadata.put("chunk_strategy", strategy);
        metadata.put("char_count", body.length());

        RagDocumentRequest request = new RagDocumentRequest();
        request.setContent(content);
        request.setSource(SOURCE);
        request.setType(document.docType());
        request.setTitle(document.title() + " - " + topics);
        request.setMetadata(metadata);
        return request;
    }

    private String toChineseDocType(String docType) {
        return switch (docType) {
            case "manual" -> "家电使用说明书";
            case "brand_story" -> "品牌故事";
            case "promotion_rule" -> "促销活动规则";
            default -> "本地 Markdown 文档";
        };
    }

    record LocalMarkdownDocument(String title, String sourceFile, String docType, String documentSummary,
            List<MarkdownParagraph> paragraphs) {
    }

    record MarkdownParagraph(String topic, String summary, String rawText) {

        String body() {
            return rawText;
        }
    }
}
