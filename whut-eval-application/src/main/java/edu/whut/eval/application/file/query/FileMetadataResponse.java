package edu.whut.eval.application.file.query;

import java.time.LocalDateTime;

public class FileMetadataResponse {

    private final String fileId;
    private final String originalFilename;
    private final String contentType;
    private final long size;
    private final String status;
    private final String sourceType;
    private final boolean canPreview;
    private final boolean canDownload;
    private final LocalDateTime createdAt;

    public FileMetadataResponse(String fileId, String originalFilename, String contentType, long size,
                                String status, LocalDateTime createdAt) {
        this(fileId, originalFilename, contentType, size, status, null, true, true, createdAt);
    }

    public FileMetadataResponse(String fileId,
                                String originalFilename,
                                String contentType,
                                long size,
                                String status,
                                String sourceType,
                                boolean canPreview,
                                boolean canDownload,
                                LocalDateTime createdAt) {
        this.fileId = fileId;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.size = size;
        this.status = status;
        this.sourceType = sourceType;
        this.canPreview = canPreview;
        this.canDownload = canDownload;
        this.createdAt = createdAt;
    }

    public String getFileId() {
        return fileId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSize() {
        return size;
    }

    public String getStatus() {
        return status;
    }

    public String getSourceType() {
        return sourceType;
    }

    public boolean isCanPreview() {
        return canPreview;
    }

    public boolean isCanDownload() {
        return canDownload;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
