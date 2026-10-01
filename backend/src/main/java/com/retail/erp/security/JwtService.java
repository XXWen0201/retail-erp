package com.retail.erp.security;

import com.retail.erp.common.BizException;
import com.retail.erp.common.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 服务
 *
 * 令牌设计（访问令牌短时效 + 刷新令牌服务端存储）：
 *   访问令牌 30 分钟，只存 userId/username/role，不落库，纯自校验，无状态高性能
 *   刷新令牌 7 天，多带一个 jti，并在 refresh_token 表登记，可单独吊销
 *
 * 为什么访问令牌设这么短：一旦泄露，攻击窗口最多 30 分钟。
 * 如果设成 7 天，等于把长期凭据放在客户端，与直接把密码给出去差别不大。
 */
@Slf4j
@Service
public class JwtService {

    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_REAL_NAME = "realName";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey key;
    private final long accessTokenMillis;
    private final long refreshTokenMillis;

    public JwtService(com.retail.erp.config.AppProperties props) {
        byte[] secretBytes = props.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret 长度不足：HMAC-SHA256 要求至少 32 字节，当前 " + secretBytes.length
                            + " 字节。请在配置中换用更长的随机字符串。");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.accessTokenMillis = props.getJwt().getAccessTokenMinutes() * 60_000L;
        this.refreshTokenMillis = props.getJwt().getRefreshTokenDays() * 86_400_000L;
    }

    /**
     * 签发访问令牌
     *
     * @param jti 令牌唯一标识，同时写入刷新令牌表，便于吊销单次会话
     */
    public String createAccessToken(LoginUser user, String jti) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(user.getUserId()))
                .id(jti)
                .claim(CLAIM_USERNAME, user.getUsername())
                .claim(CLAIM_REAL_NAME, user.getRealName())
                .claim(CLAIM_ROLE, user.getRole())
                .issuedAt(new Date(now))
                .expiration(new Date(now + accessTokenMillis))
                .signWith(key)
                .compact();
    }

    /** 签发刷新令牌：只放 userId + jti，信息越少越安全 */
    public String createRefreshToken(Long userId, String jti) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .id(jti)
                .issuedAt(new Date(now))
                .expiration(new Date(now + refreshTokenMillis))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验令牌
     *
     * 统一把过期与签名错误转成 401，且刻意不区分提示 —— 避免给攻击者提供探测线索。
     */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "登录已过期，请重新登录");
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT 校验失败: {}", e.getMessage());
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    /** 从访问令牌中还原登录用户 */
    public LoginUser toLoginUser(Claims claims) {
        LoginUser user = new LoginUser();
        user.setUserId(Long.valueOf(claims.getSubject()));
        user.setUsername(claims.get(CLAIM_USERNAME, String.class));
        user.setRealName(claims.get(CLAIM_REAL_NAME, String.class));
        user.setRole(claims.get(CLAIM_ROLE, String.class));
        return user;
    }

    public long getAccessTokenMillis() {
        return accessTokenMillis;
    }

    public long getRefreshTokenMillis() {
        return refreshTokenMillis;
    }
}
