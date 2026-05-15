package edu.whut.eval.infra.nacos.model.typed;

/**
 * 阿里云 OSS 对象存储配置。
 * 该配置通过 Nacos typed config 机制加载，供后续统一文件上传链路复用。
 * 字段命名已与 OSS SDK 初始化和对象 key 生成所需参数对齐。
 */
public class OssStorageConfig {

    private boolean enabled;
    /**
     * OSS 访问端点，例如 https://oss-cn-shanghai.aliyuncs.com
     */
    private String endpoint;
    /**
     * OSS 区域，例如 cn-shanghai。
     */
    private String region;
    /**
     * RAM AccessKey ID。
     */
    private String accessKeyId;
    /**
     * RAM AccessKey Secret。
     */
    private String accessKeySecret;
    /**
     * 目标 Bucket 名称。
     */
    private String bucket;
    /**
     * 对外访问的基础 URL，可选。
     */
    private String publicBaseUrl;
    /**
     * 对象 key 的统一前缀，可选。
     */
    private String keyPrefix;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public String getAccessKeySecret() {
        return accessKeySecret;
    }

    public void setAccessKeySecret(String accessKeySecret) {
        this.accessKeySecret = accessKeySecret;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }
}
