package com.demo.rag_demo.controller;

import com.demo.rag_demo.dto.ChatRequest;
import com.demo.rag_demo.dto.ChatResponse;
import com.demo.rag_demo.dto.Source;
import com.demo.rag_demo.exception.BusinessException;
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

@RestController
@RequestMapping("/api/rag")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

    @Autowired
    private DocumentService documentService;

    @Autowired
    private ChatClient chatClient;

    /**
     * 上传 PDF 文档
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
        if (fileName == null || !fileName.toLowerCase().endsWith(".pdf")) {
            throw new BusinessException("只支持 PDF 文件");
        }

        if (file.getSize() > 10 * 1024 * 1024) {  // 限制 10MB
            throw new BusinessException("文件大小不能超过 10MB");
        }

        try {
            String result = documentService.uploadAndProcessPdf(file);
            long elapsedMs = System.currentTimeMillis() - startTime;
            log.info("上传接口总耗时: {}ms", elapsedMs);
            return ResponseEntity.ok(result);

        } catch (IOException e) {
            log.error("文件处理IO异常", e);
            throw new BusinessException("文件处理失败: " + e.getMessage());
        }
    }

    /**
     * RAG 问答
     * POST /api/rag/ask
     */
    @PostMapping("/ask")
    public ResponseEntity<ChatResponse> askQuestion(@Valid @RequestBody ChatRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("收到问答请求: question={}, sessionId={}",
                request.getQuestion(), request.getSessionId());

        // 先校验问题是否为空
        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            log.warn("问题为空，直接返回提示");
            long elapsedMs = System.currentTimeMillis() - startTime;
            return ResponseEntity.ok(new ChatResponse(
                    "问题不能为空，请输入您想查询的内容。",
                    List.of(),  // 空来源列表
                    elapsedMs
            ));
        }

        // 1. 从向量库检索相关内容
        String context = documentService.askQuestion(request.getQuestion());

        // 2. 获取来源信息
        List<Source> sources = documentService.retrieveSources(request.getQuestion(), 5, 0.2);

        // 3. 如果检索结果为空，直接返回提示
        if (context == null || context.trim().isEmpty()) {
            log.warn("未检索到相关内容: {}", request.getQuestion());
            long elapsedMs = System.currentTimeMillis() - startTime;
            return ResponseEntity.ok(new ChatResponse(
                    "未在已上传的文档中找到相关内容，请尝试上传相关文档或换个问题。",
                    sources,
                    elapsedMs
            ));
        }

        // 4. 构建 prompt
        String prompt = """
                请基于以下参考资料回答用户的问题。
                
                【参考资料】
                %s
                
                【用户问题】
                %s
                
                【要求】
                 1. 如果参考资料包含完整答案，直接回答
                 2. 如果参考资料只包含部分信息，基于已有信息给出合理回答，并说明这是基于已有信息的推断
                 3. 只有在参考资料完全不包含相关信息时，才回答"资料中未找到相关内容"
                 4. 回答要简洁准确，用中文
                """.formatted(context, request.getQuestion());

        // 5. 调用 AI 生成答案
        String answer = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        long elapsedMs = System.currentTimeMillis() - startTime;
        log.info("问答完成: 耗时={}ms, 来源数={}, 答案长度={}",
                elapsedMs, sources.size(), answer.length());

        return ResponseEntity.ok(new ChatResponse(answer, sources, elapsedMs));
    }

    /**
     * 健康检查
     * GET /api/rag/health
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("RAG Service is running!");
    }
}