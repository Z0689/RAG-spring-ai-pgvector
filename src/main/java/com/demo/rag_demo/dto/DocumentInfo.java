package com.demo.rag_demo.dto;

public class DocumentInfo {
    private String fileName;
    private int chunkCount;
    private long uploadTime;
    private long fileSize;
    private String fileType;

    public DocumentInfo() {}

    public DocumentInfo(String fileName, int chunkCount, long uploadTime, long fileSize, String fileType) {
        this.fileName = fileName;
        this.chunkCount = chunkCount;
        this.uploadTime = uploadTime;
        this.fileSize = fileSize;
        this.fileType = fileType;
    }

    // Getters and Setters
    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(int chunkCount) {
        this.chunkCount = chunkCount;
    }

    public long getUploadTime() {
        return uploadTime;
    }

    public void setUploadTime(long uploadTime) {
        this.uploadTime = uploadTime;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    /**
     * 格式化显示文件大小
     */
    public String getFormattedFileSize() {
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.2f KB", fileSize / 1024.0);
        } else {
            return String.format("%.2f MB", fileSize / (1024.0 * 1024.0));
        }
    }

    /**
     * 格式化显示上传时间
     */
    public String getFormattedUploadTime() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                .format(new java.util.Date(uploadTime));
    }
}
