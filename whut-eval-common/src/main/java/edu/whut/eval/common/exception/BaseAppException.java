package edu.whut.eval.common.exception;

import edu.whut.eval.common.error.ErrorCode;

public abstract class BaseAppException extends RuntimeException {

    private final ErrorCode errorCode;

    protected BaseAppException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
    }

    protected BaseAppException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
