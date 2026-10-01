package com.retail.erp.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 请求追踪过滤器
 *
 * 优先级最高，保证后续所有环节（鉴权、业务、异常处理）的日志都带上同一个 requestId。
 * 注意 finally 里必须清理 MDC，否则 Tomcat 复用线程会造成 id 串台。
 */
@Slf4j
@Component
@Order(Integer.MIN_VALUE)
public class RequestIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // 允许上游网关传入，便于跨服务串联；没有则本地生成
        String requestId = request.getHeader(TraceId.HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = TraceId.generate();
        }
        TraceId.set(requestId);
        response.setHeader(TraceId.HEADER, requestId);

        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            long cost = System.currentTimeMillis() - start;
            // 慢请求单独标记，方便排查性能问题
            if (cost > 1000) {
                log.warn("慢请求 {} {} status={} cost={}ms",
                        request.getMethod(), request.getRequestURI(), response.getStatus(), cost);
            } else {
                log.info("{} {} status={} cost={}ms",
                        request.getMethod(), request.getRequestURI(), response.getStatus(), cost);
            }
            TraceId.clear();
        }
    }

    /** 静态资源与接口文档不需要追踪，避免刷屏 */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/swagger-ui")
                || uri.startsWith("/v3/api-docs")
                || uri.startsWith("/doc.html")
                || uri.startsWith("/webjars")
                || uri.equals("/favicon.ico");
    }
}
