package com.retail.erp.module.stock.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.retail.erp.config.AppProperties;
import com.retail.erp.module.stock.entity.Stock;
import com.retail.erp.module.stock.mapper.StockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 库存缓存服务（Redis + Lua）
 *
 * 本类只负责「缓存这一层」，不碰数据库业务逻辑，
 * 保证 StockCoreService 里读起来是纯粹的业务流程。
 *
 * 缓存键：erp:stock:{productId}，值就是当前可用库存的裸数字字符串。
 * 刻意不缓存整个实体对象：Lua 脚本里要用 tonumber() 直接算，
 * 存 JSON 的话还得先解开，既慢又容易出错。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockCacheService {

    private static final String KEY_PREFIX = "erp:stock:";

    /** Lua 返回：缓存里没有该商品的库存，需要回源加载 */
    public static final long NOT_CACHED = -1L;

    /** Lua 返回：库存不足，扣减被拒绝 */
    public static final long NOT_ENOUGH = -2L;

    private final StringRedisTemplate stringRedisTemplate;
    private final StockMapper stockMapper;
    private final DefaultRedisScript<Long> stockDeductScript;
    private final DefaultRedisScript<Long> stockRollbackScript;
    private final AppProperties props;

    public String key(Long productId) {
        return KEY_PREFIX + productId;
    }

    /**
     * 从数据库回源并写入缓存
     *
     * @return 回源后的库存数量
     */
    public long reload(Long productId) {
        Stock stock = stockMapper.selectOne(Wrappers.<Stock>lambdaQuery()
                .eq(Stock::getProductId, productId));
        long qty = (stock == null || stock.getQuantity() == null) ? 0L : stock.getQuantity();
        stringRedisTemplate.opsForValue().set(key(productId), String.valueOf(qty),
                Duration.ofSeconds(props.getStock().getCacheTtlSeconds()));
        return qty;
    }

    /** 缓存未命中则回源，返回当前缓存中的库存 */
    public long loadIfAbsent(Long productId) {
        String cached = stringRedisTemplate.opsForValue().get(key(productId));
        if (cached != null) {
            try {
                return Long.parseLong(cached);
            } catch (NumberFormatException e) {
                // 缓存被人工写脏了，直接回源覆盖，不要让它继续毒害后续判断
                log.warn("库存缓存值非法，已回源重建 key={} value={}", key(productId), cached);
            }
        }
        return reload(productId);
    }

    /**
     * Lua 原子预扣减
     *
     * 原子性是关键：如果拆成「GET 判断 → DECRBY 扣减」两条命令，
     * 两个并发请求可能同时读到 10、同时认为够扣 8、最后扣成 -6，直接超卖。
     * Redis 执行 Lua 是单线程原子的，中间不会被插入其他命令。
     *
     * @return >=0 扣减后的剩余库存；{@link #NOT_CACHED} 未缓存；{@link #NOT_ENOUGH} 不足
     */
    public long tryDeduct(Long productId, int quantity) {
        Long result = stringRedisTemplate.execute(stockDeductScript,
                List.of(key(productId)), String.valueOf(quantity));
        return result == null ? NOT_CACHED : result;
    }

    /** 补偿：把预扣减的数量加回去，用于数据库事务回滚后的缓存修复 */
    public void rollback(Long productId, int quantity) {
        stringRedisTemplate.execute(stockRollbackScript,
                List.of(key(productId)), String.valueOf(quantity));
    }

    /** 用数据库的真实值覆盖缓存（写后更新，保证缓存与库最终一致） */
    public void sync(Long productId, int quantity) {
        stringRedisTemplate.opsForValue().set(key(productId), String.valueOf(quantity),
                Duration.ofSeconds(props.getStock().getCacheTtlSeconds()));
    }

    /** 删除缓存，下次读取时自动回源（用于不确定新值的场景） */
    public void evict(Long productId) {
        stringRedisTemplate.delete(key(productId));
    }
}
