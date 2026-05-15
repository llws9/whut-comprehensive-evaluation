package edu.whut.eval.common.api;

import edu.whut.eval.common.error.ErrorCode;

public record ApiResponse<T>(boolean success, String code, String message, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "OK", "success", data);
    }

    public static ApiResponse<Void> failure(ErrorCode errorCode, String message) {
        return new ApiResponse<>(false, errorCode.code(), message, null);
    }
}
