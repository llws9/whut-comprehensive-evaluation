package edu.whut.eval.application.finalrecord.exporting;

import edu.whut.eval.common.error.CommonErrorCode;
import edu.whut.eval.common.exception.BaseAppException;

public class FinalScoreExportGenerationException extends BaseAppException {

    public FinalScoreExportGenerationException(String message) {
        super(CommonErrorCode.FILE_STORAGE_FAILED, message);
    }

    public FinalScoreExportGenerationException(String message, Throwable cause) {
        super(CommonErrorCode.FILE_STORAGE_FAILED, message);
        initCause(cause);
    }
}
