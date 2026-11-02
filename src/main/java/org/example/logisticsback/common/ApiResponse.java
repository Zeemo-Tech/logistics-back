package org.example.logisticsback.common;

public record ApiResponse<T>(int code, T data, String message) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(200, data, "操作成功");
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(200, data, message);
    }

    public static <T> ApiResponse<T> failure(int code, String message) {
        return new ApiResponse<>(code, null, message);
    }
}
