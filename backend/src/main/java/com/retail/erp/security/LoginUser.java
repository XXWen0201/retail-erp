package com.retail.erp.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前登录用户（存放在访问令牌里的最小信息集）
 *
 * 注意这里不含密码、手机号等敏感字段：JWT 的 payload 只是 Base64 编码，任何人都能解开看。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser {

    private Long userId;
    private String username;
    private String realName;
    /** ADMIN / MANAGER / STAFF */
    private String role;

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
