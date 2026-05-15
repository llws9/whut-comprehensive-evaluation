package edu.whut.eval.domain.application.model;

/**
 * 申请附件引用。
 * 该值对象只保留业务侧稳定引用和展示所需的最小文件元信息。
 */
public class AttachmentRef {

    private final String fileId;
    private final String storageKey;
    private final String originalFilename;
    private final String contentType;
    private final long size;
    private final Long uploadedBy;

    public AttachmentRef(String fileId,
                         String storageKey,
                         String originalFilename,
                         String contentType,
                         long size,
                         Long uploadedBy) {
        this.fileId = fileId;
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.size = size;
        this.uploadedBy = uploadedBy;
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

    public long getSize() {
        return size;
    }

    public Long getUploadedBy() {
        return uploadedBy;
    }
}
