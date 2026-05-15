package edu.whut.eval.infra.storage;

import edu.whut.eval.infra.nacos.model.typed.OssStorageConfig;

import java.io.InputStream;

/**
 * OSS 客户端适配器。
 * 该接口屏蔽 OSS SDK 的直接使用，便于 `OssFileStorageService` 做单元测试。
 */
public interface OssObjectStorageClient {

    /**
     * 将字节流写入 OSS，返回已落盘对象的核心标识信息。
     */
    StoredOssObject putObject(OssStorageConfig config,
                              String objectKey,
                              InputStream inputStream,
                              long size,
                              String contentType);
}
