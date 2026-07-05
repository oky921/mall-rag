package com.example.ragdemo.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;

public class RagQueryRequest {

    private String message;

    private Integer topK;

    @JsonAlias("session_id")
    private String sessionId;

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

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public List<RagChatTurn> getHistory() {
        return history;
    }

    public void setHistory(List<RagChatTurn> history) {
        this.history = history;
    }
}
