package edu.whut.eval.common.exception;

import edu.whut.eval.common.error.CommonErrorCode;

public class AuthenticationFailedException extends BaseAppException {

    public AuthenticationFailedException() {
        super(CommonErrorCode.AUTHENTICATION_FAILED);
    }

    public AuthenticationFailedException(String message) {
        super(CommonErrorCode.AUTHENTICATION_FAILED, message);
    }
}
