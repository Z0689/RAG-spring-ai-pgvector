package com.demo.rag_demo.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 解析后的文档结果
 */
public class ParsedDocument {
    private String fileName;
    private String fileType;
    private long fileSize;
    private List<String> pages;      // 原始页面/段落内容
    private List<String> chunks;     // 分块后的内容
    private List<Integer> chunkSizes; // 每个块的大小

    public ParsedDocument() {
        this.pages = new ArrayList<>();
        this.chunks = new ArrayList<>();
        this.chunkSizes = new ArrayList<>();
    }

    public ParsedDocument(String fileName, String fileType, long fileSize) {
        this();
        this.fileName = fileName;
        this.fileType = fileType;
        this.fileSize = fileSize;
    }

    // Getters and Setters
    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public List<String> getPages() {
        return pages;
    }

    public void setPages(List<String> pages) {
        this.pages = pages;
    }

    public void addPage(String page) {
        this.pages.add(page);
    }

    public List<String> getChunks() {
        return chunks;
    }

    public void setChunks(List<String> chunks) {
        this.chunks = chunks;
    }

    public void addChunk(String chunk) {
        this.chunks.add(chunk);
    }

    public List<Integer> getChunkSizes() {
        return chunkSizes;
    }

    public void setChunkSizes(List<Integer> chunkSizes) {
        this.chunkSizes = chunkSizes;
    }

    public void addChunkSize(int size) {
        this.chunkSizes.add(size);
    }

    public int getPageCount() {
        return pages.size();
    }

    public int getChunkCount() {
        return chunks.size();
    }

    /**
     * 获取总字符数
     */
    public int getTotalChars() {
        return pages.stream().mapToInt(String::length).sum();
    }
}
