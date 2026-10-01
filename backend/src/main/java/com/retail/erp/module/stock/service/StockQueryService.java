package com.retail.erp.module.stock.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.retail.erp.common.BizException;
import com.retail.erp.common.PageResult;
import com.retail.erp.common.enums.BizTypeEnum;
import com.retail.erp.common.enums.StockDeductMode;
import com.retail.erp.config.AppProperties;
import com.retail.erp.module.basic.entity.Product;
import com.retail.erp.module.basic.mapper.ProductMapper;
import com.retail.erp.module.stock.dto.BenchmarkRequest;
import com.retail.erp.module.stock.dto.BenchmarkResult;
import com.retail.erp.module.stock.dto.StockBatchQuery;
import com.retail.erp.module.stock.dto.StockBatchVO;
import com.retail.erp.module.stock.dto.StockOverviewVO;
import com.retail.erp.module.stock.dto.StockRecordQuery;
import com.retail.erp.module.stock.dto.StockRecordVO;
import com.retail.erp.module.stock.mapper.StockPageMapper;
import com.retail.erp.security.LoginUser;
import com.retail.erp.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 库存查询与压测服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockQueryService {

    private final StockPageMapper stockPageMapper;
    private final ProductMapper productMapper;
    private final StockCoreService stockCoreService;
    private final AppProperties props;

    // ------------------------------------------------------------------

    public PageResult<StockBatchVO> batchPage(StockBatchQuery query) {
        int nearExpiryDays = props.getAlert().getNearExpiryDays();
        IPage<StockBatchVO> page = stockPageMapper.selectBatchPage(
                new Page<>(query.getPage(), query.getSize()),
                query.getProductId(), query.getExpiringSoon(), query.getExpired(), nearExpiryDays);

        LocalDate today = LocalDate.now();
        // 剩余天数与临期状态是「以今天为基准」算出来的，不落库，每次查询实时算
        page.getRecords().forEach(vo -> vo.computeExpire(today, nearExpiryDays));
        return PageResult.of(page);
    }

    public PageResult<StockRecordVO> recordPage(StockRecordQuery query) {
        IPage<StockRecordVO> page = stockPageMapper.selectRecordPage(
                new Page<>(query.getPage(), query.getSize()),
                query.getProductId(), query.getBizType(), query.getBizNo(),
                query.getStartDate(), query.getEndDate());
        page.getRecords().forEach(vo -> vo.setBizTypeLabel(BizTypeEnum.labelOf(vo.getBizType())));
        return PageResult.of(page);
    }

    public StockOverviewVO overview() {
        StockOverviewVO vo = stockPageMapper.selectStockOverview();
        if (vo == null) {
            vo = new StockOverviewVO();
        }
        StockOverviewVO batch = stockPageMapper.selectBatchOverview(props.getAlert().getNearExpiryDays());
        if (batch != null) {
            vo.setNearExpiryBatchCount(batch.getNearExpiryBatchCount());
            vo.setExpiredBatchCount(batch.getExpiredBatchCount());
        }
        return vo;
    }

    // ==================================================================
    //  并发扣减压测
    // ==================================================================

    /**
     * 并发扣减压测
     *
     * 用同一批数据分别跑两种扣减方案，比较 QPS 与最终库存是否自洽，
     * 这是「库存扣减一致性」这个课题的核心验证手段。
     *
     * ⚠️ 副作用提醒：压测会真实扣减库存、真实写入流水（bizNo 统一为 BENCHMARK，
     *    便于事后筛选清理）。所以请挑一个可以随便扣的商品来跑，
     *    或者先跑 DB 方案、再跑 REDIS 方案，最后用盘点把库存调回去。
     */
    public BenchmarkResult benchmark(BenchmarkRequest req) {
        Product product = productMapper.selectById(req.getProductId());
        if (product == null) {
            throw BizException.notFound("商品");
        }

        StockDeductMode mode = resolveMode(req.getMode());
        int threads = req.getThreads();
        int timesPerThread = req.getTimesPerThread();
        int quantityPerRequest = req.getQuantityPerRequest();
        int totalRequests = threads * timesPerThread;

        int beforeStock = currentStock(req.getProductId());
        if (beforeStock < quantityPerRequest) {
            throw BizException.badRequest(
                    "商品【" + product.getName() + "】当前库存 " + beforeStock
                            + "，不足以进行压测，请先入库或换一个商品");
        }

        // ThreadLocal 不会自动传到工作线程，先把当前操作人取出来，
        // 在每个任务里手动塞进去，这样压测产生的流水也能查到是谁跑的
        LoginUser operator = UserContext.get();

        ExecutorService pool = Executors.newFixedThreadPool(threads, runnable -> {
            Thread t = new Thread(runnable);
            t.setName("stock-benchmark-" + t.getId());
            t.setDaemon(true);
            return t;
        });
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch fire = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();
        AtomicLong costMs = new AtomicLong();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    // 所有线程先就位，再同时开闸，才能形成真正的瞬时并发，
                    // 否则先提交的线程早就跑完了，后面线程进来时根本没有竞争
                    fire.await();
                    for (int j = 0; j < timesPerThread; j++) {
                        if (operator != null) {
                            UserContext.set(operator);
                        }
                        try {
                            stockCoreService.outboundWithMode(
                                    new StockCoreService.OutboundCmd(
                                            req.getProductId(), product.getName(), quantityPerRequest,
                                            BizTypeEnum.SALE_OUT, "BENCHMARK", null, "并发扣减压测"),
                                    mode);
                            success.incrementAndGet();
                        } catch (Exception e) {
                            // 库存不足、并发冲突都是被正确拒绝的请求，计入 fail 而不是抛出去
                            fail.incrementAndGet();
                        } finally {
                            UserContext.clear();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finish.countDown();
                }
            });
        }

        try {
            ready.await();
            long startAt = System.nanoTime();
            fire.countDown();
            finish.await(5, TimeUnit.MINUTES);
            costMs.set((System.nanoTime() - startAt) / 1_000_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pool.shutdownNow();
        }

        int finalStock = currentStock(req.getProductId());

        BenchmarkResult result = new BenchmarkResult();
        result.setMode(mode.name());
        result.setThreads(threads);
        result.setTimesPerThread(timesPerThread);
        result.setTotalRequests(totalRequests);
        result.setSuccessCount(success.get());
        result.setFailCount(fail.get());
        result.setCostMs(costMs.get());
        result.setQps(costMs.get() > 0 ? totalRequests * 1000.0 / costMs.get() : 0);
        result.setBeforeStock(beforeStock);
        result.setFinalStock(finalStock);
        result.computeConsistency();
        result.setMessage(buildBenchmarkMessage(result, mode));

        log.info("扣减压测完成 mode={} threads={} total={} success={} fail={} cost={}ms consistent={}",
                mode, threads, totalRequests, success.get(), fail.get(), costMs.get(), result.isConsistent());
        return result;
    }

    private String buildBenchmarkMessage(BenchmarkResult r, StockDeductMode mode) {
        if (!r.isConsistent()) {
            return "❌ 数据不一致：压测前 " + r.getBeforeStock() + " - 成功 " + r.getSuccessCount()
                    + " = " + (r.getBeforeStock() - r.getSuccessCount())
                    + "，但实际库存是 " + r.getFinalStock() + "，存在超卖或漏扣，请立即排查";
        }
        return "✅ 数据一致（" + mode.getLabel() + "）：压测前 " + r.getBeforeStock()
                + " - 成功 " + r.getSuccessCount() + " = " + r.getFinalStock()
                + "，无超卖。耗时 " + r.getCostMs() + " ms，QPS "
                + r.formattedQps().toPlainString() + "，被正确拒绝 " + r.getFailCount() + " 次";
    }

    private StockDeductMode resolveMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return props.getStock().getDeductMode();
        }
        try {
            return StockDeductMode.valueOf(mode.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw BizException.badRequest("不支持的扣减方案：" + mode + "，只支持 DB 或 REDIS");
        }
    }

    /** 直接读数据库，绕过缓存 —— 压测结论必须基于真实落库的数据 */
    private int currentStock(Long productId) {
        Integer qty = stockPageMapper.selectQuantityByProductId(productId);
        return qty == null ? 0 : qty;
    }
}
