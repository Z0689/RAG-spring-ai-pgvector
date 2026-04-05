package com.demo.rag_demo.service.impl;

import com.demo.rag_demo.dto.ParsedDocument;
import com.demo.rag_demo.service.DocumentParser;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Component
public class WordDocumentParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(WordDocumentParser.class);

    @Override
    public boolean supports(String fileName) {
        return fileName != null && (fileName.toLowerCase().endsWith(".docx") ||
                fileName.toLowerCase().endsWith(".doc"));
    }

    @Override
    public ParsedDocument parse(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        log.info("开始解析 Word 文档: {}", fileName);

        ParsedDocument result = new ParsedDocument(fileName, "Word", file.getSize());

        try (XWPFDocument document = new XWPFDocument(file.getInputStream());
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {

            String fullText = extractor.getText();

            if (fullText != null && !fullText.trim().isEmpty()) {
                // 按段落分割
                String[] paragraphs = fullText.split("\\r?\\n\\s*\\r?\\n");
                for (String paragraph : paragraphs) {
                    String cleaned = paragraph.trim();
                    if (!cleaned.isEmpty()) {
                        result.addPage(cleaned);
                    }
                }

                // 如果没有段落，整个文档作为一页
                if (result.getPageCount() == 0) {
                    result.addPage(fullText.trim());
                }
            }
        }

        log.info("Word 解析完成: {} 段落, {} 字符", result.getPageCount(), result.getTotalChars());
        return result;
    }

    @Override
    public String[] getSupportedExtensions() {
        return new String[]{".docx", ".doc"};
    }
}