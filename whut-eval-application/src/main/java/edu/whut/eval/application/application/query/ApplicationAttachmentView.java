package edu.whut.eval.application.application.query;

import java.time.LocalDateTime;

public class ApplicationAttachmentView {

    private String fileId;
    private String originalFilename;
    private String contentType;
    private Long size;
    private Integer sortNo;
    private String sourceType;
    private String accessUrl;
    private String accessMode;
    private LocalDateTime expiresAt;

    public ApplicationAttachmentView() {
    }

    public ApplicationAttachmentView(String fileId,
                                     String originalFilename,
                                     String contentType,
                                     Long size,
                                     Integer sortNo) {
        this(fileId, originalFilename, contentType, size, sortNo, null, null, null, null);
    }

    public ApplicationAttachmentView(String fileId,
                                     String originalFilename,
                                     String contentType,
                                     Long size,
                                     Integer sortNo,
                                     String sourceType,
                                     String accessUrl,
                                     String accessMode,
                                     LocalDateTime expiresAt) {
        this.fileId = fileId;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.size = size;
        this.sortNo = sortNo;
        this.sourceType = sourceType;
        this.accessUrl = accessUrl;
        this.accessMode = accessMode;
        this.expiresAt = expiresAt;
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getAccessUrl() {
        return accessUrl;
    }

    public void setAccessUrl(String accessUrl) {
        this.accessUrl = accessUrl;
    }

    public String getAccessMode() {
        return accessMode;
    }

    public void setAccessMode(String accessMode) {
        this.accessMode = accessMode;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}
