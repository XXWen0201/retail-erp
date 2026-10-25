package com.retail.erp.module.stock.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 并发扣减压测结果
 *
 * 这个对象是答辩时的核心证据：用同一批商品、同样的并发量，
 * 分别跑「数据库乐观锁」和「Redis+Lua」两种方案，
 * 对比 QPS 与最终库存是否与理论值一致。
 */
@Data
public class BenchmarkResult {

    /** DB / REDIS */
    private String mode;

    private int threads;
    private int timesPerThread;

    /** 总请求数 = threads * timesPerThread */
    private int totalRequests;

    /** 扣减成功次数 */
    private int successCount;

    /** 失败次数（库存不足、并发冲突等，都是被正确拒绝的请求） */
    private int failCount;

    private long costMs;

    /** 每秒处理请求数 */
    private double qps;

    /** 压测前的库存 */
    private int beforeStock;

    /** 压测后的实际库存 */
    private int finalStock;

    /** 每次扣减的数量。判定一致性时必须乘上去，不能只看成功次数 */
    private int quantityPerRequest = 1;

    /** 理论上应该被扣掉的总数量 = successCount * quantityPerRequest */
    private int expectedDeducted;

    /** 实际被扣掉的总数量 = beforeStock - finalStock */
    private int actualDeducted;

    /**
     * 数据是否自洽：finalStock == beforeStock - successCount * quantityPerRequest
     *
     * 这一条是判定「有没有超卖」的唯一标准。
     * 如果并发控制有漏洞，成功次数会大于实际扣掉的数量，
     * 库存对不上，这里的 consistent 就是 false。
     *
     * 注意这里必须乘 quantityPerRequest：每次扣 3 件、成功 10 次，
     * 账面就该少 30 件。早期版本只减了 successCount（少了 10 件），
     * 于是只要把「每次扣减数量」填成大于 1，判定就会永远报不一致 ——
     * 那是判定公式写错了，不是系统真的超卖。
     */
    private boolean consistent;

    /** 人类可读的结论 */
    private String message;

    public void computeConsistency() {
        int per = quantityPerRequest <= 0 ? 1 : quantityPerRequest;
        this.expectedDeducted = successCount * per;
        this.actualDeducted = beforeStock - finalStock;
        this.consistent = (expectedDeducted == actualDeducted);
    }

    public BigDecimal formattedQps() {
        return BigDecimal.valueOf(qps).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
