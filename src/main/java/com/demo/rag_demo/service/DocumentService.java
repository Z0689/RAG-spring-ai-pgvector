package com.demo.rag_demo.service;

import com.demo.rag_demo.dto.Source;
import com.demo.rag_demo.exception.BusinessException;
import com.demo.rag_demo.repository.VectorStoreRepository;
import com.demo.rag_demo.dto.DocumentInfo;
import com.demo.rag_demo.dto.ParsedDocument;
import com.demo.rag_demo.service.DocumentParser;
import com.demo.rag_demo.service.DocumentParserFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private VectorStoreRepository vectorStoreRepository;

    @Autowired
    private DocumentParserFactory parserFactory;

    private final Path uploadDir = Path.of("./uploads");

    // ==================== 文档上传与处理 ====================

    /**
     * 通用文档上传方法（支持 PDF、TXT、MD、DOCX）
     */
    public String uploadDocument(MultipartFile file) throws IOException {
        long startTime = System.currentTimeMillis();
        String fileName = file.getOriginalFilename();
        long fileSize = file.getSize();

        log.info("开始处理文档上传: fileName={}, size={} bytes", fileName, fileSize);

        // 1. 创建上传目录
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
            log.debug("创建上传目录: {}", uploadDir.toAbsolutePath());
        }

        // 2. 保存物理文件（备份）
        Path filePath = uploadDir.resolve(fileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        log.debug("物理文件已保存: {}", filePath);

        // 3. 获取对应的解析器并解析文档
        DocumentParser parser = parserFactory.getParser(fileName);
        log.info("使用解析器: {}", parser.getClass().getSimpleName());

        ParsedDocument parsedDoc = parser.parse(file);
        log.info("文档解析完成: 类型={}, 页数/段落数={}, 总字符数={}",
                parsedDoc.getFileType(), parsedDoc.getPageCount(), parsedDoc.getTotalChars());

        // 4. 文本分块
        List<String> chunks = splitIntoChunks(parsedDoc.getPages());

        // 5. 创建 Document 对象并设置元数据
        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String chunkContent = chunks.get(i);
            Document doc = new Document(chunkContent);

            // 设置元数据
            doc.getMetadata().put("fileName", fileName);
            doc.getMetadata().put("fileSize", fileSize);
            doc.getMetadata().put("fileType", parsedDoc.getFileType());
            doc.getMetadata().put("uploadTime", String.valueOf(System.currentTimeMillis()));
            doc.getMetadata().put("chunkIndex", i);
            doc.getMetadata().put("totalChunks", chunks.size());
            doc.getMetadata().put("totalPages", parsedDoc.getPageCount());

            documents.add(doc);
        }

        log.debug("元数据设置完成: fileName={}, chunks={}", fileName, documents.size());

        // 6. 向量化并存储
        vectorStore.add(documents);

        long elapsedMs = System.currentTimeMillis() - startTime;
        log.info("文档处理完成！耗时: {}ms, 原始页数: {}, 分块数: {}",
                elapsedMs, parsedDoc.getPageCount(), documents.size());

        return String.format("文档处理完成！文件: %s, 类型: %s, 原始页数: %d, 分块数: %d, 耗时: %dms",
                fileName, parsedDoc.getFileType(), parsedDoc.getPageCount(), documents.size(), elapsedMs);
    }

    /**
     * 文本分块
     */
    private List<String> splitIntoChunks(List<String> pages) {
        List<String> allChunks = new ArrayList<>();
        TokenTextSplitter splitter = new TokenTextSplitter(800, 100, 10, 10000, true);

        for (String page : pages) {
            if (page == null || page.trim().isEmpty()) continue;

            Document tempDoc = new Document(page);
            List<Document> chunks = splitter.apply(List.of(tempDoc));

            for (Document chunk : chunks) {
                allChunks.add(chunk.getText());
            }
        }

        log.debug("分块完成: 原始 {} 页, 生成 {} 个块", pages.size(), allChunks.size());
        return allChunks;
    }

    // ==================== 向量检索 ====================

    /**
     * 检索相关文档并返回来源信息
     */
    public List<Source> retrieveSources(String question, int topK, double threshold) {
        long startTime = System.currentTimeMillis();

        SearchRequest searchRequest = SearchRequest.builder()
                .query(question)
                .topK(topK)
                .similarityThreshold(threshold)
                .build();

        List<Document> relevantDocs = vectorStore.similaritySearch(searchRequest);

        long elapsedMs = System.currentTimeMillis() - startTime;
        log.info("向量检索完成: question={}, topK={}, threshold={}, 结果数={}, 耗时={}ms",
                truncate(question, 50), topK, threshold, relevantDocs.size(), elapsedMs);

        List<Source> sources = new ArrayList<>();
        for (Document doc : relevantDocs) {
            String fileName = "未知";
            if (doc.getMetadata() != null && doc.getMetadata().containsKey("fileName")) {
                Object nameObj = doc.getMetadata().get("fileName");
                if (nameObj != null) {
                    fileName = nameObj.toString();
                }
            }

            // 获取 chunk 索引
            int chunkIndex = -1;
            if (doc.getMetadata() != null && doc.getMetadata().containsKey("chunkIndex")) {
                Object indexObj = doc.getMetadata().get("chunkIndex");
                if (indexObj != null) {
                    try {
                        chunkIndex = Integer.parseInt(indexObj.toString());
                    } catch (NumberFormatException e) {
                        log.warn("chunkIndex 格式错误: {}", indexObj);
                    }
                }
            }

            // 获取相似度分数
            double score = doc.getScore() != null ? doc.getScore() : 0.0;

            // 添加详细日志
            log.debug("检索结果: fileName={}, score={}, chunkIndex={}",
                    fileName, String.format("%.2f", score), chunkIndex);

            sources.add(new Source(
                    truncate(doc.getText(), 500),  // 增加到500字符，提供更完整的上下文
                    score,
                    fileName,
                    chunkIndex
            ));
        }

        // 输出汇总日志
        log.info("检索完成，共找到 {} 个相关片段，主要来源: {}",
                sources.size(),
                sources.stream().map(Source::getDocumentName).distinct().collect(Collectors.toList()));

        return sources;
    }

    /**
     * 基于文档内容问答（返回检索到的上下文）
     */
    public String askQuestion(String question) {
        List<Source> sources = retrieveSources(question, 8, 0.2);

        if (sources.isEmpty()) {
            log.warn("未检索到相关文档: question={}", truncate(question, 50));
            return null;
        }

        StringBuilder context = new StringBuilder();
        for (Source source : sources) {
            context.append(source.getContent()).append("\n\n");
        }

        log.info("构建上下文完成，共 {} 个来源", sources.size());
        return context.toString();
    }

    /**
     * 截断字符串用于日志输出
     */
    private String truncate(String str, int maxLen) {
        if (str == null) return "null";
        if (str.length() <= maxLen) return str;
        return str.substring(0, maxLen) + "...";
    }

    // ==================== 文档管理接口 ====================

    /**
     * 获取所有已上传的文档列表
     */
    public List<DocumentInfo> getAllDocuments() {
        log.info("获取所有文档列表");

        List<String> fileNames = vectorStoreRepository.findAllDocumentNames();
        List<DocumentInfo> documents = new ArrayList<>();

        for (String fileName : fileNames) {
            DocumentInfo info = vectorStoreRepository.findDocumentInfo(fileName);
            if (info != null) {
                documents.add(info);
            }
        }

        log.info("找到 {} 个文档", documents.size());
        return documents;
    }

    /**
     * 获取单个文档详情
     */
    public DocumentInfo getDocumentInfo(String fileName) {
        log.info("获取文档详情: fileName={}", fileName);

        if (!vectorStoreRepository.documentExists(fileName)) {
            throw new BusinessException(404, "文档不存在: " + fileName);
        }

        return vectorStoreRepository.findDocumentInfo(fileName);
    }

    /**
     * 获取文档的片段列表
     */
    public List<String> getDocumentChunks(String fileName, int limit) {
        log.info("获取文档片段: fileName={}, limit={}", fileName, limit);

        if (!vectorStoreRepository.documentExists(fileName)) {
            throw new BusinessException(404, "文档不存在: " + fileName);
        }

        return vectorStoreRepository.findDocumentChunks(fileName, limit);
    }

    /**
     * 删除文档
     */
    public String deleteDocument(String fileName) {
        log.info("删除文档: fileName={}", fileName);

        if (!vectorStoreRepository.documentExists(fileName)) {
            throw new BusinessException(404, "文档不存在: " + fileName);
        }

        // 获取文档信息用于日志
        DocumentInfo info = vectorStoreRepository.findDocumentInfo(fileName);

        // 删除向量数据
        int deletedCount = vectorStoreRepository.deleteDocument(fileName);

        // 尝试删除物理文件
        try {
            Path filePath = uploadDir.resolve(fileName);
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.debug("物理文件已删除: {}", filePath);
            }
        } catch (IOException e) {
            log.warn("物理文件删除失败: {}", e.getMessage());
        }

        String result = String.format("文档已删除: %s (共 %d 个片段)", fileName, deletedCount);
        log.info(result);

        return result;
    }

    /**
     * 清空所有文档
     */
    public String deleteAllDocuments() {
        log.warn("清空所有文档");

        List<DocumentInfo> documents = getAllDocuments();
        int totalChunks = 0;

        for (DocumentInfo doc : documents) {
            int deleted = vectorStoreRepository.deleteDocument(doc.getFileName());
            totalChunks += deleted;

            // 删除物理文件
            try {
                Path filePath = uploadDir.resolve(doc.getFileName());
                if (Files.exists(filePath)) {
                    Files.delete(filePath);
                }
            } catch (IOException e) {
                log.warn("物理文件删除失败: {}", e.getMessage());
            }
        }

        String result = String.format("已清空所有文档，共删除 %d 个文档，%d 个片段",
                documents.size(), totalChunks);
        log.info(result);

        return result;
    }
}