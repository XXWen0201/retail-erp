package com.retail.erp.module.system.service;

import com.retail.erp.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 登录失败次数限制
 *
 * 解决的问题：没有这层保护时，/api/auth/login 可以被无限次尝试，
 * 演示账号的密码是 123456，字典跑几分钟就能撞开。
 *
 * ── 三个设计决定 ──
 *
 * 1) 计数键用「用户名」而不是「IP」
 *    IP 会被 NAT、代理、手机网络共享，同一个人换个网络就能绕过；
 *    而账号是攻击者真正想拿下的目标，锁账号才锁得住。
 *    代价是存在「恶意锁定别人账号」的可能，所以锁定时长故意做得短（10 分钟），
 *    并且到期自动解锁，不依赖管理员手工恢复。
 *
 * 2) 计数放在 Redis 而不是内存
 *    内存计数在重启后清零、在多实例部署下各算各的，两种情况都等于没有防护。
 *    Redis 的 INCR + EXPIRE 是原子的，且天然支持过期自动清理。
 *
 * 3) 账号不存在也照样计数
 *    如果只对存在的账号计数，攻击者就能靠「锁定与否」判断账号是否存在，
 *    反而把用户枚举的口子重新打开了。所以这里一律按输入的用户名计数，
 *    与 AuthService 里「账号不存在和密码错误合并成同一个提示」的思路一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    /** 失败计数键前缀 */
    private static final String FAIL_KEY_PREFIX = "erp:login:fail:";

    private final StringRedisTemplate stringRedisTemplate;
    private final AppProperties props;

    /**
     * 当前是否已被锁定
     *
     * @return 剩余锁定秒数；0 表示未锁定
     */
    public long lockedSeconds(String username) {
        String key = failKey(username);
        String value = stringRedisTemplate.opsForValue().get(key);

        // 锁定期间把计数直接写到上限，方便判断「是否处于锁定态」，
        // 不用再单独维护一个 lock key —— 少一个键就少一处不一致的可能
        if (value == null) {
            return 0L;
        }
        long count;
        try {
            count = Long.parseLong(value);
        } catch (NumberFormatException e) {
            // 值被外部改坏了，当作没失败过处理，不能让登录功能因此不可用
            log.warn("登录失败计数格式异常，已忽略 username={} value={}", username, value);
            return 0L;
        }
        if (count < props.getLogin().getMaxFailCount()) {
            return 0L;
        }
        Long ttl = stringRedisTemplate.getExpire(key);
        return ttl == null || ttl < 0 ? 0L : ttl;
    }

    /**
     * 记录一次失败
     *
     * @return 本次失败后的累计次数
     */
    public long recordFailure(String username) {
        String key = failKey(username);
        Long count = stringRedisTemplate.opsForValue().increment(key);
        long value = count == null ? 1L : count;

        // 只在第一次失败时设置过期时间。
        // 如果每次失败都刷新 TTL，攻击者只要持续尝试就永远不会过期，
        // 而正常用户偶尔输错一次，10 分钟后计数会自动清零。
        if (value == 1L) {
            stringRedisTemplate.expire(key, Duration.ofMinutes(props.getLogin().getFailWindowMinutes()));
        }

        int max = props.getLogin().getMaxFailCount();
        if (value == max) {
            // 刚好达到上限时把 TTL 改成锁定时长：
            // 统计窗口（比如 10 分钟）和锁定时长可能不同，锁定必须按后者算
            stringRedisTemplate.expire(key, Duration.ofMinutes(props.getLogin().getLockMinutes()));
            log.warn("账号连续登录失败 {} 次，已锁定 {} 分钟 username={}",
                    value, props.getLogin().getLockMinutes(), username);
        }
        return value;
    }

    /**
     * 登录成功后清除计数
     *
     * 必须清：否则用户之前输错过几次，之后正常登录也会莫名其妙被锁。
     */
    public void clear(String username) {
        stringRedisTemplate.delete(failKey(username));
    }

    /**
     * 剩余可尝试次数，用于在提示里告诉用户「还能试几次」
     *
     * 不把准确次数告诉用户是常见做法（避免攻击者精确判断），
     * 但这里选择告诉 —— 门店场景是内部系统，用户就是自己的员工，
     * 提示清楚能减少「我明明没输错几次」的困惑。
     */
    public int remainingAttempts(String username) {
        String value = stringRedisTemplate.opsForValue().get(failKey(username));
        if (value == null) {
            return props.getLogin().getMaxFailCount();
        }
        try {
            long used = Long.parseLong(value);
            return (int) Math.max(props.getLogin().getMaxFailCount() - used, 0);
        } catch (NumberFormatException e) {
            return props.getLogin().getMaxFailCount();
        }
    }

    /** 键名统一在这里拼，避免各处写错前缀导致计数对不上 */
    private String failKey(String username) {
        return FAIL_KEY_PREFIX + (username == null ? "" : username.trim().toLowerCase());
    }
}
