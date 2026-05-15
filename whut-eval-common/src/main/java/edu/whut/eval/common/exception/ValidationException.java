package edu.whut.eval.common.exception;

import edu.whut.eval.common.error.CommonErrorCode;

public class ValidationException extends BaseAppException {

    public ValidationException(String message) {
        super(CommonErrorCode.VALIDATION_ERROR, message);
    }
}
