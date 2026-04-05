package com.demo.rag_demo.service.impl;

import com.demo.rag_demo.dto.ParsedDocument;
import com.demo.rag_demo.service.DocumentParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class TextDocumentParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(TextDocumentParser.class);

    // 支持的文本格式
    private static final List<String> SUPPORTED_EXTENSIONS = List.of(".txt", ".md", ".markdown", ".text");

    @Override
    public boolean supports(String fileName) {
        if (fileName == null) return false;
        String lowerName = fileName.toLowerCase();
        return SUPPORTED_EXTENSIONS.stream().anyMatch(lowerName::endsWith);
    }

    @Override
    public ParsedDocument parse(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        String fileType = getFileType(fileName);
        log.info("开始解析文本文档: {}", fileName);

        ParsedDocument result = new ParsedDocument(fileName, fileType, file.getSize());

        // 读取全部内容
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }

        String fullText = content.toString();

        // 按段落分割（以空行分隔）
        String[] paragraphs = fullText.split("\\n\\s*\\n");
        for (String paragraph : paragraphs) {
            String cleaned = paragraph.trim();
            if (!cleaned.isEmpty()) {
                result.addPage(cleaned);
            }
        }

        // 如果没有段落分割，整个文档作为一页
        if (result.getPageCount() == 0 && !fullText.trim().isEmpty()) {
            result.addPage(fullText.trim());
        }

        log.info("文本解析完成: {} 段落, {} 字符", result.getPageCount(), result.getTotalChars());
        return result;
    }

    @Override
    public String[] getSupportedExtensions() {
        return SUPPORTED_EXTENSIONS.toArray(new String[0]);
    }

    private String getFileType(String fileName) {
        if (fileName == null) return "TEXT";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
            return "Markdown";
        } else if (lower.endsWith(".txt")) {
            return "TXT";
        }
        return "TEXT";
    }
}
