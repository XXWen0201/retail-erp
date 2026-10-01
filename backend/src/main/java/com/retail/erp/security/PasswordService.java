package com.retail.erp.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 密码服务
 *
 * 为什么用 BCrypt 而不是 MD5/SHA：
 *   1. 自带随机盐，相同密码每次哈希结果都不同，彩虹表失效
 *   2. 故意设计成慢哈希（默认 10 轮，约 50~100ms），暴力破解成本极高
 *   MD5 加盐虽然也防彩虹表，但单次计算微秒级，GPU 一秒能跑几十亿次，早已不可用。
 *
 * 数据库里存的种子账号密码是 123456 的 BCrypt 密文，可直接登录演示。
 */
@Service
public class PasswordService {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public String encode(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    /**
     * 校验密码
     *
     * 注意：故意不区分「用户不存在」和「密码错误」，调用方统一返回同一提示，
     * 否则攻击者可以靠提示差异枚举出系统里有哪些账号。
     */
    public boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        try {
            return encoder.matches(rawPassword, encodedPassword);
        } catch (IllegalArgumentException e) {
            // 数据库里存了非 BCrypt 格式的脏数据
            return false;
        }
    }
}
