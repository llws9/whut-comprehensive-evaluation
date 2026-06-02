package edu.whut.eval.interfaces.exception;

import edu.whut.eval.common.api.ApiResponse;
import edu.whut.eval.common.error.CommonErrorCode;
import edu.whut.eval.common.exception.BaseAppException;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.ValidationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

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

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
        ValidationException validationException = new ValidationException(
                message.isEmpty() ? "参数校验失败" : message);
        return ResponseEntity.status(validationException.getErrorCode().httpStatus())
                .body(ApiResponse.failure(validationException.getErrorCode(), validationException.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolationException(DataIntegrityViolationException exception) {
        ConflictException conflictException = new ConflictException("数据操作冲突，可能存在重复数据或违反约束条件");
        return ResponseEntity.status(conflictException.getErrorCode().httpStatus())
                .body(ApiResponse.failure(conflictException.getErrorCode(), conflictException.getMessage()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataAccessException(DataAccessException exception) {
        return ResponseEntity.status(CommonErrorCode.SYSTEM_ERROR.httpStatus())
                .body(ApiResponse.failure(CommonErrorCode.SYSTEM_ERROR, "数据访问异常，请稍后重试"));
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(Exception exception) {
        return ResponseEntity.status(CommonErrorCode.ACCESS_DENIED.httpStatus())
                .body(ApiResponse.failure(CommonErrorCode.ACCESS_DENIED, CommonErrorCode.ACCESS_DENIED.defaultMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknownException(Exception exception) {
        return ResponseEntity.status(CommonErrorCode.SYSTEM_ERROR.httpStatus())
                .body(ApiResponse.failure(CommonErrorCode.SYSTEM_ERROR, CommonErrorCode.SYSTEM_ERROR.defaultMessage()));
    }
}
