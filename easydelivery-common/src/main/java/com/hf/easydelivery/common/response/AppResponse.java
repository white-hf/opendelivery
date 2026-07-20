package com.hf.easydelivery.common.response;

public class AppResponse<T> {
    private String biz_code;
    private String biz_message;
    private T biz_data;

    public AppResponse() {}

    public AppResponse(String biz_code, String biz_message, T biz_data) {
        this.biz_code = biz_code;
        this.biz_message = biz_message;
        this.biz_data = biz_data;
    }

    public static <T> AppResponse<T> success(T data) {
        return new AppResponse<>("COMMON.QUERY.SUCCESS", "Success", data);
    }

    public static <T> AppResponse<T> success(String message, T data) {
        return new AppResponse<>("COMMON.QUERY.SUCCESS", message, data);
    }

    public static <T> AppResponse<T> fail(String bizCode, String message) {
        return new AppResponse<>(bizCode, message, null);
    }

    // Getters and Setters
    public String getBiz_code() { return biz_code; }
    public void setBiz_code(String biz_code) { this.biz_code = biz_code; }

    public String getBiz_message() { return biz_message; }
    public void setBiz_message(String biz_message) { this.biz_message = biz_message; }

    public T getBiz_data() { return biz_data; }
    public void setBiz_data(T biz_data) { this.biz_data = biz_data; }
}
