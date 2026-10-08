package com.attijariwafa.custody.EpargneOPCVM.api;

import com.attijariwafa.custody.EpargneOPCVM.api.dto.ApiErrorResponse;
import com.attijariwafa.custody.EpargneOPCVM.support.ApplicationException;
import com.attijariwafa.custody.EpargneOPCVM.support.ErrorCode;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApplicationException.class)
    ResponseEntity<ApiErrorResponse> handleApplicationException(ApplicationException ex) {
        ErrorCode code = ex.getErrorCode();
        return ResponseEntity.status(code.httpStatus())
                .body(new ApiErrorResponse(code.httpStatus().value(), code.code(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        String message = details.isBlank() ? "Validation failed" : details;
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(400, ErrorCode.VALIDATION_ERROR.code(), message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleUnreadableMessage(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(400, ErrorCode.VALIDATION_ERROR.code(), "Invalid request body"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        String message = ex.getMessage();
        if (ex.getCause() != null && ex.getCause().getMessage() != null) {
            message = message + " | " + ex.getCause().getMessage();
        }
        return ResponseEntity.internalServerError()
                .body(new ApiErrorResponse(500, ErrorCode.TECHNICAL_ERROR.code(), message));
    }
}
