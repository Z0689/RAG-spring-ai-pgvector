package com.demo.rag_demo.service;

import com.demo.rag_demo.dto.ChatMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class ChatHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ChatHistoryService.class);

    // 改为 RedisTemplate<String, String>
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private final ObjectMapper objectMapper;

    private static final int DEFAULT_MAX_HISTORY = 10;
    private static final long SESSION_TIMEOUT_HOURS = 24;
    private static final String HISTORY_KEY_PREFIX = "chat:history:";

    public ChatHistoryService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public void addMessage(String sessionId, String role, String content) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            log.debug("sessionId 为空，跳过保存历史");
            return;
        }

        String key = HISTORY_KEY_PREFIX + sessionId;
        ChatMessage message = new ChatMessage(role, content, LocalDateTime.now());

        try {
            List<ChatMessage> history = getHistory(sessionId);
            history.add(message);

            if (history.size() > DEFAULT_MAX_HISTORY) {
                history = history.subList(history.size() - DEFAULT_MAX_HISTORY, history.size());
            }

            // 直接存储 JSON 字符串
            String json = objectMapper.writeValueAsString(history);
            redisTemplate.opsForValue().set(key, json, SESSION_TIMEOUT_HOURS, TimeUnit.HOURS);

            log.debug("消息已保存: sessionId={}, role={}", sessionId, role);

        } catch (JsonProcessingException e) {
            log.error("保存历史消息失败: sessionId={}", sessionId, e);
        }
    }

    public List<ChatMessage> getHistory(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String key = HISTORY_KEY_PREFIX + sessionId;
        String json = redisTemplate.opsForValue().get(key);

        if (json == null || json.isEmpty()) {
            log.debug("Redis 中未找到 key: {}", key);
            return new ArrayList<>();
        }

        try {
            log.debug("原始 JSON: {}", json.length() > 200 ? json.substring(0, 200) + "..." : json);
            List<ChatMessage> history = objectMapper.readValue(json, new TypeReference<List<ChatMessage>>() {});
            log.info("成功读取历史: sessionId={}, 消息数={}", sessionId, history.size());
            return history;

        } catch (JsonProcessingException e) {
            log.error("反序列化失败: sessionId={}, error={}", sessionId, e.getMessage());
            return new ArrayList<>();
        }
    }

    public String getFormattedHistory(String sessionId, int maxMessages) {
        List<ChatMessage> history = getHistory(sessionId);
        if (history.isEmpty()) {
            return "";
        }

        int start = Math.max(0, history.size() - maxMessages);
        List<ChatMessage> recent = history.subList(start, history.size());

        StringBuilder sb = new StringBuilder();
        sb.append("\n【对话历史】\n");
        for (ChatMessage msg : recent) {
            String roleName = "user".equals(msg.getRole()) ? "用户" : "助手";
            sb.append(roleName).append(": ").append(msg.getContent()).append("\n");
        }
        sb.append("\n");

        return sb.toString();
    }

    public void clearHistory(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return;
        }

        String key = HISTORY_KEY_PREFIX + sessionId;
        Boolean deleted = redisTemplate.delete(key);
        log.info("会话历史已清空: sessionId={}, 删除成功={}", sessionId, deleted);
    }

    public int getHistorySize(String sessionId) {
        return getHistory(sessionId).size();
    }

    public boolean hasHistory(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return false;
        }
        String key = HISTORY_KEY_PREFIX + sessionId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
