package com.retail.erp.config;

import com.retail.erp.security.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层配置：跨域 + 鉴权拦截器
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final AppProperties props;

    /** 无需登录即可访问的路径 */
    private static final String[] WHITE_LIST = {
            "/api/auth/login",
            "/api/auth/refresh",
            // 退出也放行：访问令牌过期后用户仍应能正常登出，
            // 否则会出现「想退出却提示登录已过期」的别扭体验。
            // 它本身只吊销请求里带来的那个刷新令牌，不存在越权风险。
            "/api/auth/logout",
            "/api/auth/captcha",
            "/api/public/**",
            "/actuator/health",
            "/actuator/info",
            "/doc.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/webjars/**",
            "/error"
    };

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(WHITE_LIST);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        AppProperties.Cors cors = props.getCors();
        registry.addMapping("/api/**")
                // 显式来源白名单，不用通配符：带 Cookie 的跨域请求浏览器不接受 "*"
                .allowedOrigins(cors.getAllowedOrigins().toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-Request-Id")
                .allowCredentials(cors.isAllowCredentials())
                .maxAge(cors.getMaxAge());
    }
}
