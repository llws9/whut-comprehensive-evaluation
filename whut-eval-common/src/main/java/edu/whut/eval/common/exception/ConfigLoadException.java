package edu.whut.eval.common.exception;

import edu.whut.eval.common.error.CommonErrorCode;

public class ConfigLoadException extends ExternalDependencyException {

    public ConfigLoadException(String message) {
        super(CommonErrorCode.NACOS_CONFIG_LOAD_FAILED, message);
    }

    public ConfigLoadException(String message, Throwable cause) {
        super(CommonErrorCode.NACOS_CONFIG_LOAD_FAILED, message);
        initCause(cause);
    }
}
