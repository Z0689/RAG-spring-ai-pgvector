package com.demo.rag_demo.dto;

import java.util.List;

public class ChatResponse {
    private String answer;
    private List<Source> sources;
    private Long elapsedMs;
    private String sessionId;      // 返回会话ID
    private Integer historyCount;  // 当前会话历史消息数

    public ChatResponse() {}

    public ChatResponse(String answer) {
        this.answer = answer;
        this.sources = List.of();
        this.elapsedMs = 0L;
    }

    public ChatResponse(String answer, List<Source> sources, Long elapsedMs) {
        this.answer = answer;
        this.sources = sources;
        this.elapsedMs = elapsedMs;
    }

    public ChatResponse(String answer, List<Source> sources, Long elapsedMs,
                        String sessionId, Integer historyCount) {
        this.answer = answer;
        this.sources = sources;
        this.elapsedMs = elapsedMs;
        this.sessionId = sessionId;
        this.historyCount = historyCount;
    }

    // Getters and Setters
    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<Source> getSources() {
        return sources;
    }

    public void setSources(List<Source> sources) {
        this.sources = sources;
    }

    public Long getElapsedMs() {
        return elapsedMs;
    }

    public void setElapsedMs(Long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Integer getHistoryCount() {
        return historyCount;
    }

    public void setHistoryCount(Integer historyCount) {
        this.historyCount = historyCount;
    }
}