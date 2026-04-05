package com.demo.rag_demo.service;

import com.demo.rag_demo.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DocumentParserFactory {

    private final Map<String, DocumentParser> parserMap = new ConcurrentHashMap<>();
    private final List<DocumentParser> parsers;

    @Autowired
    public DocumentParserFactory(List<DocumentParser> parsers) {
        this.parsers = parsers;
        // 初始化扩展名到解析器的映射
        for (DocumentParser parser : parsers) {
            for (String ext : parser.getSupportedExtensions()) {
                parserMap.put(ext.toLowerCase(), parser);
            }
        }
    }

    /**
     * 根据文件名获取对应的解析器
     */
    public DocumentParser getParser(String fileName) {
        if (fileName == null) {
            throw new BusinessException("文件名不能为空");
        }

        // 查找匹配的解析器
        for (DocumentParser parser : parsers) {
            if (parser.supports(fileName)) {
                return parser;
            }
        }

        throw new BusinessException("不支持的文件格式: " + fileName +
                "。支持的格式: PDF, TXT, MD, DOCX");
    }

    /**
     * 检查是否支持该文件
     */
    public boolean supports(String fileName) {
        try {
            getParser(fileName);
            return true;
        } catch (BusinessException e) {
            return false;
        }
    }

    /**
     * 获取所有支持的扩展名
     */
    public List<String> getSupportedExtensions() {
        return parserMap.keySet().stream().sorted().toList();
    }
}