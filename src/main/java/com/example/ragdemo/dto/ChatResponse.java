package com.example.ragdemo.dto;

public class ChatResponse {

    private final boolean success;

    private final String content;

    private ChatResponse(boolean success, String content) {
        this.success = success;
        this.content = content;
    }

    public static ChatResponse ok(String content) {
        return new ChatResponse(true, content);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getContent() {
        return content;
    }
}
