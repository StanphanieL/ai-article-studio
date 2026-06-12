package com.nana.aiarticlestudio.exception;

import com.nana.aiarticlestudio.common.BaseResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public BaseResponse<?> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return BaseResponse.fail(message);
    }

    @ExceptionHandler(Exception.class)
    public BaseResponse<?> handleException(Exception e) {
        return BaseResponse.fail(e.getMessage());
    }
}