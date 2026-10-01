package com.retail.erp.security;

import com.retail.erp.common.BizException;
import com.retail.erp.common.ErrorCode;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * 鉴权拦截器
 *
 * 职责只有两件事：认证（你是谁）+ 授权（你能不能干这事）。
 * 不做任何业务判断，保持单一职责。
 *
 * 生命周期注意点：
 *   preHandle 里 set，afterCompletion 里必须 clear。
 *   Tomcat 线程是复用的，漏掉 clear 会把 A 用户的身份带给 B 用户 —— 这是最危险的一类越权 bug。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        // 放行非控制器请求（静态资源、错误页等）
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // 预检请求不带令牌，直接放行由 CORS 配置处理
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = resolveToken(request);
        if (token == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未携带登录凭证，请先登录");
        }

        Claims claims = jwtService.parse(token);
        LoginUser user = jwtService.toLoginUser(claims);
        UserContext.set(user);

        checkRole(handlerMethod, user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 必须清理，防止线程复用造成身份串台
        UserContext.clear();
    }

    /**
     * 从 Authorization 头取令牌
     *
     * 刻意不支持从 URL 查询参数取令牌 —— 查询参数会进入浏览器历史、Nginx access log、
     * Referer 头，等于把凭据公开。同理前端也不会把令牌放进 localStorage。
     */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(AUTH_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    /** 方法级注解优先于类级注解 */
    private void checkRole(HandlerMethod handlerMethod, LoginUser user) {
        RequireRole annotation = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (annotation == null) {
            annotation = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        if (annotation == null) {
            return;
        }
        // ADMIN 是超级角色，天然放行
        if (user.isAdmin()) {
            return;
        }
        boolean allowed = Arrays.stream(annotation.value())
                .anyMatch(role -> role.equalsIgnoreCase(user.getRole()));
        if (!allowed) {
            log.warn("越权访问被拦截 username={} role={} handler={}",
                    user.getUsername(), user.getRole(), handlerMethod.getMethod().getName());
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }
}
