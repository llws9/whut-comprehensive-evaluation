package edu.whut.eval.application.platform.service;

import edu.whut.eval.common.error.CommonErrorCode;
import edu.whut.eval.common.exception.ExternalDependencyException;

public class ConfigPublishException extends ExternalDependencyException {

    public ConfigPublishException(String message) {
        super(CommonErrorCode.FILE_STORAGE_FAILED, message);
    }

    public ConfigPublishException(String message, Throwable cause) {
        super(CommonErrorCode.FILE_STORAGE_FAILED, message);
        initCause(cause);
    }
}
