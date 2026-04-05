package com.demo.rag_demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ChatRequest {

    @NotBlank(message = "问题不能为空")
    @Size(min = 1, max = 500, message = "问题长度必须在1-500字符之间")
    private String question;

    // 会话ID，用于区分不同用户/会话
    private String sessionId;

    // 是否包含历史记录（默认 true）
    private Boolean includeHistory = true;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Boolean getIncludeHistory() {
        return includeHistory != null ? includeHistory : true;
    }

    public void setIncludeHistory(Boolean includeHistory) {
        this.includeHistory = includeHistory;
    }
}