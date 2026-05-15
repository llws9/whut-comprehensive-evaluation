package edu.whut.eval.common.exception;

import edu.whut.eval.common.error.ErrorCode;

public class PersistenceOperationException extends BaseAppException {

    public PersistenceOperationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
