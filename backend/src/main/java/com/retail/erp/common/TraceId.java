package com.retail.erp.common;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * 请求追踪 id 工具
 *
 * 每个 HTTP 请求生成一个 id，写入 MDC，日志模板里输出 %X{requestId}。
 * 同时回写到响应体的 requestId 字段和响应头，方便用户截图报错时直接定位日志。
 */
public final class TraceId {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private TraceId() {
    }

    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public static void set(String requestId) {
        MDC.put(MDC_KEY, requestId);
    }

    public static String get() {
        String id = MDC.get(MDC_KEY);
        return id == null ? "-" : id;
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
