package com.retail.erp.module.stock.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.retail.erp.common.BizException;
import com.retail.erp.common.ErrorCode;
import com.retail.erp.common.enums.BizTypeEnum;
import com.retail.erp.common.enums.StockDeductMode;
import com.retail.erp.config.AppProperties;
import com.retail.erp.module.stock.dto.BatchDeductResult;
import com.retail.erp.module.stock.entity.Stock;
import com.retail.erp.module.stock.entity.StockBatch;
import com.retail.erp.module.stock.entity.StockRecord;
import com.retail.erp.module.stock.mapper.StockBatchMapper;
import com.retail.erp.module.stock.mapper.StockMapper;
import com.retail.erp.module.stock.mapper.StockRecordMapper;
import com.retail.erp.security.LoginUser;
import com.retail.erp.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 库存核心引擎
 *
 * 全系统唯一可以改动 stock / stock_batch / stock_record 三张表的地方。
 * 采购入库、销售出库、退货、盘点调整全部经过这里，好处是：
 *   1. 库存只有一条写入口，不会出现「某个模块漏写流水」导致盘点追不回差异
 *   2. 一致性策略（乐观锁、FEFO、缓存同步）只在这里实现一次
 *
 * ── 事务隔离级别为什么显式指定 READ_COMMITTED ──
 * MySQL 默认 REPEATABLE READ 下，同一事务内的普通 SELECT 走的是「快照读」，
 * 第一次读到 version=3 之后，后面再读还是 3 —— 于是乐观锁冲突重试时会永远
 * 读到同一个旧版本，重试多少轮都没用，最后只能报「并发修改」失败退出。
 * 改成 READ_COMMITTED 后每次读都取最新已提交数据，重试才真正有效。
 * 这不是理论问题，是把 maxRetry 设成 3 却仍然大量失败时排查出来的。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockCoreService {

    private final StockMapper stockMapper;
    private final StockBatchMapper stockBatchMapper;
    private final StockRecordMapper stockRecordMapper;
    private final StockCacheService cacheService;
    private final AppProperties props;

    // ==================================================================
    //  入库
    // ==================================================================

    /**
     * 入库：生成批次 + 增加总库存 + 重算移动加权平均成本 + 写流水
     *
     * @return 新建的批次
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public StockBatch inbound(InboundCmd cmd) {
        Stock stock = requireStock(cmd.productId());

        BigDecimal newAvgCost = movingAverage(
                stock.getQuantity(), stock.getAvgCost(), cmd.quantity(), cmd.costPrice());

        // 1) 生成批次。同一商品分批入库，每批独立记录生产日期与到期日期，
        //    这样才可能做到「哪个批次先到期就先卖哪个」
        StockBatch batch = new StockBatch();
        batch.setProductId(cmd.productId());
        batch.setBatchNo(cmd.batchNo());
        batch.setPurchaseOrderId(cmd.bizId());
        batch.setProductionDate(cmd.productionDate());
        batch.setExpireDate(cmd.expireDate());
        batch.setInitQuantity(cmd.quantity());
        batch.setOutQuantity(0);
        batch.setStockQuantity(cmd.quantity());
        batch.setCostPrice(cmd.costPrice() == null ? BigDecimal.ZERO : cmd.costPrice());
        batch.setStatus(1);
        stockBatchMapper.insert(batch);

        // 2) 增加总库存（带乐观锁）
        StockChange change = applyChange(stock.getProductId(), cmd.quantity(), newAvgCost);

        // 3) 写流水，记录变动前后值
        writeRecord(RecordCmd.builder()
                .productId(cmd.productId())
                .batchId(batch.getId())
                .bizType(cmd.bizType())
                .changeQuantity(cmd.quantity())
                .beforeQuantity(change.before())
                .afterQuantity(change.after())
                .unitCost(batch.getCostPrice())
                .bizNo(cmd.bizNo())
                .bizId(cmd.bizId())
                .remark(cmd.remark())
                .build());

        syncCacheAfterCommit(cmd.productId(), change.after());
        return batch;
    }

    // ==================================================================
    //  出库
    // ==================================================================

    /**
     * 出库：按 FEFO 扣批次 + 扣总库存 + 写流水
     *
     * FEFO（First Expired First Out，先到期先出）：
     * 不是简单的先进先出，而是「到期日最早的最先卖」。
     * 便利店/超市场景下这是硬要求 —— 都按进货先后出的话，
     * 后进但快到期的货会一直压在货架后面，最后全部报废。
     *
     * @return 实际扣减的批次明细（一个商品可能跨多个批次）
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public List<BatchDeductResult> outbound(OutboundCmd cmd) {
        return doOutbound(cmd, null);
    }

    /**
     * 出库（可显式指定扣减方案）
     *
     * 压测接口要在同一批数据上分别跑「数据库乐观锁」和「Redis+Lua」两种方案做对比，
     * 所以额外开一个带方案参数的重载。正常业务只走上面那个方法、认配置文件里的方案 ——
     * 把方案塞进 OutboundCmd 的话，每个业务调用点都要被迫传一个它根本不关心的参数。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public List<BatchDeductResult> outboundWithMode(OutboundCmd cmd, StockDeductMode mode) {
        return doOutbound(cmd, mode);
    }

    private List<BatchDeductResult> doOutbound(OutboundCmd cmd, StockDeductMode modeOverride) {
        // 先做一次总量校验，好在错误信息里直接告诉用户「当前有多少、需要多少」，
        // 而不是等到扣批次的时候才发现凑不齐
        Stock stock = requireStock(cmd.productId());
        if (stock.getQuantity() < cmd.quantity()) {
            throw BizException.stockNotEnough(cmd.productName(), stock.getQuantity(), cmd.quantity());
        }

        List<StockBatch> batches = pickAvailableBatches(cmd.productId());
        List<BatchDeductResult> results = new ArrayList<>();
        int remaining = cmd.quantity();

        for (StockBatch batch : batches) {
            if (remaining <= 0) {
                break;
            }
            int take = Math.min(remaining, batch.getStockQuantity());

            // 条件更新：要求批次剩余量仍然 >= 要扣的数量。
            // 影响行数为 0 说明这个批次已被并发事务先扣走了，
            // 直接跳过继续找下一个批次 —— 不用抛异常重试，对调用方更友好。
            int rows = stockBatchMapper.update(null, Wrappers.<StockBatch>lambdaUpdate()
                    .setSql("stock_quantity = stock_quantity - " + take)
                    .setSql("out_quantity = out_quantity + " + take)
                    .eq(StockBatch::getId, batch.getId())
                    .ge(StockBatch::getStockQuantity, take));
            if (rows == 0) {
                continue;
            }

            remaining -= take;
            results.add(new BatchDeductResult(batch.getId(), batch.getBatchNo(), take,
                    batch.getCostPrice(), batch.getStockQuantity() - take));
        }

        if (remaining > 0) {
            // 走到这里说明批次合计不够。理论上前面的总量校验已经挡住了，
            // 只有在并发下批次被别人抢先扣走时才会出现，属于兜底分支。
            throw BizException.stockNotEnough(cmd.productName(),
                    cmd.quantity() - remaining, cmd.quantity());
        }

        // 批次扣光的标记为无效，避免后续 FEFO 反复扫到空批次
        stockBatchMapper.update(null, Wrappers.<StockBatch>lambdaUpdate()
                .set(StockBatch::getStatus, 0)
                .in(StockBatch::getId, results.stream().map(BatchDeductResult::getBatchId).toList())
                .le(StockBatch::getStockQuantity, 0));

        // 扣总库存。注意必须放在批次扣减之后 ——
        // 这样即使这里失败抛异常，整个事务回滚，批次也不会被扣掉
        StockChange change = deductStock(stock.getProductId(), cmd.productName(),
                cmd.quantity(), modeOverride);

        // 每个批次写一条流水：盘点对账时要能一路追到「哪一批少了几个」
        for (BatchDeductResult r : results) {
            writeRecord(RecordCmd.builder()
                    .productId(cmd.productId())
                    .batchId(r.getBatchId())
                    .bizType(cmd.bizType())
                    .changeQuantity(-r.getQuantity())
                    .beforeQuantity(change.before())
                    .afterQuantity(change.after())
                    .unitCost(r.getCostPrice())
                    .bizNo(cmd.bizNo())
                    .bizId(cmd.bizId())
                    .remark(cmd.remark())
                    .build());
        }

        syncCacheAfterCommit(cmd.productId(), change.after());
        return results;
    }

    // ==================================================================
    //  从指定批次扣减
    // ==================================================================

    /**
     * 从指定批次扣减库存
     *
     * 与 outbound 的区别：outbound 由系统按 FEFO 自己挑批次，
     * 这个方法扣的是调用方明确指定的那一批。用在两个场景：
     *   1. 采购退货时指定退哪一批
     *   2. 退货单作废时，按当初退货产生的流水把货再扣回去
     *
     * 刻意不校验批次是否过期：这是账务冲正，不是对外销售。
     * 如果因为「这批货现在过期了」就禁止冲正，作废操作会永远失败，
     * 单据卡在中间状态，账反而更乱。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public BatchDeductResult deductFromBatch(DeductBatchCmd cmd) {
        StockBatch batch = stockBatchMapper.selectById(cmd.batchId());
        if (batch == null) {
            throw BizException.notFound("库存批次");
        }

        // 归属校验：batchId 是客户端可以直接传进来的（采购退货允许指定退哪一批），
        // 而 productId 是另一个独立入参，两者之间没有任何天然约束。
        // 万一传进来一个「属于别的商品」的批次，会把这批货扣掉、却去减另一个商品的总库存 ——
        // 两个商品的账同时被搞乱，而且盘点都追不回来。所以这里必须挡住。
        if (!batch.getProductId().equals(cmd.productId())) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "批次【" + batch.getBatchNo() + "】不属于商品【" + cmd.productName()
                            + "】，请重新选择要退货的批次");
        }

        int remain = batch.getStockQuantity() == null ? 0 : batch.getStockQuantity();
        // 条件更新，防止并发下把批次扣成负数
        int rows = stockBatchMapper.update(null, Wrappers.<StockBatch>lambdaUpdate()
                .setSql("stock_quantity = stock_quantity - " + cmd.quantity())
                .setSql("out_quantity = out_quantity + " + cmd.quantity())
                .eq(StockBatch::getId, cmd.batchId())
                .ge(StockBatch::getStockQuantity, cmd.quantity()));
        if (rows == 0) {
            throw new BizException(ErrorCode.BATCH_NOT_ENOUGH,
                    "批次【" + batch.getBatchNo() + "】库存不足，当前剩余 " + remain
                            + "，需要 " + cmd.quantity());
        }

        stockBatchMapper.update(null, Wrappers.<StockBatch>lambdaUpdate()
                .set(StockBatch::getStatus, 0)
                .eq(StockBatch::getId, cmd.batchId())
                .le(StockBatch::getStockQuantity, 0));

        StockChange change = deductStock(cmd.productId(), cmd.productName(), cmd.quantity(), null);

        writeRecord(RecordCmd.builder()
                .productId(cmd.productId())
                .batchId(cmd.batchId())
                .bizType(cmd.bizType())
                .changeQuantity(-cmd.quantity())
                .beforeQuantity(change.before())
                .afterQuantity(change.after())
                .unitCost(batch.getCostPrice())
                .bizNo(cmd.bizNo())
                .bizId(cmd.bizId())
                .remark(cmd.remark())
                .build());

        syncCacheAfterCommit(cmd.productId(), change.after());
        return new BatchDeductResult(batch.getId(), batch.getBatchNo(), cmd.quantity(),
                batch.getCostPrice(), remain - cmd.quantity());
    }

    // ==================================================================
    //  回退到指定批次（销售退货 / 销售单作废）
    // ==================================================================

    /**
     * 把货物退回指定批次
     *
     * 为什么必须回到原批次、而不是像盘盈那样新建一个批次：
     *   销售退货退回的是「当初从这批货里卖出去的那几件」，它还是原来那批货、
     *   还是原来的生产日期和到期日期。如果新建批次，这批货的到期日信息就丢了，
     *   保质期管理立刻失效 —— 退回来的临期货会被当成新货重新上架。
     *
     * 作废已出库的销售单同样走这里，保证「卖出去」和「撤销卖出」是对称的。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void restoreToBatch(RestoreCmd cmd) {
        StockBatch batch = stockBatchMapper.selectById(cmd.batchId());
        if (batch == null) {
            throw BizException.notFound("库存批次");
        }

        stockBatchMapper.update(null, Wrappers.<StockBatch>lambdaUpdate()
                .setSql("stock_quantity = stock_quantity + " + cmd.quantity())
                // 历史出库量同步冲抵。用 GREATEST 兜底：中间如果做过盘点，
                // 出库累计数可能已经和当初对不上，不能让这个字段变成负数
                .setSql("out_quantity = GREATEST(out_quantity - " + cmd.quantity() + ", 0)")
                .eq(StockBatch::getId, cmd.batchId()));

        // 批次原来可能因为扣光而被标记为无效，退回后要重新激活，
        // 否则 FEFO 挑批次时会把刚退回来的货漏掉
        stockBatchMapper.update(null, Wrappers.<StockBatch>lambdaUpdate()
                .set(StockBatch::getStatus, 1)
                .eq(StockBatch::getId, cmd.batchId())
                .gt(StockBatch::getStockQuantity, 0));

        StockChange change = applyChange(cmd.productId(), cmd.quantity(), null);

        writeRecord(RecordCmd.builder()
                .productId(cmd.productId())
                .batchId(cmd.batchId())
                .bizType(cmd.bizType())
                .changeQuantity(cmd.quantity())
                .beforeQuantity(change.before())
                .afterQuantity(change.after())
                .unitCost(batch.getCostPrice())
                .bizNo(cmd.bizNo())
                .bizId(cmd.bizId())
                .remark(cmd.remark())
                .build());

        syncCacheAfterCommit(cmd.productId(), change.after());
    }

    // ==================================================================
    //  盘点调整
    // ==================================================================

    /**
     * 按盘点差异调整库存
     *
     * 盘盈（diff > 0）：建一个「盘盈批次」把数量补回去，成本沿用当前平均成本。
     *                  不指定批次是因为货架上凭空多出来的东西没有来源批次。
     * 盘亏（diff < 0）：按 FEFO 扣批次，和正常出库同一套逻辑，
     *                  这样差异的扣减同样能精确落到具体批次上。
     *
     * @return 变动前后的库存
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public StockChange adjust(AdjustCmd cmd) {
        if (cmd.diffQuantity() == 0) {
            Stock stock = requireStock(cmd.productId());
            return new StockChange(stock.getQuantity(), stock.getQuantity());
        }

        if (cmd.diffQuantity() > 0) {
            // 盘盈
            String batchNo = "PY" + (cmd.bizNo() == null ? "" : cmd.bizNo());
            StockBatch batch = inbound(new InboundCmd(
                    cmd.productId(), cmd.productName(), cmd.diffQuantity(),
                    cmd.unitCost(), batchNo, null, null,
                    BizTypeEnum.CHECK_GAIN, cmd.bizNo(), cmd.bizId(), cmd.remark()));
            Stock stock = requireStock(cmd.productId());
            return new StockChange(stock.getQuantity() - batch.getInitQuantity(), stock.getQuantity());
        }

        // 盘亏：数量不会超过账面数（实盘不可能为负），所以这里不需要兜底减数量
        int loss = -cmd.diffQuantity();
        outbound(new OutboundCmd(cmd.productId(), cmd.productName(), loss,
                BizTypeEnum.CHECK_LOSS, cmd.bizNo(), cmd.bizId(), cmd.remark()));
        Stock stock = requireStock(cmd.productId());
        return new StockChange(stock.getQuantity() + loss, stock.getQuantity());
    }

    // ==================================================================
    //  库存增减的底层实现
    // ==================================================================

    /**
     * 增加库存，并可选地更新平均成本
     *
     * 乐观锁重试：updateById 会自动带上 `AND version = 旧值`，
     * 影响行数为 0 就说明这条记录在我们读取之后被别人改过，
     * 于是重新读一次最新的再试，最多 maxRetry 次。
     */
    private StockChange applyChange(Long productId, int delta, BigDecimal newAvgCost) {
        int maxRetry = props.getStock().getMaxRetry();
        for (int attempt = 0; attempt <= maxRetry; attempt++) {
            Stock fresh = requireStock(productId);
            int before = fresh.getQuantity();
            fresh.setQuantity(before + delta);
            if (newAvgCost != null) {
                fresh.setAvgCost(newAvgCost);
            }
            fresh.setUpdateTime(LocalDateTime.now());
            if (stockMapper.updateById(fresh) > 0) {
                return new StockChange(before, before + delta);
            }
            log.debug("库存乐观锁冲突，重试第 {} 次 productId={}", attempt + 1, productId);
        }
        throw new BizException(ErrorCode.STOCK_CONCURRENT_MODIFY);
    }

    /**
     * 扣减库存（带余量校验与乐观锁重试）
     *
     * REDIS 模式下先在缓存里做一次原子预扣减：这样高并发时绝大多数请求
     * 在缓存层就被挡掉了，不会全部压到数据库上。缓存扣减成功后再落库，
     * 落库失败由事务回调把缓存补回来。
     */
    private StockChange deductStock(Long productId, String productName, int quantity,
                                    StockDeductMode modeOverride) {
        StockDeductMode mode = modeOverride == null ? props.getStock().getDeductMode() : modeOverride;
        boolean redisFirst = mode == StockDeductMode.REDIS;
        if (redisFirst) {
            preDeductInCache(productId, productName, quantity);
        }

        int maxRetry = props.getStock().getMaxRetry();
        for (int attempt = 0; attempt <= maxRetry; attempt++) {
            Stock fresh = requireStock(productId);
            int before = fresh.getQuantity();
            if (before < quantity) {
                throw BizException.stockNotEnough(productName, before, quantity);
            }
            fresh.setQuantity(before - quantity);
            fresh.setUpdateTime(LocalDateTime.now());
            if (stockMapper.updateById(fresh) > 0) {
                return new StockChange(before, before - quantity);
            }
            log.debug("库存乐观锁冲突，重试第 {} 次 productId={}", attempt + 1, productId);
        }
        throw new BizException(ErrorCode.STOCK_CONCURRENT_MODIFY);
    }

    /** Redis 预扣减。缓存说不够时先回源核对一次，避免缓存偏旧造成误判 */
    private void preDeductInCache(Long productId, String productName, int quantity) {
        cacheService.loadIfAbsent(productId);
        long remain = cacheService.tryDeduct(productId, quantity);
        if (remain == StockCacheService.NOT_ENOUGH) {
            long accurate = cacheService.reload(productId);
            remain = cacheService.tryDeduct(productId, quantity);
            if (remain == StockCacheService.NOT_ENOUGH) {
                throw BizException.stockNotEnough(productName, (int) accurate, quantity);
            }
        }
        // 事务回滚时把缓存补回去，保证缓存不会比数据库少
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != TransactionSynchronization.STATUS_COMMITTED) {
                        cacheService.reload(productId);
                    }
                }
            });
        }
    }

    /**
     * 挑出可出库的批次，按 FEFO 排序
     *
     * 排序放在 Java 里做而不是 SQL 的 ORDER BY：
     * 一个商品的在库批次通常只有几个，Java 排序完全够用；
     * 而 SQL 里要表达「到期日升序、无到期日排最后」，得写
     * ORDER BY expire_date IS NULL, expire_date，这种表达式既不好读，
     * 也容易被框架的 SQL 注入检查拦下来。
     */
    private List<StockBatch> pickAvailableBatches(Long productId) {
        List<StockBatch> batches = stockBatchMapper.selectList(Wrappers.<StockBatch>lambdaQuery()
                .eq(StockBatch::getProductId, productId)
                .eq(StockBatch::getStatus, 1)
                .gt(StockBatch::getStockQuantity, 0)
                // 已过期的批次不允许自动出库，必须走盘点报损或退供应商
                .and(w -> w.isNull(StockBatch::getExpireDate)
                        .or().ge(StockBatch::getExpireDate, LocalDate.now())));

        batches.sort(Comparator
                .comparing(StockBatch::getExpireDate,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(StockBatch::getId));
        return batches;
    }

    // ==================================================================
    //  流水与工具
    // ==================================================================

    private void writeRecord(RecordCmd cmd) {
        StockRecord record = new StockRecord();
        record.setProductId(cmd.productId());
        record.setBatchId(cmd.batchId());
        record.setBizType(cmd.bizType().name());
        record.setChangeQuantity(cmd.changeQuantity());
        record.setBeforeQuantity(cmd.beforeQuantity());
        record.setAfterQuantity(cmd.afterQuantity());
        record.setUnitCost(cmd.unitCost() == null ? BigDecimal.ZERO : cmd.unitCost());
        record.setBizNo(cmd.bizNo());
        record.setBizId(cmd.bizId());
        record.setRemark(cmd.remark() == null ? cmd.bizType().getLabel() : cmd.remark());

        LoginUser operator = UserContext.get();
        if (operator != null) {
            record.setOperatorId(operator.getUserId());
            record.setOperatorName(operator.getRealName());
        }
        stockRecordMapper.insert(record);
    }

    /**
     * 事务提交后再更新缓存
     *
     * 如果提交前就写缓存，事务一旦回滚，缓存里就留下了一个数据库并不存在的值，
     * 后续所有基于缓存的判断都是错的。afterCommit 才是唯一安全的时间点。
     */
    private void syncCacheAfterCommit(Long productId, int quantity) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cacheService.sync(productId, quantity);
                }
            });
        } else {
            cacheService.sync(productId, quantity);
        }
    }

    private Stock requireStock(Long productId) {
        Stock stock = stockMapper.selectOne(Wrappers.<Stock>lambdaQuery()
                .eq(Stock::getProductId, productId));
        if (stock == null) {
            throw new BizException(ErrorCode.NOT_FOUND,
                    "商品 id=" + productId + " 没有库存记录，数据异常，请联系管理员");
        }
        return stock;
    }

    /**
     * 移动加权平均成本
     *
     *   新成本 = (原库存 × 原成本 + 本次入库量 × 本次进价) / 新库存总量
     *
     * 为什么用移动加权平均而不是先进先出计价：
     *   小门店的商品同批进货价经常波动，移动加权平均能让成本随进货实时更新，
     *   又不需要为每次出库维护一长串成本队列。保留 4 位小数是必需的 ——
     *   只保留 2 位的话，反复「进货 + 出库」后误差会累积到肉眼可见。
     */
    private BigDecimal movingAverage(int oldQty, BigDecimal oldAvg, int inQty, BigDecimal inPrice) {
        int total = oldQty + inQty;
        if (total <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal safeOldAvg = oldAvg == null ? BigDecimal.ZERO : oldAvg;
        BigDecimal safeInPrice = inPrice == null ? BigDecimal.ZERO : inPrice;

        BigDecimal oldValue = safeOldAvg.multiply(BigDecimal.valueOf(oldQty));
        BigDecimal inValue = safeInPrice.multiply(BigDecimal.valueOf(inQty));
        return oldValue.add(inValue)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
    }

    // ==================================================================
    //  入参 / 出参对象
    // ==================================================================

    /** 库存变动前后的快照 */
    public record StockChange(int before, int after) {
    }

    /** 入库指令 */
    public record InboundCmd(
            Long productId,
            String productName,
            int quantity,
            BigDecimal costPrice,
            String batchNo,
            LocalDate productionDate,
            LocalDate expireDate,
            BizTypeEnum bizType,
            String bizNo,
            Long bizId,
            String remark
    ) {
    }

    /** 出库指令 */
    public record OutboundCmd(
            Long productId,
            String productName,
            int quantity,
            BizTypeEnum bizType,
            String bizNo,
            Long bizId,
            String remark
    ) {
    }

    /** 盘点调整指令 */
    public record AdjustCmd(
            Long productId,
            String productName,
            int diffQuantity,
            BigDecimal unitCost,
            String bizNo,
            Long bizId,
            String remark
    ) {
    }

    /** 回退到指定批次指令（销售退货 / 销售单作废） */
    public record RestoreCmd(
            Long productId,
            Long batchId,
            int quantity,
            BizTypeEnum bizType,
            String bizNo,
            Long bizId,
            String remark
    ) {
    }

    /** 从指定批次扣减指令（采购退货指定批次 / 退货单作废冲正） */
    public record DeductBatchCmd(
            Long productId,
            String productName,
            Long batchId,
            int quantity,
            BizTypeEnum bizType,
            String bizNo,
            Long bizId,
            String remark
    ) {
    }

    /** 写流水用的内部参数（字段多，用 builder 避免构造参数错位） */
    @lombok.Builder
    private record RecordCmd(
            Long productId,
            Long batchId,
            BizTypeEnum bizType,
            int changeQuantity,
            int beforeQuantity,
            int afterQuantity,
            BigDecimal unitCost,
            String bizNo,
            Long bizId,
            String remark
    ) {
    }
}
