package org.example.logisticsback.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusiness(BusinessException exception) {
        return ApiResponse.failure(exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException exception) {
        var error = exception.getBindingResult().getFieldError();
        return ApiResponse.failure(HttpStatus.BAD_REQUEST.value(),
                error == null ? "请求参数不合法" : error.getDefaultMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResponse<Void> handleConstraint(ConstraintViolationException exception) {
        return ApiResponse.failure(HttpStatus.BAD_REQUEST.value(), exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleUnknown(Exception exception, HttpServletRequest request) {
        return ApiResponse.failure(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "服务暂时不可用，请稍后重试");
    }
}
