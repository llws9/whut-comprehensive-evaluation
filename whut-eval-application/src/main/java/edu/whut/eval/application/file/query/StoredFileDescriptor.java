package edu.whut.eval.application.file.query;

/**
 * 已上传文件的标准化元信息。
 * 业务层只依赖该对象，不直接消费 OSS SDK 返回结构。
 */
public class StoredFileDescriptor {

    private final String fileId;
    private final String bucket;
    private final String objectKey;
    private final String publicUrl;
    private final String originalFilename;
    private final String contentType;
    private final long size;

    public StoredFileDescriptor(String bucket,
                                String objectKey,
                                String publicUrl,
                                String originalFilename,
                                String contentType,
                                long size) {
        this(null, bucket, objectKey, publicUrl, originalFilename, contentType, size);
    }

    public StoredFileDescriptor(String fileId,
                                String bucket,
                                String objectKey,
                                String publicUrl,
                                String originalFilename,
                                String contentType,
                                long size) {
        this.fileId = fileId;
        this.bucket = bucket;
        this.objectKey = objectKey;
        this.publicUrl = publicUrl;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.size = size;
    }

    public String getFileId() {
        return fileId;
    }

    public String getBucket() {
        return bucket;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getPublicUrl() {
        return publicUrl;
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
}
