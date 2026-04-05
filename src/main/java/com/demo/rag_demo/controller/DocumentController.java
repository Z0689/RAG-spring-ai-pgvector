package com.demo.rag_demo.controller;

import com.demo.rag_demo.dto.ChatMessage;
import com.demo.rag_demo.dto.ChatRequest;
import com.demo.rag_demo.dto.ChatResponse;
import com.demo.rag_demo.dto.Source;
import com.demo.rag_demo.dto.DocumentInfo;
import com.demo.rag_demo.exception.BusinessException;
import com.demo.rag_demo.service.ChatHistoryService;
import com.demo.rag_demo.service.DocumentParserFactory;
import com.demo.rag_demo.service.DocumentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/rag")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

    @Autowired
    private DocumentService documentService;

    @Autowired
    private ChatClient chatClient;

    @Autowired
    private DocumentParserFactory parserFactory;

    @Autowired
    private ChatHistoryService chatHistoryService;

    /**
     * 上传文档（支持 PDF、TXT、MD、DOCX）
     * POST /api/rag/upload
     */
    @PostMapping("/upload")
    public ResponseEntity<String> uploadDocument(@RequestParam("file") MultipartFile file) {
        long startTime = System.currentTimeMillis();

        // 参数校验
        if (file.isEmpty()) {
            throw new BusinessException("请选择要上传的文件");
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new BusinessException("文件名不能为空");
        }

        // 检查文件大小（限制 20MB）
        if (file.getSize() > 20 * 1024 * 1024) {
            throw new BusinessException("文件大小不能超过 20MB");
        }

        // 检查文件格式是否支持
        if (!parserFactory.supports(fileName)) {
            String supported = String.join(", ", parserFactory.getSupportedExtensions());
            throw new BusinessException("不支持的文件格式: " + fileName + "。支持的格式: " + supported);
        }

        try {
            String result = documentService.uploadDocument(file);
            long elapsedMs = System.currentTimeMillis() - startTime;
            log.info("上传接口总耗时: {}ms", elapsedMs);
            return ResponseEntity.ok(result);

        } catch (IOException e) {
            log.error("文件处理IO异常", e);
            throw new BusinessException("文件处理失败: " + e.getMessage());
        }
    }

    /**
     * RAG 问答（支持多轮对话记忆）
     * POST /api/rag/ask
     */
    @PostMapping("/ask")
    public ResponseEntity<ChatResponse> askQuestion(@Valid @RequestBody ChatRequest request) {
        long startTime = System.currentTimeMillis();

        // 生成或使用传入的 sessionId
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = UUID.randomUUID().toString();
            log.info("生成新会话: sessionId={}", sessionId);
        }

        log.info("收到问答请求: sessionId={}, question={}, includeHistory={}",
                sessionId, request.getQuestion(), request.getIncludeHistory());

        // 校验空问题
        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            log.warn("问题为空，直接返回提示");
            long elapsedMs = System.currentTimeMillis() - startTime;
            return ResponseEntity.ok(new ChatResponse(
                    "问题不能为空，请输入您想查询的内容。",
                    List.of(),
                    elapsedMs,
                    sessionId,
                    chatHistoryService.getHistorySize(sessionId)
            ));
        }

        // 1. 从向量库检索相关内容
        String context = documentService.askQuestion(request.getQuestion());

        // 2. 获取来源信息
        List<Source> sources = documentService.retrieveSources(request.getQuestion(), 5, 0.2);

        // 3. 如果检索结果为空，直接返回提示（不保存历史）
        if (context == null || context.trim().isEmpty()) {
            log.warn("未检索到相关内容: {}", request.getQuestion());
            long elapsedMs = System.currentTimeMillis() - startTime;
            return ResponseEntity.ok(new ChatResponse(
                    "未在已上传的文档中找到相关内容，请尝试上传相关文档或换个问题。",
                    sources,
                    elapsedMs,
                    sessionId,
                    chatHistoryService.getHistorySize(sessionId)
            ));
        }

        // 4. 获取对话历史（如果启用）
        String historyContext = "";
        if (request.getIncludeHistory()) {
            historyContext = chatHistoryService.getFormattedHistory(sessionId, 10);
            if (!historyContext.isEmpty()) {
                log.debug("已加载历史记录: sessionId={}, 历史长度={}",
                        sessionId, historyContext.length());
            }
        }

        // 5. 构建 prompt（包含历史记录）
        String prompt = buildPrompt(context, historyContext, request.getQuestion());

        // 6. 保存用户问题到历史
        chatHistoryService.addMessage(sessionId, "user", request.getQuestion());

        // 7. 调用 AI 生成答案
        String answer = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        // 8. 保存 AI 回答到历史
        chatHistoryService.addMessage(sessionId, "assistant", answer);

        long elapsedMs = System.currentTimeMillis() - startTime;
        log.info("问答完成: sessionId={}, 耗时={}ms, 来源数={}, 历史消息数={}",
                sessionId, elapsedMs, sources.size(),
                chatHistoryService.getHistorySize(sessionId));

        return ResponseEntity.ok(new ChatResponse(
                answer,
                sources,
                elapsedMs,
                sessionId,
                chatHistoryService.getHistorySize(sessionId)
        ));
    }

    /**
     * 构建 Prompt（包含历史记录）
     */
    private String buildPrompt(String context, String historyContext, String question) {
        return """
                请基于以下参考资料回答用户的问题。
                
                【参考资料】
                %s
                %s
                【用户问题】
                %s
                
                【要求】
                 1. 如果参考资料包含完整答案，直接回答
                 2. 如果参考资料只包含部分信息，基于已有信息给出合理回答，并说明这是基于已有信息的推断
                 3. 如果有多轮对话历史，可以参考历史对话理解用户意图
                 4. 只有在参考资料完全不包含相关信息时，才回答"资料中未找到相关内容"
                 5. 回答要简洁准确，用中文
                """.formatted(context, historyContext, question);
    }

    /**
     * 健康检查
     * GET /api/rag/health
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("RAG Service is running!");
    }

    // ==================== 文档管理接口 ====================

    /**
     * 获取所有已上传的文档列表
     * GET /api/rag/documents
     */
    @GetMapping("/documents")
    public ResponseEntity<List<DocumentInfo>> listDocuments() {
        log.info("GET /api/rag/documents - 获取文档列表");
        List<DocumentInfo> documents = documentService.getAllDocuments();
        return ResponseEntity.ok(documents);
    }

    /**
     * 获取单个文档详情
     * GET /api/rag/documents/{fileName}
     */
    @GetMapping("/documents/{fileName}")
    public ResponseEntity<DocumentInfo> getDocumentInfo(@PathVariable String fileName) {
        log.info("GET /api/rag/documents/{} - 获取文档详情", fileName);
        DocumentInfo info = documentService.getDocumentInfo(fileName);
        return ResponseEntity.ok(info);
    }

    /**
     * 获取文档的片段列表
     * GET /api/rag/documents/{fileName}/chunks?limit=10
     */
    @GetMapping("/documents/{fileName}/chunks")
    public ResponseEntity<List<String>> getDocumentChunks(
            @PathVariable String fileName,
            @RequestParam(defaultValue = "10") int limit) {

        log.info("GET /api/rag/documents/{}/chunks - limit={}", fileName, limit);

        // 限制最大返回数量
        if (limit > 50) {
            limit = 50;
        }

        List<String> chunks = documentService.getDocumentChunks(fileName, limit);
        return ResponseEntity.ok(chunks);
    }

    /**
     * 删除文档
     * DELETE /api/rag/documents/{fileName}
     */
    @DeleteMapping("/documents/{fileName}")
    public ResponseEntity<String> deleteDocument(@PathVariable String fileName) {
        log.info("DELETE /api/rag/documents/{} - 删除文档", fileName);
        String result = documentService.deleteDocument(fileName);
        return ResponseEntity.ok(result);
    }

    /**
     * 清空所有文档
     * DELETE /api/rag/documents
     */
    @DeleteMapping("/documents")
    public ResponseEntity<String> deleteAllDocuments() {
        log.warn("DELETE /api/rag/documents - 清空所有文档");
        String result = documentService.deleteAllDocuments();
        return ResponseEntity.ok(result);
    }

    // ==================== 对话历史管理接口 ====================

    /**
     * 清空会话历史
     * DELETE /api/rag/history/{sessionId}
     */
    @DeleteMapping("/history/{sessionId}")
    public ResponseEntity<String> clearHistory(@PathVariable String sessionId) {
        log.info("DELETE /api/rag/history/{} - 清空会话历史", sessionId);
        chatHistoryService.clearHistory(sessionId);
        return ResponseEntity.ok("会话历史已清空: " + sessionId);
    }

    /**
     * 获取会话历史
     * GET /api/rag/history/{sessionId}
     */
    @GetMapping("/history/{sessionId}")
    public ResponseEntity<List<ChatMessage>> getHistory(@PathVariable String sessionId) {
        log.info("GET /api/rag/history/{} - 获取会话历史", sessionId);
        List<ChatMessage> history = chatHistoryService.getHistory(sessionId);
        return ResponseEntity.ok(history);
    }

    /**
     * 获取会话历史数量
     * GET /api/rag/history/{sessionId}/count
     */
    @GetMapping("/history/{sessionId}/count")
    public ResponseEntity<Integer> getHistoryCount(@PathVariable String sessionId) {
        log.info("GET /api/rag/history/{}/count - 获取历史数量", sessionId);
        int count = chatHistoryService.getHistorySize(sessionId);
        return ResponseEntity.ok(count);
    }

    /**
     * 检查会话是否存在
     * GET /api/rag/history/{sessionId}/exists
     */
    @GetMapping("/history/{sessionId}/exists")
    public ResponseEntity<Boolean> historyExists(@PathVariable String sessionId) {
        log.info("GET /api/rag/history/{}/exists - 检查会话是否存在", sessionId);
        boolean exists = chatHistoryService.hasHistory(sessionId);
        return ResponseEntity.ok(exists);
    }
}