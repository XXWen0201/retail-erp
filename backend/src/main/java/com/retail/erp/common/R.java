package com.retail.erp.common;

import lombok.Data;

/**
 * 统一响应体
 *
 * 全站所有接口都返回这个结构，前端只需判断 code 一个字段，
 * 不需要在每个接口里分别处理错误格式。
 *
 * @param <T> 业务数据类型
 */
@Data
public class R<T> {

    /** 业务状态码，200 表示成功 */
    private int code;

    /** 提示信息。失败时是给用户看的中文文案，不含堆栈或内部细节 */
    private String message;

    /** 业务数据 */
    private T data;

    /** 请求追踪 id，与日志中的 requestId 一致，便于线上排查 */
    private String requestId;

    private long timestamp = System.currentTimeMillis();

    private R(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> R<T> ok(T data) {
        return new R<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> fail(ErrorCode errorCode) {
        return new R<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static <T> R<T> fail(ErrorCode errorCode, String message) {
        return new R<>(errorCode.getCode(), message, null);
    }

    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null);
    }
}
