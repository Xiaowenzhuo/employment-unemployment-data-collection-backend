package com.example.employment.backend.common;

import com.example.employment.backend.week1.identity.service.AuthService.InvalidCredentialsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, Map<String, String>>>> validation(
            MethodArgumentNotValidException exception) {
        Map<String, String> fields = exception.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(error -> error.getField(),
                        error -> error.getDefaultMessage() == null ? "无效值" : error.getDefaultMessage(),
                        (first, second) -> first));
        return ResponseEntity.badRequest().body(
                ApiResponse.error("BAD_REQUEST", "请求参数不合法", Map.of("fieldErrors", fields)));
    }

    @ExceptionHandler(FieldErrorException.class)
    public ResponseEntity<ApiResponse<Map<String, Map<String, String>>>> fieldError(
            FieldErrorException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.error("BAD_REQUEST", "请求参数不合法",
                Map.of("fieldErrors", exception.fieldErrors())));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, BadRequestException.class})
    public ResponseEntity<ApiResponse<Void>> badRequest(Exception exception) {
        return ResponseEntity.badRequest().body(ApiResponse.error("BAD_REQUEST", "请求参数不合法"));
    }

    @ExceptionHandler({NoResourceFoundException.class, NotFoundException.class})
    public ResponseEntity<ApiResponse<Void>> notFound(Exception exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("NOT_FOUND", "资源不存在"));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> forbidden(ForbiddenException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("FORBIDDEN", exception.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> conflict(ConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("CONFLICT", exception.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> unauthorized(InvalidCredentialsException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("UNAUTHORIZED", "账号或密码错误"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> internalError(Exception exception) {
        log.error("Unhandled request error", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "服务器内部错误"));
    }
}
