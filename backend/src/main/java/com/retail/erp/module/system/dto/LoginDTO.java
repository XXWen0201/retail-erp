package com.retail.erp.module.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录入参
 *
 * 用 record：登录请求只有两个字段，且不需要被修改，
 * 写成可变类反而给了「顺手 setPassword」的机会。
 */
public record LoginDTO(

        @NotBlank(message = "请输入账号")
        @Size(max = 50, message = "账号长度不能超过 50")
        String username,

        @NotBlank(message = "请输入密码")
        @Size(max = 64, message = "密码长度不能超过 64")
        String password,

        /**
         * 客户端类型，可选。
         *
         * 不传或传 WEB：刷新令牌通过 httpOnly Cookie 下发（浏览器端的做法，XSS 读不到）。
         * 传 MINI：刷新令牌改为在响应体里返回，交给小程序自行存储 ——
         * 小程序没有浏览器那套 Cookie 语义，硬塞 Cookie 反而容易出现
         * 「真机正常、工具异常」这类难查的问题。
         */
        String client
) {

    /** 是否小程序端登录 */
    public boolean miniClient() {
        return client != null && "MINI".equalsIgnoreCase(client.trim());
    }
}
