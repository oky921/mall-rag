package com.example.ragdemo.service;

import com.example.ragdemo.dto.ChatRequest;
import com.example.ragdemo.dto.ChatResponse;
import com.example.ragdemo.exception.AiServiceException;
import com.example.ragdemo.exception.BadRequestException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ChatService {

    private final ChatClient chatClient;

    public ChatService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public ChatResponse chat(ChatRequest request) {
        String message = normalizeMessage(request);

        try {
            String content = chatClient.prompt()
                    .user(message)
                    .call()
                    .content();

            if (!StringUtils.hasText(content)) {
                throw new AiServiceException("模型返回内容为空", null);
            }

            return ChatResponse.ok(content);
        } catch (AiServiceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AiServiceException("模型调用失败，请检查模型路由、API Key、Base URL 或网络连接。", ex);
        }
    }

    private String normalizeMessage(ChatRequest request) {
        if (request == null || !StringUtils.hasText(request.getMessage())) {
            throw new BadRequestException("message 不能为空");
        }
        return request.getMessage().trim();
    }
}
