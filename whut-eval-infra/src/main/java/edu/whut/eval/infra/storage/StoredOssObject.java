package edu.whut.eval.infra.storage;

/**
 * OSS 客户端上传后的标准返回对象。
 * infra 内部先收敛为该对象，再映射到 application 层的 `StoredFileDescriptor`。
 */
public class StoredOssObject {

    private final String bucket;
    private final String objectKey;
    private final String publicUrl;

    public StoredOssObject(String bucket, String objectKey, String publicUrl) {
        this.bucket = bucket;
        this.objectKey = objectKey;
        this.publicUrl = publicUrl;
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
}
