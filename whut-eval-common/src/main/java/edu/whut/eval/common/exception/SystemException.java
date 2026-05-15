package edu.whut.eval.common.exception;

import edu.whut.eval.common.error.CommonErrorCode;

public class SystemException extends BaseAppException {

    public SystemException(String message) {
        super(CommonErrorCode.SYSTEM_ERROR, message);
    }

    public SystemException(String message, Throwable cause) {
        super(CommonErrorCode.SYSTEM_ERROR, message);
        initCause(cause);
    }
}
