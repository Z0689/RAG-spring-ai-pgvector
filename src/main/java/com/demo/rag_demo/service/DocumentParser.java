package com.demo.rag_demo.service;

import com.demo.rag_demo.dto.ParsedDocument;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 文档解析器接口
 */
public interface DocumentParser {

    /**
     * 判断是否支持该文件类型
     */
    boolean supports(String fileName);

    /**
     * 解析文档
     * @param file 上传的文件
     * @return 解析后的文档对象
     */
    ParsedDocument parse(MultipartFile file) throws IOException;

    /**
     * 获取支持的文件扩展名
     */
    String[] getSupportedExtensions();
}
