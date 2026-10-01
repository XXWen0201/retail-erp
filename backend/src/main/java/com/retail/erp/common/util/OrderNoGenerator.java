package com.retail.erp.common.util;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

/**
 * 单据号生成器
 *
 * 格式：前缀 + yyyyMMdd + 3 位流水，例如 PO20260929001、SO20260929001。
 *
 * 为什么用 Redis 自增而不是「查数据库 max(no) + 1」：
 *   并发下两个请求可能查到同一个 max，各自 +1 得到同一个单号，
 *   最后只能靠唯一索引报错兜底，用户体验是「明明点了一次却提示重复提交」。
 *   Redis 的 INCR 是单线程原子的，天然不会重号，且不用锁表。
 *
 * 为什么还要读一次数据库最大序号：
 *   系统首次上线时库里已经导入了历史单据（种子数据），
 *   Redis 计数器从 0 开始就会和历史单号撞车。所以每个日期第一次发号时，
 *   用库里已有的最大序号作为起点对齐。
 */
@Component
@RequiredArgsConstructor
public class OrderNoGenerator {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String SEQ_KEY_PREFIX = "erp:seq:";

    /** 计数器值必须是可被 Lua / INCR 解析的裸数字，所以用 StringRedisTemplate 而不是 JSON 序列化的 RedisTemplate */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 生成单号
     *
     * @param prefix         单号前缀：PO 采购 / SO 销售 / RT 退货 / PC 盘点 / B 批次
     * @param date           业务日期。传业务日期而不是 LocalDate.now()，
     *                       是为了支持补录历史单据时单号与单据日期一致
     * @param maxSuffixLookup 按「前缀+日期」查库中已有最大序号，仅在该日期首次发号时调用一次
     */
    public String next(String prefix, LocalDate date, Function<String, Long> maxSuffixLookup) {
        String day = date.format(DAY_FMT);
        String key = SEQ_KEY_PREFIX + prefix + ":" + day;

        Long seq = stringRedisTemplate.opsForValue().increment(key);
        if (seq != null && seq == 1L) {
            // 该日期首次发号：把库里已有的最大序号作为起点，避免与历史数据撞号
            stringRedisTemplate.expire(key, Duration.ofDays(3));
            Long dbMax = maxSuffixLookup.apply(prefix + day);
            if (dbMax != null && dbMax > 0) {
                seq = stringRedisTemplate.opsForValue().increment(key, dbMax);
            }
        }

        long value = (seq == null || seq <= 0) ? 1L : seq;
        return prefix + day + String.format("%03d", value);
    }
}
