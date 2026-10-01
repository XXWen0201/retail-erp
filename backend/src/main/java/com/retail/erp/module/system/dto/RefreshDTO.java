package com.retail.erp.module.system.dto;

/**
 * 刷新 / 退出的入参
 *
 * 只有小程序端会用到：它把刷新令牌存在自己的本地缓存里，
 * 请求时放在 body 中传上来。Web 端不传这个字段，后端会回退去读 httpOnly Cookie。
 */
public record RefreshDTO(String refreshToken) {
}
