package edu.whut.eval.common.exception;

import edu.whut.eval.common.error.CommonErrorCode;

public class ConflictException extends BaseAppException {

    public ConflictException(String message) {
        super(CommonErrorCode.RESOURCE_CONFLICT, message);
    }
}
