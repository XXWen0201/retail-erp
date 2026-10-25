package com.retail.erp.module.system;

import com.retail.erp.module.system.service.LoginAttemptService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 登录失败次数限制测试
 *
 * 这个功能的特点是「只在不正常的时候起作用」—— 平时它什么都不做，
 * 所以很难靠手工点页面发现问题：真要验证，得连续输错 5 次密码把演示账号锁掉，
 * 之后还得等 10 分钟。用测试跑一遍几秒钟，而且不会真的锁住账号。
 *
 * 测试用一个专门的不存在账号名，跑完清理 Redis 键，不影响 admin/manager/staff。
 */
@SpringBootTest
@DisplayName("登录失败次数限制")
class LoginAttemptServiceTest {

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** 用独立的测试账号名，避免污染真实账号的计数 */
    private static final String TEST_USER = "__junit_login_probe__";

    private static final int MAX_FAIL = 5;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        stringRedisTemplate.delete("erp:login:fail:" + TEST_USER);
    }

    @Test
    @DisplayName("失败次数未达上限时不锁定")
    void shouldNotLockBeforeMaxFail() {
        for (int i = 1; i < MAX_FAIL; i++) {
            loginAttemptService.recordFailure(TEST_USER);
        }
        assertEquals(0L, loginAttemptService.lockedSeconds(TEST_USER),
                "失败 " + (MAX_FAIL - 1) + " 次时不应该锁定");
        assertEquals(1, loginAttemptService.remainingAttempts(TEST_USER),
                "失败 " + (MAX_FAIL - 1) + " 次后应该只剩 1 次机会");
    }

    @Test
    @DisplayName("达到上限后锁定，且剩余尝试次数为 0")
    void shouldLockAtMaxFail() {
        for (int i = 0; i < MAX_FAIL; i++) {
            loginAttemptService.recordFailure(TEST_USER);
        }
        long locked = loginAttemptService.lockedSeconds(TEST_USER);
        assertTrue(locked > 0, "达到上限后应该被锁定");
        // 锁定时长 10 分钟，剩余秒数应当落在合理区间
        assertTrue(locked <= Duration.ofMinutes(10).toSeconds(),
                "剩余锁定时间不应超过配置的 10 分钟，实际 " + locked);
        assertEquals(0, loginAttemptService.remainingAttempts(TEST_USER),
                "锁定时剩余尝试次数应为 0");
    }

    @Test
    @DisplayName("登录成功后清除计数，不会累积到锁定")
    void shouldClearOnSuccess() {
        for (int i = 0; i < MAX_FAIL - 1; i++) {
            loginAttemptService.recordFailure(TEST_USER);
        }
        loginAttemptService.clear(TEST_USER);

        assertEquals(0L, loginAttemptService.lockedSeconds(TEST_USER));
        assertEquals(MAX_FAIL, loginAttemptService.remainingAttempts(TEST_USER),
                "清除后应恢复满额尝试次数");
    }

    @Test
    @DisplayName("用户名大小写与空格不影响计数")
    void shouldNormalizeUsername() {
        loginAttemptService.recordFailure("__JUNIT_LOGIN_PROBE__");
        loginAttemptService.recordFailure("  __junit_login_probe__  ");

        assertEquals(2, MAX_FAIL - loginAttemptService.remainingAttempts(TEST_USER),
                "大小写与首尾空格应归一化为同一个计数键");
    }

    @Test
    @DisplayName("失败计数带有过期时间，不会永久占用 Redis")
    void shouldHaveExpiry() {
        loginAttemptService.recordFailure(TEST_USER);
        Long ttl = stringRedisTemplate.getExpire("erp:login:fail:" + TEST_USER);
        assertTrue(ttl != null && ttl > 0,
                "计数键必须带 TTL，否则攻击者试过一次的账号名会永久留在 Redis 里");
    }
}
