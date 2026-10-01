package com.retail.erp.module.system.dto;

import lombok.Data;

/**
 * 登录成功返回
 *
 * accessToken 只放在响应体里，由前端保存在 Pinia 内存中；
 * refreshToken 对 Web 端不走响应体，而是通过 Set-Cookie 下发 httpOnly Cookie，
 * 前端 JS 读不到，XSS 也无法窃取。
 *
 * 小程序端（client=MINI）例外：它没有 httpOnly Cookie 这套机制，
 * 刷新令牌会放在 refreshToken 字段里返回，由小程序存进本地缓存自行保管。
 */
@Data
public class LoginVO {

    /** 访问令牌，前端放在 Authorization 头里 */
    private String accessToken;

    /** 访问令牌有效期（秒），前端据此提前静默续期 */
    private long expiresIn;

    private UserVO user;

    /** 刷新令牌，仅小程序端（client=MINI）返回；Web 端为 null */
    private String refreshToken;

    /** 刷新令牌有效期（秒），仅小程序端返回 */
    private Long refreshExpiresIn;
}
