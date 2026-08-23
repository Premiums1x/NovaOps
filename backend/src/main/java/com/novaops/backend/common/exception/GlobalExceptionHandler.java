package com.novaops.backend.common.exception;

import com.novaops.backend.common.api.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException exception) {
    return ResponseEntity.ok(ApiResponse.failure(exception.getCode(), exception.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Object>> handleValidationException(MethodArgumentNotValidException exception) {
    String message = exception.getBindingResult().getFieldErrors().stream()
        .map(FieldError::getDefaultMessage)
        .collect(Collectors.joining("；"));
    return ResponseEntity.ok(ApiResponse.failure(400, message.isBlank() ? "请求参数不合法" : message));
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiResponse<Object>> handleConstraintViolation(ConstraintViolationException exception) {
    return ResponseEntity.ok(ApiResponse.failure(400, exception.getMessage()));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ApiResponse<Object>> handleUploadTooLarge(MaxUploadSizeExceededException exception) {
    return ResponseEntity.ok(ApiResponse.failure(400, "上传文件超过允许的大小上限"));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Object>> handleException(Exception exception) {
    return ResponseEntity.ok(ApiResponse.failure(500, "服务异常，请稍后重试"));
  }
}
