package com.demo.rag_demo.service.impl;

import com.demo.rag_demo.dto.ParsedDocument;
import com.demo.rag_demo.service.DocumentParser;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Component
public class PdfDocumentParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(PdfDocumentParser.class);

    @Override
    public boolean supports(String fileName) {
        return fileName != null && fileName.toLowerCase().endsWith(".pdf");
    }

    @Override
    public ParsedDocument parse(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        log.info("开始解析 PDF 文档: {}", fileName);

        ParsedDocument result = new ParsedDocument(fileName, "PDF", file.getSize());

        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            int pageCount = document.getNumberOfPages();

            log.debug("PDF 页数: {}", pageCount);

            // 按页解析
            for (int i = 1; i <= pageCount; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String pageText = stripper.getText(document);

                if (pageText != null && !pageText.trim().isEmpty()) {
                    String cleaned = cleanText(pageText);
                    result.addPage(cleaned);
                }
            }
        }

        log.info("PDF 解析完成: {} 页, {} 字符", result.getPageCount(), result.getTotalChars());
        return result;
    }

    @Override
    public String[] getSupportedExtensions() {
        return new String[]{".pdf"};
    }

    private String cleanText(String text) {
        // 去除多余的空行和空白
        return text.replaceAll("\\s+", " ").trim();
    }
}