package com.demo.rag_demo.dto;

import java.util.List;

public class ChatResponse {
    private String answer;
    private List<Source> sources;  // 引用来源
    private Long elapsedMs;        // 处理耗时（毫秒）

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
}