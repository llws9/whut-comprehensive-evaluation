package edu.whut.eval.application.file.query;

import java.time.LocalDateTime;

public class PublishPublicAttachmentResult {

    private final Long entryId;
    private final String fileId;
    private final String status;
    private final String scopeType;
    private final LocalDateTime publishedAt;

    public PublishPublicAttachmentResult(Long entryId,
                                         String fileId,
                                         String status,
                                         String scopeType,
                                         LocalDateTime publishedAt) {
        this.entryId = entryId;
        this.fileId = fileId;
        this.status = status;
        this.scopeType = scopeType;
        this.publishedAt = publishedAt;
    }

    public Long getEntryId() {
        return entryId;
    }

    public String getFileId() {
        return fileId;
    }

    public String getStatus() {
        return status;
    }

    public String getScopeType() {
        return scopeType;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }
}
