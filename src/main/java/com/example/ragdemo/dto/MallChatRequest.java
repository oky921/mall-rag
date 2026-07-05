package com.example.ragdemo.dto;

import java.util.List;

public class MallChatRequest {

    private String message;

    private Integer topK;

    private List<RagChatTurn> history;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public List<RagChatTurn> getHistory() {
        return history;
    }

    public void setHistory(List<RagChatTurn> history) {
        this.history = history;
    }
}
