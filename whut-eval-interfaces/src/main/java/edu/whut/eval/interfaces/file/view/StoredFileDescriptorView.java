package edu.whut.eval.interfaces.file.view;

/**
 * 文件上传接口返回视图。
 */
public class StoredFileDescriptorView {

    private final String fileId;
    private final String bucket;
    private final String objectKey;
    private final String publicUrl;
    private final String originalFilename;
    private final String contentType;
    private final long size;

    public StoredFileDescriptorView(String fileId,
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
