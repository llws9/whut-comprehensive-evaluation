package edu.whut.eval.common.exception;

import edu.whut.eval.common.error.CommonErrorCode;

public class AccessDeniedAppException extends BaseAppException {

    public AccessDeniedAppException(String message) {
        super(CommonErrorCode.ACCESS_DENIED, message);
    }
}
