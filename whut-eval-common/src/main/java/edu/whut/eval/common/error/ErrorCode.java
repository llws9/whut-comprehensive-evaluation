package edu.whut.eval.common.error;

public interface ErrorCode {

    String code();

    int httpStatus();

    String defaultMessage();
}
