package edu.whut.eval.common.exception;

import edu.whut.eval.common.error.CommonErrorCode;

/**
 * 对象存储或文件上传链路异常。
 */
public class FileStorageException extends ExternalDependencyException {

    public FileStorageException(String message) {
        super(CommonErrorCode.FILE_STORAGE_FAILED, message);
    }

    public FileStorageException(String message, Throwable cause) {
        super(CommonErrorCode.FILE_STORAGE_FAILED, message);
        initCause(cause);
    }
}
