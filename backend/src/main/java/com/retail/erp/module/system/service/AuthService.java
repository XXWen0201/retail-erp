package com.retail.erp.module.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.retail.erp.common.BizException;
import com.retail.erp.common.ErrorCode;
import com.retail.erp.config.AppProperties;
import com.retail.erp.module.system.dto.LoginDTO;
import com.retail.erp.module.system.dto.LoginVO;
import com.retail.erp.module.system.dto.UserVO;
import com.retail.erp.module.system.entity.RefreshToken;
import com.retail.erp.module.system.entity.SysUser;
import com.retail.erp.module.system.mapper.RefreshTokenMapper;
import com.retail.erp.module.system.mapper.SysUserMapper;
import com.retail.erp.security.JwtService;
import com.retail.erp.security.LoginUser;
import com.retail.erp.security.PasswordService;
import com.retail.erp.security.UserContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/**
 * 认证服务
 *
 * 令牌方案：短时效访问令牌（30 分钟，无状态自校验）
 *         + 服务端刷新令牌（7 天，落库可吊销）
 *
 * 刷新令牌采用「轮转」策略：每次刷新都吊销旧的、签发新的。
 * 好处是即使刷新令牌泄露，攻击者用过一次后原用户会立刻掉线，
 * 而合法用户刷新时会发现令牌已被吊销，从而察觉异常。
 *
 * 同一套认证体系服务两种客户端，区别只在刷新令牌的「载体」：
 *   Web（PC 管理端）：刷新令牌走 httpOnly Cookie，JS 读不到，天然防 XSS 窃取。
 *   小程序端        ：没有浏览器的 Cookie 语义，刷新令牌改走请求体，
 *                    由客户端存进本地缓存自行保管。
 * 校验、轮转、吊销这些真正的业务逻辑两端完全共用，不存在两套实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** 刷新令牌 Cookie 名，与 PC 端 axios 约定一致 */
    public static final String REFRESH_COOKIE = "refreshToken";

    /**
     * Cookie 路径限定在 /api/auth 下。
     * 刷新令牌只在登录、刷新、退出三个接口用得到，没必要让它在每次业务请求里
     * 都跟着发出去 —— 缩小暴露面，也少几个字节的请求头。
     */
    private static final String COOKIE_PATH = "/api/auth";

    private final SysUserMapper userMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final PasswordService passwordService;
    private final JwtService jwtService;
    private final AppProperties props;

    // ------------------------------------------------------------------
    // 登录
    // ------------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    public LoginVO login(LoginDTO dto, HttpServletResponse response) {
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, dto.username()));

        // 故意把「账号不存在」与「密码错误」合并成同一个提示，
        // 否则攻击者可以靠提示差异枚举出系统里有哪些账号
        if (user == null || !passwordService.matches(dto.password(), user.getPassword())) {
            throw new BizException(ErrorCode.LOGIN_FAILED);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }

        boolean mini = dto.miniClient();

        LoginUser loginUser = toLoginUser(user);
        String jti = newJti();
        String accessToken = jwtService.createAccessToken(loginUser, jti);
        String refreshToken = jwtService.createRefreshToken(user.getId(), jti);

        saveRefreshToken(jti, user);

        if (mini) {
            log.info("小程序端登录成功 username={} role={}", user.getUsername(), user.getRole());
            return buildLoginVO(accessToken, user, refreshToken);
        }

        writeRefreshCookie(response, refreshToken);
        log.info("登录成功 username={} role={}", user.getUsername(), user.getRole());
        return buildLoginVO(accessToken, user, null);
    }

    // ------------------------------------------------------------------
    // 刷新
    // ------------------------------------------------------------------

    /**
     * 用刷新令牌换取新的访问令牌
     *
     * 整个方法放在事务里：「吊销旧令牌 + 插入新令牌」必须原子完成，
     * 否则中途失败会出现「旧的已废、新的没写上」的令牌真空，用户被迫重新登录。
     *
     * ⚠️ 不要把事务方法抽成同类内部方法再由这里调用 —— Spring 的 @Transactional
     *    是基于代理实现的，this.xxx() 这种自调用不会走代理，事务会静默失效。
     *
     * @param bodyToken 小程序端从请求体带上来的刷新令牌，Web 端传 null
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginVO refresh(HttpServletRequest request, HttpServletResponse response, String bodyToken) {
        boolean mini = bodyToken != null && !bodyToken.isBlank();
        String token = mini ? bodyToken : readRefreshCookie(request).orElse(null);

        if (token == null || token.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "登录已过期，请重新登录");
        }

        Claims claims = jwtService.parse(token);
        String oldJti = claims.getId();
        Long userId = Long.valueOf(claims.getSubject());

        SysUser user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            if (!mini) {
                clearRefreshCookie(response);
            }
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }

        RefreshToken record = refreshTokenMapper.selectOne(Wrappers.<RefreshToken>lambdaQuery()
                .eq(RefreshToken::getTokenId, oldJti));

        if (record == null || (record.getRevoked() != null && record.getRevoked() == 1)) {
            // 已吊销的令牌被再次使用 —— 可能是重放攻击，也可能是用户重复刷新
            log.warn("刷新令牌无效或已被吊销 tokenId={} userId={}", oldJti, user.getId());
            if (!mini) {
                clearRefreshCookie(response);
            }
            throw new BizException(ErrorCode.UNAUTHORIZED, "登录已过期，请重新登录");
        }
        if (record.getExpireTime() != null && record.getExpireTime().isBefore(LocalDateTime.now())) {
            if (!mini) {
                clearRefreshCookie(response);
            }
            throw new BizException(ErrorCode.UNAUTHORIZED, "登录已过期，请重新登录");
        }

        record.setRevoked(1);
        refreshTokenMapper.updateById(record);

        LoginUser loginUser = toLoginUser(user);
        String newJti = newJti();
        String accessToken = jwtService.createAccessToken(loginUser, newJti);
        String refreshToken = jwtService.createRefreshToken(user.getId(), newJti);

        saveRefreshToken(newJti, user);

        if (mini) {
            log.debug("小程序端刷新令牌成功 userId={}", user.getId());
            return buildLoginVO(accessToken, user, refreshToken);
        }

        writeRefreshCookie(response, refreshToken);
        return buildLoginVO(accessToken, user, null);
    }

    // ------------------------------------------------------------------
    // 退出
    // ------------------------------------------------------------------

    /**
     * 退出登录
     *
     * @param bodyToken 小程序端从请求体带上来的刷新令牌，Web 端传 null
     */
    @Transactional(rollbackFor = Exception.class)
    public void logout(HttpServletRequest request, HttpServletResponse response, String bodyToken) {
        String token = (bodyToken != null && !bodyToken.isBlank())
                ? bodyToken
                : readRefreshCookie(request).orElse(null);

        if (token != null) {
            try {
                String jti = jwtService.parse(token).getId();
                refreshTokenMapper.delete(Wrappers.<RefreshToken>lambdaQuery()
                        .eq(RefreshToken::getTokenId, jti));
            } catch (Exception e) {
                // 令牌已过期或损坏，退出动作依然算成功 —— 用户目的就是「登出」
                log.debug("退出时解析刷新令牌失败，忽略：{}", e.getMessage());
            }
        }
        clearRefreshCookie(response);
    }

    // ------------------------------------------------------------------
    // 当前用户
    // ------------------------------------------------------------------

    public UserVO me() {
        LoginUser current = UserContext.require();
        SysUser user = userMapper.selectById(current.getUserId());
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return UserVO.from(user);
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    private LoginUser toLoginUser(SysUser user) {
        return new LoginUser(user.getId(), user.getUsername(), user.getRealName(), user.getRole());
    }

    private String newJti() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private void saveRefreshToken(String jti, SysUser user) {
        RefreshToken record = new RefreshToken();
        record.setTokenId(jti);
        record.setUserId(user.getId());
        record.setUsername(user.getUsername());
        record.setExpireTime(LocalDateTime.now().plusSeconds(jwtService.getRefreshTokenMillis() / 1000));
        record.setRevoked(0);
        refreshTokenMapper.insert(record);
    }

    /**
     * 组装登录返回
     *
     * @param refreshToken 非 null 时说明是小程序端，刷新令牌随响应体一起返回
     */
    private LoginVO buildLoginVO(String accessToken, SysUser user, String refreshToken) {
        LoginVO vo = new LoginVO();
        vo.setAccessToken(accessToken);
        vo.setExpiresIn(jwtService.getAccessTokenMillis() / 1000);
        vo.setUser(UserVO.from(user));
        if (refreshToken != null) {
            vo.setRefreshToken(refreshToken);
            vo.setRefreshExpiresIn(jwtService.getRefreshTokenMillis() / 1000);
        }
        return vo;
    }

    /**
     * 写刷新令牌 Cookie
     *
     * httpOnly=true 是硬要求：这样 document.cookie 读不到，
     * 即使页面被注入 XSS 脚本也偷不走刷新令牌。
     */
    private void writeRefreshCookie(HttpServletResponse response, String token) {
        AppProperties.Jwt jwt = props.getJwt();
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, token)
                .httpOnly(true)
                .secure(jwt.isCookieSecure())
                .sameSite(jwt.getCookieSameSite())
                .path(COOKIE_PATH)
                .maxAge(Duration.ofSeconds(jwtService.getRefreshTokenMillis() / 1000))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(props.getJwt().isCookieSecure())
                .sameSite(props.getJwt().getCookieSameSite())
                .path(COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private Optional<String> readRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(c -> REFRESH_COOKIE.equals(c.getName()))
                .map(Cookie::getValue)
                .filter(v -> v != null && !v.isBlank())
                .findFirst();
    }
}
