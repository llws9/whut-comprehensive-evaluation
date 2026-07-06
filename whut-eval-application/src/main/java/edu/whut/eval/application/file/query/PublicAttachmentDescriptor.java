package edu.whut.eval.application.file.query;

import java.time.LocalDateTime;

public class PublicAttachmentDescriptor {

    private final Long entryId;
    private final String fileId;
    private final String displayName;
    private final String description;
    private final String categoryCode;
    private final String originalFilename;
    private final String contentType;
    private final Long size;
    private final LocalDateTime publishedAt;
    private final Integer sortNo;

    public PublicAttachmentDescriptor(Long entryId, String fileId, String displayName, String description,
                                      String categoryCode, String originalFilename, String contentType,
                                      Long size, LocalDateTime publishedAt, Integer sortNo) {
        this.entryId = entryId;
        this.fileId = fileId;
        this.displayName = displayName;
        this.description = description;
        this.categoryCode = categoryCode;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.size = size;
        this.publishedAt = publishedAt;
        this.sortNo = sortNo;
    }

    public Long getEntryId() {
        return entryId;
    }

    public String getFileId() {
        return fileId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String getCategoryCode() {
        return categoryCode;
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

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public Integer getSortNo() {
        return sortNo;
    }
}
