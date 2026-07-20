package com.hf.easydelivery.common.exception;

import com.hf.easydelivery.common.response.AppResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.OK) // Maintain HTTP 200 for business level errors as expected by the Volley setup
    public AppResponse<Void> handleBizException(BizException ex) {
        log.warn("Business Exception: [{}] {}", ex.getBizCode(), ex.getMessage());
        return AppResponse.fail(ex.getBizCode(), ex.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public AppResponse<Void> handleUnauthorizedException(UnauthorizedException ex) {
        log.warn("Unauthorized Access: {}", ex.getMessage());
        return AppResponse.fail("AUTH.UNAUTHORIZED", ex.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public AppResponse<Void> handleForbiddenException(ForbiddenException ex) {
        log.warn("Forbidden Access: {}", ex.getMessage());
        return AppResponse.fail("AUTH.FORBIDDEN", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public AppResponse<Void> handleValidationException(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .reduce((a, b) -> a + ", " + b)
                .orElse("Validation failed");
        log.warn("Validation Error: {}", msg);
        return AppResponse.fail("PARAM.INVALID", msg);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public AppResponse<Void> handleMissingParamException(MissingServletRequestParameterException ex) {
        log.warn("Missing parameter: {}", ex.getParameterName());
        return AppResponse.fail("PARAM.MISSING", "Required parameter is missing: " + ex.getParameterName());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public AppResponse<Void> handleNotFound(NoResourceFoundException ex) {
        return AppResponse.fail("RESOURCE.NOT.FOUND", "Requested resource was not found");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public AppResponse<Void> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return AppResponse.fail("METHOD.NOT.ALLOWED", "Request method is not supported");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public AppResponse<Void> handleMaxUploadSizeException(MaxUploadSizeExceededException ex) {
        log.warn("Upload size limit exceeded");
        return AppResponse.fail("UPLOAD.LIMIT.EXCEEDED", "File size exceeds maximum upload limit");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public AppResponse<Void> handleGeneralException(Exception ex) {
        log.error("Unhandled System Exception", ex);
        return AppResponse.fail("SYSTEM.ERROR", "An unexpected system error occurred. Please try again later.");
    }
}
