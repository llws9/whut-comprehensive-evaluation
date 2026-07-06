package edu.whut.eval.application.file.query;

import java.time.LocalDateTime;

public class FileAssetDescriptor {

    private final String fileId;
    private final String storageKey;
    private final String originalFilename;
    private final String contentType;
    private final Long size;
    private final Long uploaderUserId;
    private final String status;
    private final LocalDateTime createdAt;

    public FileAssetDescriptor(String fileId, String storageKey, String originalFilename, String contentType,
                               Long size, Long uploaderUserId, String status, LocalDateTime createdAt) {
        this.fileId = fileId;
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.size = size;
        this.uploaderUserId = uploaderUserId;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getFileId() {
        return fileId;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getSize() {
        return size;
    }

    public Long getUploaderUserId() {
        return uploaderUserId;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
