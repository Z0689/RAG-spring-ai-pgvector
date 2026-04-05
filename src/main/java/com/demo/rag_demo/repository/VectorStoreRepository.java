package com.demo.rag_demo.repository;

import com.demo.rag_demo.dto.DocumentInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

@Repository
public class VectorStoreRepository {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreRepository.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 查询所有文档名称（从 metadata 中提取 fileName）
     */
    public List<String> findAllDocumentNames() {
        String sql = """
            SELECT DISTINCT metadata->>'fileName' as file_name 
            FROM vector_store 
            WHERE metadata->>'fileName' IS NOT NULL
            ORDER BY file_name
        """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("file_name"));
    }

    /**
     * 查询文档详情
     */
    public DocumentInfo findDocumentInfo(String fileName) {
        String sql = """
            SELECT 
                COUNT(*) as chunk_count,
                MIN((metadata->>'uploadTime')::bigint) as upload_time,
                MIN((metadata->>'fileSize')::bigint) as file_size
            FROM vector_store 
            WHERE metadata->>'fileName' = ?
        """;

        return jdbcTemplate.query(sql, rs -> {
            if (rs.next()) {
                int chunkCount = rs.getInt("chunk_count");
                long uploadTime = rs.getLong("upload_time");
                long fileSize = rs.getLong("file_size");

                // 判断文件类型
                String fileType = "unknown";
                if (fileName != null) {
                    if (fileName.toLowerCase().endsWith(".pdf")) {
                        fileType = "PDF";
                    } else if (fileName.toLowerCase().endsWith(".txt")) {
                        fileType = "TXT";
                    } else if (fileName.toLowerCase().endsWith(".md")) {
                        fileType = "Markdown";
                    } else if (fileName.toLowerCase().endsWith(".docx")) {
                        fileType = "Word";
                    }
                }

                return new DocumentInfo(fileName, chunkCount, uploadTime, fileSize, fileType);
            }
            return null;
        }, fileName);
    }

    /**
     * 删除文档的所有向量片段
     */
    public int deleteDocument(String fileName) {
        String sql = "DELETE FROM vector_store WHERE metadata->>'fileName' = ?";
        int deletedCount = jdbcTemplate.update(sql, fileName);
        log.info("删除文档: fileName={}, 删除片段数={}", fileName, deletedCount);
        return deletedCount;
    }

    /**
     * 获取文档的片段列表
     */
    public List<String> findDocumentChunks(String fileName, int limit) {
        String sql = """
            SELECT content 
            FROM vector_store 
            WHERE metadata->>'fileName' = ?
            ORDER BY (metadata->>'chunkIndex')::int
            LIMIT ?
        """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            String content = rs.getString("content");
            // 截取前200字符用于预览
            if (content.length() > 200) {
                content = content.substring(0, 200) + "...";
            }
            return content;
        }, fileName, limit);
    }

    /**
     * 获取文档片段总数
     */
    public int getDocumentChunkCount(String fileName) {
        String sql = "SELECT COUNT(*) FROM vector_store WHERE metadata->>'fileName' = ?";
        return jdbcTemplate.queryForObject(sql, Integer.class, fileName);
    }

    /**
     * 检查文档是否存在
     */
    public boolean documentExists(String fileName) {
        String sql = "SELECT COUNT(*) FROM vector_store WHERE metadata->>'fileName' = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, fileName);
        return count != null && count > 0;
    }
}
