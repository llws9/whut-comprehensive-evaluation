package edu.whut.eval.interfaces.exception;

import edu.whut.eval.common.api.ApiResponse;
import edu.whut.eval.common.error.CommonErrorCode;
import edu.whut.eval.common.exception.BaseAppException;
import edu.whut.eval.common.exception.ValidationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseAppException.class)
    public ResponseEntity<ApiResponse<Void>> handleBaseAppException(BaseAppException exception) {
        return ResponseEntity.status(exception.getErrorCode().httpStatus())
                .body(ApiResponse.failure(exception.getErrorCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse(CommonErrorCode.VALIDATION_ERROR.defaultMessage());
        ValidationException validationException = new ValidationException(message);
        return ResponseEntity.status(validationException.getErrorCode().httpStatus())
                .body(ApiResponse.failure(validationException.getErrorCode(), validationException.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknownException(Exception exception) {
        return ResponseEntity.status(CommonErrorCode.SYSTEM_ERROR.httpStatus())
                .body(ApiResponse.failure(CommonErrorCode.SYSTEM_ERROR, CommonErrorCode.SYSTEM_ERROR.defaultMessage()));
    }
}
