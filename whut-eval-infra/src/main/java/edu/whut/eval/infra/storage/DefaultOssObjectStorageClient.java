package edu.whut.eval.infra.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import edu.whut.eval.common.exception.FileStorageException;
import edu.whut.eval.common.log.AppLog;
import edu.whut.eval.domain.config.model.OssStorageConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * 基于阿里云 OSS SDK 的对象存储客户端实现。
 */
@Component
public class DefaultOssObjectStorageClient implements OssObjectStorageClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultOssObjectStorageClient.class);

    @Override
    public StoredOssObject putObject(OssStorageConfig config,
                                     String objectKey,
                                     InputStream inputStream,
                                     long size,
                                     String contentType) {
        OSS ossClient = null;
        try {
            ossClient = new OSSClientBuilder().build(
                    config.getEndpoint(),
                    config.getAccessKeyId(),
                    config.getAccessKeySecret()
            );
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(size);
            if (contentType != null && !contentType.isBlank()) {
                metadata.setContentType(contentType);
            }
            ossClient.putObject(config.getBucket(), objectKey, inputStream, metadata);
            return new StoredOssObject(
                    config.getBucket(),
                    objectKey,
                    buildPublicUrl(config, objectKey)
            );
        } catch (Exception exception) {
            AppLog.error(log, exception, "file.upload.oss.put-object.failed",
                    "endpoint", config.getEndpoint(),
                    "bucket", config.getBucket(),
                    "objectKey", objectKey,
                    "contentType", contentType,
                    "size", size);
            throw new FileStorageException("Failed to upload file to OSS", exception);
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
    }

    private String buildPublicUrl(OssStorageConfig config, String objectKey) {
        if (config.getPublicBaseUrl() == null || config.getPublicBaseUrl().isBlank()) {
            return null;
        }
        String baseUrl = config.getPublicBaseUrl().endsWith("/")
                ? config.getPublicBaseUrl().substring(0, config.getPublicBaseUrl().length() - 1)
                : config.getPublicBaseUrl();
        return baseUrl + "/" + objectKey;
    }
}
