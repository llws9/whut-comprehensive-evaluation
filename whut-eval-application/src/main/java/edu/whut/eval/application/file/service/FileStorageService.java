package edu.whut.eval.application.file.service;

import edu.whut.eval.application.file.command.UploadFileCommand;
import edu.whut.eval.application.file.query.StoredFileDescriptor;

/**
 * 文件存储服务抽象。
 * 由 application 层依赖，具体对象存储厂商由 infra 层适配实现。
 */
public interface FileStorageService {

    /**
     * 将文件内容写入对象存储并返回标准化元信息。
     */
    StoredFileDescriptor store(UploadFileCommand command);
}
