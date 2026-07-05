package com.example.ragdemo.controller;

import com.example.ragdemo.dto.MallImageBatchRequest;
import com.example.ragdemo.dto.MallImageDocumentRequest;
import com.example.ragdemo.dto.MallImageIngestResponse;
import com.example.ragdemo.dto.MallImageSearchRequest;
import com.example.ragdemo.dto.MallImageSearchResponse;
import com.example.ragdemo.exception.BadRequestException;
import com.example.ragdemo.service.ImageRagService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/mall/images")
@ConditionalOnProperty(name = "app.image-rag.enabled", havingValue = "true", matchIfMissing = true)
public class ImageRagController {

    private final ImageRagService imageRagService;

    public ImageRagController(ImageRagService imageRagService) {
        this.imageRagService = imageRagService;
    }

    @PostMapping
    public MallImageIngestResponse ingest(@RequestBody MallImageDocumentRequest request) {
        return imageRagService.ingest(request);
    }

    @PostMapping("/batch")
    public MallImageIngestResponse ingestBatch(@RequestBody MallImageBatchRequest request) {
        if (request == null || request.getImages() == null || request.getImages().isEmpty()) {
            throw new BadRequestException("images payload cannot be empty");
        }
        return imageRagService.ingestAll(request.getImages());
    }

    @PostMapping("/search")
    public MallImageSearchResponse search(@RequestBody MallImageSearchRequest request) {
        return imageRagService.search(request);
    }

    @PostMapping(value = "/search-by-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MallImageSearchResponse searchByUpload(@RequestPart("file") MultipartFile file,
            @RequestPart(value = "topK", required = false) Integer topK,
            @RequestPart(value = "filter", required = false) String filter) {
        Path tempFile = writeTempFile(file);
        try {
            MallImageSearchRequest request = new MallImageSearchRequest();
            request.setImageUrl(tempFile.toAbsolutePath().toString());
            request.setTopK(topK);
            request.setFilter(filter);
            return imageRagService.search(request);
        } finally {
            deleteQuietly(tempFile);
        }
    }

    @GetMapping("/preview")
    public ResponseEntity<Resource> preview(@RequestParam("path") String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            throw new BadRequestException("path cannot be empty");
        }
        Path path = Path.of(imagePath).normalize();
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new BadRequestException("image file does not exist");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                .contentType(resolveMediaType(path))
                .body(new FileSystemResource(path));
    }

    private Path writeTempFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("file cannot be empty");
        }
        try {
            Path tempFile = Files.createTempFile("mall-image-search-", suffix(file.getOriginalFilename()));
            file.transferTo(tempFile);
            return tempFile;
        } catch (IOException ex) {
            throw new BadRequestException("failed to read uploaded image");
        }
    }

    private String suffix(String filename) {
        if (filename == null) {
            return ".img";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return ".img";
        }
        return filename.substring(dot);
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best effort cleanup for upload search temp files.
        }
    }

    private MediaType resolveMediaType(Path path) {
        try {
            String contentType = Files.probeContentType(path);
            if (contentType != null && contentType.startsWith("image/")) {
                return MediaType.parseMediaType(contentType);
            }
        } catch (IOException ignored) {
            // Fall through to octet stream.
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
