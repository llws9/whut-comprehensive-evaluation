package edu.whut.eval.application.file.query;

import java.time.LocalDateTime;

public class FileAccessUrlResponse {

    private final String fileId;
    private final String accessUrl;
    private final String accessMode;
    private final LocalDateTime expiresAt;

    public FileAccessUrlResponse(String fileId, String accessUrl, LocalDateTime expiresAt) {
        this(fileId, accessUrl, null, expiresAt);
    }

    public FileAccessUrlResponse(String fileId, String accessUrl, String accessMode, LocalDateTime expiresAt) {
        this.fileId = fileId;
        this.accessUrl = accessUrl;
        this.accessMode = accessMode;
        this.expiresAt = expiresAt;
    }

    public String getFileId() {
        return fileId;
    }

    public String getAccessUrl() {
        return accessUrl;
    }

    public String getAccessMode() {
        return accessMode;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
}
