package com.retail.erp.module.stock;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.retail.erp.common.enums.BizTypeEnum;
import com.retail.erp.common.enums.StockDeductMode;
import com.retail.erp.module.stock.dto.BatchDeductResult;
import com.retail.erp.module.stock.entity.Stock;
import com.retail.erp.module.stock.mapper.StockMapper;
import com.retail.erp.module.stock.service.StockCoreService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 库存扣减一致性测试
 *
 * 这个测试回答答辩时最容易被追问的那个问题：
 * 「你说并发下不会超卖，凭什么？」
 *
 * 判定标准只有一条，和压测接口完全一致：
 *     初始库存 - 成功扣减次数 × 每次数量 == 最终库存
 * 只要等式成立，就说明既没有超卖（扣多了），也没有漏扣（扣少了）。
 *
 * 为什么必须真连数据库，不能用 Mock：
 *   乐观锁的行为完全依赖 MySQL 的 UPDATE ... WHERE version = ? 在并发下
 *   真实的影响行数。Mock 掉的 Mapper 永远返回 1，这个测试就变成了自欺欺人 ——
 *   它能通过，但什么都没证明。
 *
 * 测试会在结束后把库存与流水恢复原状，可以反复执行。
 */
@SpringBootTest
@DisplayName("库存扣减一致性")
class StockConcurrencyTest {

    @Autowired
    private StockCoreService stockCoreService;

    @Autowired
    private StockMapper stockMapper;

    /** 用一个固定商品做测试，结束后恢复库存，避免污染演示数据 */
    private static final Long PRODUCT_ID = 12L;

    private int originQuantity;

    @BeforeEach
    void setUp() {
        originQuantity = quantityOf(PRODUCT_ID);
        // 并发扣减需要足够的库存来制造竞争，先把库存垫到 1000。
        // 同样必须走批次补货，不能直接改 stock.quantity —— 否则批次合计为 0，
        // 出库时一个批次都挑不到，测试会全部失败
        int diff = 1000 - originQuantity;
        if (diff > 0) {
            restoreAsBatch(diff);
        } else if (diff < 0) {
            stockCoreService.outbound(new StockCoreService.OutboundCmd(
                    PRODUCT_ID, "并发测试商品", -diff, BizTypeEnum.SALE_OUT,
                    "JUNIT", null, "并发测试前清理多余库存"));
        }
    }

    @AfterEach
    void tearDown() {
        // 恢复原始库存，保证这个测试可以反复跑、不影响其它用例。
        // 关键：全程只通过「批次」调整数量，绝不直接改 stock.quantity。
        // 直接改总库存会让 stock.quantity != SUM(stock_batch.stock_quantity)，
        // 之后所有出库都会挑不到批次而失败 —— 这是本测试早期版本踩过的坑。
        int current = quantityOf(PRODUCT_ID);
        int diff = originQuantity - current;
        if (diff > 0) {
            restoreAsBatch(diff);
        } else if (diff < 0) {
            // 多出来的部分按 FEFO 出掉，同样走正规扣减路径
            stockCoreService.outbound(new StockCoreService.OutboundCmd(
                    PRODUCT_ID, "并发测试商品", -diff, BizTypeEnum.SALE_OUT,
                    "JUNIT", null, "并发测试结束后清理多余库存"));
        }
    }

    /** 通过新建一个测试批次把库存补回去，保持总库存与批次合计一致 */
    private void restoreAsBatch(int quantity) {
        stockCoreService.inbound(new StockCoreService.InboundCmd(
                PRODUCT_ID, "并发测试商品", quantity, BigDecimal.ZERO,
                "JUNIT-RESTORE-" + System.nanoTime(), null, null,
                BizTypeEnum.CHECK_GAIN, "JUNIT", null, "并发测试结束后恢复库存"));
    }

    @Test
    @DisplayName("DB 乐观锁：50 线程并发扣减，库存不超卖")
    void dbOptimisticLockShouldNotOversell() throws Exception {
        int threads = 50;
        int perThread = 10;
        int quantityPerRequest = 1;
        int before = quantityOf(PRODUCT_ID);

        RunResult result = runConcurrent(threads, perThread, quantityPerRequest, StockDeductMode.DB);

        int after = quantityOf(PRODUCT_ID);
        int expectedDeducted = result.success() * quantityPerRequest;
        int actualDeducted = before - after;

        assertEquals(expectedDeducted, actualDeducted,
                "DB 乐观锁下扣减量必须与成功次数一致：成功 "
                        + result.success() + " 次 × " + quantityPerRequest
                        + " 件，实际却扣了 " + actualDeducted + " 件");

        assertTrue(after >= 0, "库存不允许被扣成负数，实际为 " + after);

        // 库存 1000、每次扣 1、共 500 次请求，理论上应当全部成功
        assertEquals(threads * perThread, result.success() + result.fail(),
                "请求总数应当等于成功数加失败数");
    }

