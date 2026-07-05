package com.example.ragdemo.controller;

import com.example.ragdemo.dto.MallChatRequest;
import com.example.ragdemo.dto.MallChatResponse;
import com.example.ragdemo.exception.BadRequestException;
import com.example.ragdemo.service.MallChatService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/mall/chat")
public class MallChatController {

    private final MallChatService mallChatService;

    public MallChatController(MallChatService mallChatService) {
        this.mallChatService = mallChatService;
    }

    @PostMapping
    public MallChatResponse chat(@RequestBody MallChatRequest request) {
        return mallChatService.chat(request);
    }

    @PostMapping(value = "/with-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MallChatResponse chatWithImage(@RequestParam(value = "message", required = false) String message,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "topK", required = false) Integer topK) {
        Path tempFile = writeTempFile(file);
        try {
            return mallChatService.chatWithImage(message, tempFile.toAbsolutePath().toString(), topK);
        } finally {
            deleteQuietly(tempFile);
        }
    }

    private Path writeTempFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("file cannot be empty");
        }
        try {
            Path tempFile = Files.createTempFile("mall-chat-image-", suffix(file.getOriginalFilename()));
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
            // Best effort cleanup for uploaded image temp files.
        }
    }
}
