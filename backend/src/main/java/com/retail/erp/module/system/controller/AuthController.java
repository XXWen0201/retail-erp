package com.retail.erp.module.system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retail.erp.common.R;
import com.retail.erp.module.system.dto.LoginDTO;
import com.retail.erp.module.system.dto.LoginVO;
import com.retail.erp.module.system.dto.RefreshDTO;
import com.retail.erp.module.system.dto.UserVO;
import com.retail.erp.module.system.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 认证接口
 *
 * Controller 只做三件事：收参数、调用 Service、包响应体。
 * 这里没有任何业务判断，密码比对、令牌签发、Cookie 写入全在 AuthService 里，
 * 这样换一套 HTTP 入口（比如加个 gRPC）时业务逻辑不用重写。
 *
 * 刷新与退出接口同时接受两种令牌来源：
 *   Web 端  —— 不带 body，后端从 httpOnly Cookie 读；
 *   小程序端 —— body 里带 refreshToken，后端从请求体读。
 */
@Tag(name = "认证", description = "登录 / 刷新令牌 / 退出")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final ObjectMapper objectMapper;

    @Operation(summary = "登录",
            description = "client=MINI 时刷新令牌随响应体返回（小程序端）；"
                    + "其余情况通过 Set-Cookie 下发 httpOnly 刷新令牌（Web 端）")
    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginDTO dto, HttpServletResponse response) {
        return R.ok(authService.login(dto, response));
    }

    @Operation(summary = "刷新访问令牌",
            description = "小程序端在 body 中传 refreshToken；Web 端不传，后端从 Cookie 读取")
    @PostMapping("/refresh")
    public R<LoginVO> refresh(HttpServletRequest request, HttpServletResponse response) {
        return R.ok(authService.refresh(request, response, tokenFromBody(request)));
    }

    @Operation(summary = "退出登录", description = "吊销刷新令牌并清除 Cookie")
    @PostMapping("/logout")
    public R<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response, tokenFromBody(request));
        return R.ok();
    }

    @Operation(summary = "获取当前登录用户")
    @GetMapping("/me")
    public R<UserVO> me() {
        return R.ok(authService.me());
    }

    /**
     * 从请求体里取刷新令牌（小程序端那条路）。
     *
     * 这里刻意不用 @RequestBody —— 一旦声明了它，Spring 会先按 Content-Type 挑消息转换器，
     * 碰到「没有 body」或「Content-Type 不是 JSON」的请求，在进入方法体之前就抛
     * HttpMediaTypeNotSupportedException，注解上的 required=false 也救不回来。
     * 而 Web 端的刷新请求本来就不带 body（令牌在 httpOnly Cookie 里），
     * axios 发空 body 时会自动带上 application/x-www-form-urlencoded，
     * 正好命中这个坑 —— 结果是每次刷新页面都会被踢回登录页。
     * 所以这里自己读流、自己判 JSON，读到什么算什么。
     */
    private String tokenFromBody(HttpServletRequest request) {
        try (InputStream in = request.getInputStream()) {
            byte[] bytes = in.readAllBytes();
            if (bytes.length == 0) {
                return null;
            }
            String text = new String(bytes, StandardCharsets.UTF_8).trim();
            if (!text.startsWith("{")) {
                return null;
            }
            RefreshDTO dto = objectMapper.readValue(text, RefreshDTO.class);
            return dto == null ? null : dto.refreshToken();
        } catch (Exception e) {
            // body 不是合法 JSON 就当作没带，交给 Cookie 那条路，不要在这里 500
            return null;
        }
    }
}