    @Test
    @DisplayName("DB 乐观锁：每次扣 3 件时，判定公式必须按数量累计")
    void dbShouldAccountForQuantityPerRequest() throws Exception {
        int threads = 20;
        int perThread = 10;
        int quantityPerRequest = 3;
        int before = quantityOf(PRODUCT_ID);

        RunResult result = runConcurrent(threads, perThread, quantityPerRequest, StockDeductMode.DB);

        int after = quantityOf(PRODUCT_ID);
        assertEquals(result.success() * quantityPerRequest, before - after,
                "每次扣 " + quantityPerRequest + " 件时，扣减总量必须按数量累计");

        // 这一条专门守住「只减成功次数、忘记乘每次数量」这个坑
        assertTrue(after < before - result.success(),
                "库存下降幅度应当明显大于成功次数（因为每次扣多件），"
                        + "若此处失败说明判定公式又退回了按 1 件计算");
    }

    @Test
    @DisplayName("库存不足时并发请求被正确拒绝，不会扣成负数")
    void shouldRejectWhenStockNotEnough() throws Exception {
        // 只留 10 件库存，却发起 200 次扣减请求。
        // 这里必须用出库把多余的库存扣掉，而不是直接改 stock.quantity：
        // 直接改总数会让批次合计大于总库存，后面扣批次时账就乱了
        int surplus = quantityOf(PRODUCT_ID) - 10;
        if (surplus > 0) {
            stockCoreService.outbound(new StockCoreService.OutboundCmd(
                    PRODUCT_ID, "并发测试商品", surplus, BizTypeEnum.SALE_OUT,
                    "JUNIT", null, "准备库存不足场景"));
        }
        int before = quantityOf(PRODUCT_ID);
        assertEquals(10, before, "准备阶段应把库存精确压到 10 件");

        RunResult result = runConcurrent(20, 10, 1, StockDeductMode.DB);

        int after = quantityOf(PRODUCT_ID);
        assertEquals(before - result.success(), after, "扣减量必须与成功次数一致");
        assertTrue(after >= 0, "库存不允许为负");
        assertEquals(10, result.success(), "库存只有 10 件，最多只能成功 10 次");
        assertTrue(result.fail() > 0, "库存耗尽后的请求必须被拒绝");
    }

    // ==================================================================

    /**
     * 并发跑扣减，返回成功/失败次数
     *
     * 三个 CountDownLatch 的分工：ready 等所有线程就位，fire 是统一发令枪，
     * finish 等全部结束。少了发令枪，先启动的线程早就跑完了，
     * 后启动的线程进来时根本没有竞争，测试等于串行执行，测不出任何问题。
     */
    private RunResult runConcurrent(int threads, int perThread, int quantityPerRequest,
                                    StockDeductMode mode) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch fire = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();
        List<Throwable> unexpected = new ArrayList<>();

        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        fire.await();
                        for (int j = 0; j < perThread; j++) {
                            try {
                                List<BatchDeductResult> deducted = stockCoreService.outboundWithMode(
                                        new StockCoreService.OutboundCmd(
                                                PRODUCT_ID, "并发测试商品", quantityPerRequest,
                                                BizTypeEnum.SALE_OUT, "JUNIT", null, "并发一致性测试"),
                                        mode);
                                if (deducted != null && !deducted.isEmpty()) {
                                    success.incrementAndGet();
                                } else {
                                    fail.incrementAndGet();
                                }
                            } catch (Exception e) {
                                // 库存不足、乐观锁重试耗尽都是「被正确拒绝」，不是测试失败
                                fail.incrementAndGet();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Throwable t) {
                        synchronized (unexpected) {
                            unexpected.add(t);
                        }
                    } finally {
                        finish.countDown();
                    }
                });
            }

            assertTrue(ready.await(30, TimeUnit.SECONDS), "线程未能在 30 秒内全部就位");
            fire.countDown();
            assertTrue(finish.await(120, TimeUnit.SECONDS), "并发扣减未能在 120 秒内结束");
        } finally {
            pool.shutdownNow();
        }

        if (!unexpected.isEmpty()) {
            throw new AssertionError("并发执行中出现非预期异常", unexpected.get(0));
        }
        return new RunResult(success.get(), fail.get());
    }

    private int quantityOf(Long productId) {
        Stock stock = stockMapper.selectOne(Wrappers.<Stock>lambdaQuery()
                .eq(Stock::getProductId, productId));
        return stock == null || stock.getQuantity() == null ? 0 : stock.getQuantity();
    }

    private record RunResult(int success, int fail) {
    }
}
