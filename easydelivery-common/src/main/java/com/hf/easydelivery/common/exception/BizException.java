package com.hf.easydelivery.common.exception;

public class BizException extends RuntimeException {
    private final String bizCode;

    public BizException(String bizCode, String message) {
        super(message);
        this.bizCode = bizCode;
    }

    public String getBizCode() {
        return bizCode;
    }
}
