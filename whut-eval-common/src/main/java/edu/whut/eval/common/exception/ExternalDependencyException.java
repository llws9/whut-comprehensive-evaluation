package edu.whut.eval.common.exception;

import edu.whut.eval.common.error.ErrorCode;

public class ExternalDependencyException extends BaseAppException {

    public ExternalDependencyException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
