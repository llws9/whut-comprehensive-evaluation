package edu.whut.eval.common.exception;

import edu.whut.eval.common.error.CommonErrorCode;

public class ResourceNotFoundException extends BaseAppException {

    public ResourceNotFoundException(String message) {
        super(CommonErrorCode.RESOURCE_NOT_FOUND, message);
    }
}
