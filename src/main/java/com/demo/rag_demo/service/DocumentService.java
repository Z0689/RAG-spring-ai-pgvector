package com.demo.rag_demo.service;

import com.demo.rag_demo.dto.Source;
import com.demo.rag_demo.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
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

    private final Path uploadDir = Path.of("./uploads");

    /**
     * 上传并处理 PDF 文档（带日志记录）
     */
    public String uploadAndProcessPdf(MultipartFile file) throws IOException {
        long startTime = System.currentTimeMillis();
        String fileName = file.getOriginalFilename();
        log.info("开始处理文档上传: fileName={}, size={} bytes", fileName, file.getSize());

        try {
            // 1. 创建上传目录
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
                log.debug("创建上传目录: {}", uploadDir.toAbsolutePath());
            }

            // 2. 保存文件到本地
            Path filePath = uploadDir.resolve(fileName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            log.debug("文件已保存: {}", filePath);

            // 3. 解析 PDF 文档
            Resource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            };
            PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource);
            List<Document> documents = pdfReader.get();
            log.info("PDF 解析完成，共 {} 页", documents.size());

            // 4. 文本分块
            TokenTextSplitter splitter = new TokenTextSplitter(800, 100, 10, 10000, true);
            List<Document> chunks = splitter.apply(documents);

            // 修复2：正确设置元数据
            for (int i = 0; i < chunks.size(); i++) {
                Document chunk = chunks.get(i);
                chunk.getMetadata().put("fileName", fileName);
                chunk.getMetadata().put("fileSize", file.getSize());
                chunk.getMetadata().put("uploadTime", String.valueOf(System.currentTimeMillis()));
                chunk.getMetadata().put("chunkIndex", i);
                chunk.getMetadata().put("totalChunks", chunks.size());
            }

            log.debug("元数据设置完成: fileName={}, chunks={}", fileName, chunks.size());

            // 5. 向量化并存储
            vectorStore.add(chunks);

            long elapsedMs = System.currentTimeMillis() - startTime;
            log.info("文档处理完成！耗时: {}ms, 原始页数: {}, 分块数: {}",
                    elapsedMs, documents.size(), chunks.size());

            return String.format("文档处理完成！原始文档 %d 页，分块后 %d 个片段，耗时 %dms",
                    documents.size(), chunks.size(), elapsedMs);

        } catch (Exception e) {
            log.error("文档处理失败: {}", e.getMessage(), e);
            throw new BusinessException("文档处理失败: " + e.getMessage());
        }
    }

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
            // 修复3：正确获取 fileName
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
                    chunkIndex = Integer.parseInt(indexObj.toString());
                }
            }

            // 获取相似度分数
            double score = doc.getScore() != null ? doc.getScore() : 0.0;

            // 添加详细日志
            log.debug("检索结果: fileName={}, score={:.2f}, chunkIndex={}, content={}",
                    fileName, score, chunkIndex, truncate(doc.getText(), 100));

            sources.add(new Source(
                    truncate(doc.getText(), 200),  // 截取前200字符，更易阅读
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
}