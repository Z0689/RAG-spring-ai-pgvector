package com.demo.rag_demo.dto;

public class Source {
    private String content;      // 原文片段（已截断）
    private double score;        // 相似度分数
    private String documentName; // 来源文档名
    private int chunkIndex;      // 片段索引

    public Source() {}

    public Source(String content, double score, String documentName) {
        this.content = content;
        this.score = score;
        this.documentName = documentName;
        this.chunkIndex = -1;
    }

    public Source(String content, double score, String documentName, int chunkIndex) {
        this.content = content;
        this.score = score;
        this.documentName = documentName;
        this.chunkIndex = chunkIndex;
    }

    /**
     * 辅助方法：格式化显示来源信息
     */
    public String getFormattedSource() {
        String chunkInfo = chunkIndex >= 0 ? " (片段 " + chunkIndex + ")" : "";
        return String.format("【来源】%s%s (相似度: %.2f%%)\n%s\n",
                documentName, chunkInfo, score * 100, content);
    }

    // Getters and Setters
    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public String getDocumentName() {
        return documentName;
    }

    public void setDocumentName(String documentName) {
        this.documentName = documentName;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }
}