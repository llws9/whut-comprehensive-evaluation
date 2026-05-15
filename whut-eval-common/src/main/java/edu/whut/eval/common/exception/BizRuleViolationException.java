package edu.whut.eval.common.exception;

import edu.whut.eval.common.error.CommonErrorCode;

public class BizRuleViolationException extends BaseAppException {

    public BizRuleViolationException(String message) {
        super(CommonErrorCode.BIZ_RULE_VIOLATION, message);
    }
}
