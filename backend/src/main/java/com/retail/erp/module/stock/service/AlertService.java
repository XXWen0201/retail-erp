package com.retail.erp.module.stock.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.retail.erp.common.BizException;
import com.retail.erp.common.PageResult;
import com.retail.erp.common.enums.AlertStatusEnum;
import com.retail.erp.common.enums.AlertTypeEnum;
import com.retail.erp.config.AppProperties;
import com.retail.erp.module.stock.dto.AlertScanResultVO;
import com.retail.erp.module.stock.dto.AlertSummaryVO;
import com.retail.erp.module.stock.dto.BatchExpiryRow;
import com.retail.erp.module.stock.dto.ProductStockRow;
import com.retail.erp.module.stock.dto.StockAlertQuery;
import com.retail.erp.module.stock.dto.StockAlertVO;
import com.retail.erp.module.stock.entity.StockAlert;
import com.retail.erp.module.stock.entity.StockBatch;
import com.retail.erp.module.stock.mapper.StockAlertMapper;
import com.retail.erp.module.stock.mapper.StockBatchMapper;
import com.retail.erp.module.stock.mapper.StockPageMapper;
import com.retail.erp.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存预警服务
 *
 * 五类预警、两个维度：
 *   商品维度 —— 零库存断货、低于下限、高于上限
 *   批次维度 —— 临近到期、已经过期
 *
 * 扫描是「幂等」的：同一商品同一问题反复扫，只会更新已有预警的当前值，
 * 不会一直往里插新记录。否则每天扫两次、一个月就是 60 条重复的低库存预警，
 * 预警列表直接变成垃圾堆，真正该处理的那条反而被埋掉。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    /** 商品级预警在库里用 batch_id = 0 表示「与批次无关」 */
    private static final long NO_BATCH = 0L;

    private final StockAlertMapper alertMapper;
    private final StockBatchMapper stockBatchMapper;
    private final StockPageMapper stockPageMapper;
    private final AppProperties props;

    // ==================================================================
    //  查询
    // ==================================================================

    public PageResult<StockAlertVO> page(StockAlertQuery query) {
        IPage<StockAlert> page = alertMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()),
                Wrappers.<StockAlert>lambdaQuery()
                        .eq(StringUtils.hasText(query.getAlertType()),
                                StockAlert::getAlertType, query.getAlertType())
                        .eq(StringUtils.hasText(query.getAlertLevel()),
                                StockAlert::getAlertLevel, query.getAlertLevel())
                        .eq(StringUtils.hasText(query.getStatus()),
                                StockAlert::getStatus, query.getStatus())
                        .like(StringUtils.hasText(query.getKeyword()),
                                StockAlert::getProductName, query.getKeyword())
                        // 严重程度优先，其次是越早出现的越靠前，保证最该处理的在最上面
                        .orderByDesc(StockAlert::getAlertLevel)
                        .orderByAsc(StockAlert::getId));
        return PageResult.of(page).map(this::toVO);
    }

    public AlertSummaryVO summary() {
        AlertSummaryVO vo = new AlertSummaryVO();
        // 一次 GROUP BY 拿到五类数量，比发五条 COUNT 少四次数据库往返。
        // 别名刻意写成驼峰：不同 MyBatis 版本对 Map 结果是否做下划线转驼峰并不一致，
        // 别名里没有下划线，两种行为下取到的 key 都一样，不会因为升级版本而失效
        List<Map<String, Object>> rows = alertMapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<StockAlert>()
                        .select("alert_type AS alertType", "COUNT(*) AS cnt")
                        .eq("status", AlertStatusEnum.UNHANDLED.name())
                        .groupBy("alert_type"));

        long total = 0;
        for (Map<String, Object> row : rows) {
            String type = String.valueOf(row.get("alertType"));
            long cnt = toLong(row.get("cnt"));
            total += cnt;
            if (AlertTypeEnum.LOW_STOCK.name().equals(type)) {
                vo.setLowStockCount(cnt);
            } else if (AlertTypeEnum.OVER_STOCK.name().equals(type)) {
                vo.setOverStockCount(cnt);
            } else if (AlertTypeEnum.OUT_OF_STOCK.name().equals(type)) {
                vo.setOutOfStockCount(cnt);
            } else if (AlertTypeEnum.NEAR_EXPIRY.name().equals(type)) {
                vo.setNearExpiryCount(cnt);
            } else if (AlertTypeEnum.EXPIRED.name().equals(type)) {
                vo.setExpiredCount(cnt);
            }
        }
        vo.setUnhandledTotal(total);
        return vo;
    }

    // ==================================================================
    //  扫描
    // ==================================================================

    @Transactional(rollbackFor = Exception.class)
    public AlertScanResultVO scan() {
        long created = 0;
        long updated = 0;
        long closed = 0;
        List<StockAlertVO> newAlerts = new ArrayList<>();

        // ---------- 一、商品维度 ----------
        for (ProductStockRow row : stockPageMapper.selectProductStockRows()) {
            int quantity = row.getQuantity() == null ? 0 : row.getQuantity();
            int lower = row.getStockLower() == null ? 0 : row.getStockLower();
            int upper = row.getStockUpper() == null ? 0 : row.getStockUpper();

            AlertTypeEnum type = resolveStockAlertType(quantity, lower, upper);
            if (type != null) {
                int threshold = switch (type) {
                    case OUT_OF_STOCK -> lower;
                    case LOW_STOCK -> lower;
                    case OVER_STOCK -> upper;
                    default -> 0;
                };
                UpsertOutcome outcome = upsert(type, row.getProductId(), row.getProductName(),
                        null, null, quantity, threshold, null);
                if (outcome.created()) {
                    created++;
                    newAlerts.add(outcome.vo());
                } else if (outcome.changed()) {
                    updated++;
                }
            }
            closed += closeStaleStockAlerts(row.getProductId(), quantity, lower, upper);
        }

        // ---------- 二、批次维度 ----------
        int nearExpiryDays = props.getAlert().getNearExpiryDays();
        LocalDate today = LocalDate.now();
        for (BatchExpiryRow batch : stockPageMapper.selectExpiringBatches(nearExpiryDays)) {
            long daysToExpire = ChronoUnit.DAYS.between(today, batch.getExpireDate());
            AlertTypeEnum type = daysToExpire < 0
                    ? AlertTypeEnum.EXPIRED : AlertTypeEnum.NEAR_EXPIRY;
            int threshold = (type == AlertTypeEnum.EXPIRED) ? 0 : nearExpiryDays;

            UpsertOutcome outcome = upsert(type, batch.getProductId(), batch.getProductName(),
                    batch.getBatchId(), batch.getBatchNo(), (int) daysToExpire, threshold,
                    batch.getExpireDate());
            if (outcome.created()) {
                created++;
                newAlerts.add(outcome.vo());
            } else if (outcome.changed()) {
                updated++;
            }
        }
        closed += closeStaleBatchAlerts();

        AlertSummaryVO summary = summary();
        log.info("预警扫描完成 新增={} 更新={} 自动关闭={} 未处理总数={}",
                created, updated, closed, summary.getUnhandledTotal());

        return new AlertScanResultVO((int) created, (int) updated, (int) closed,
                summary.getUnhandledTotal(), LocalDateTime.now(), newAlerts);
    }

    /**
     * 判断商品该报哪一类库存预警
     *
     * 判定顺序：断货 → 低于下限 → 高于上限。
     * 断货必须排在第一位：库里为 0 时，它同时也满足「低于下限」，
     * 如果先判下限，断货就永远只能报出一个「低于下限」的橙色提示，
     * 严重程度被降级，门店会当成普通补货而不是紧急断货。
     */
    private AlertTypeEnum resolveStockAlertType(int quantity, int lower, int upper) {
        if (quantity <= 0) {
            return AlertTypeEnum.OUT_OF_STOCK;
        }
        if (lower > 0 && quantity < lower) {
            return AlertTypeEnum.LOW_STOCK;
        }
        if (upper > 0 && quantity > upper) {
            return AlertTypeEnum.OVER_STOCK;
        }
        return null;
    }

    // ==================================================================
    //  写入与自动关闭
    // ==================================================================

    /**
     * 有则更新、无则插入
     *
     * 商品级预警统一存 batch_id = 0 而不是 NULL，是因为 SQL 的唯一索引
     * 不认为两个 NULL 相等：如果存 NULL，同一商品的低库存预警会被反复插进去，
     * 唯一索引完全拦不住。存 0 才能真正生效。
     */
    private UpsertOutcome upsert(AlertTypeEnum type, Long productId, String productName,
                                 Long batchId, String batchNo,
                                 int currentValue, int thresholdValue, LocalDate expireDate) {
        long batchKey = batchId == null ? NO_BATCH : batchId;

        StockAlert exist = alertMapper.selectOne(Wrappers.<StockAlert>lambdaQuery()
                .eq(StockAlert::getProductId, productId)
                .eq(StockAlert::getAlertType, type.name())
                .eq(StockAlert::getStatus, AlertStatusEnum.UNHANDLED.name())
                .eq(StockAlert::getBatchId, batchKey)
                .last("LIMIT 1"));

        if (exist != null) {
            boolean changed = !java.util.Objects.equals(exist.getCurrentValue(), currentValue)
                    || !java.util.Objects.equals(exist.getThresholdValue(), thresholdValue);
            if (changed) {
                exist.setCurrentValue(currentValue);
                exist.setThresholdValue(thresholdValue);
                exist.setExpireDate(expireDate);
                alertMapper.updateById(exist);
            }
            return new UpsertOutcome(false, changed, null);
        }

        // 历史上如果存在 batch_id 为 NULL 的同类型旧预警，也要认出来，
        // 避免升级后同一问题出现「一条 NULL + 一条 0」的重复预警
        if (batchId == null) {
            StockAlert legacy = alertMapper.selectOne(Wrappers.<StockAlert>lambdaQuery()
                    .eq(StockAlert::getProductId, productId)
                    .eq(StockAlert::getAlertType, type.name())
                    .eq(StockAlert::getStatus, AlertStatusEnum.UNHANDLED.name())
                    .isNull(StockAlert::getBatchId)
                    .last("LIMIT 1"));
            if (legacy != null) {
                legacy.setBatchId(NO_BATCH);
                legacy.setCurrentValue(currentValue);
                legacy.setThresholdValue(thresholdValue);
                alertMapper.updateById(legacy);
                return new UpsertOutcome(false, true, null);
            }
        }

        StockAlert alert = new StockAlert();
        alert.setProductId(productId);
        alert.setProductName(productName);
        alert.setBatchId(batchKey);
        alert.setBatchNo(batchNo);
        alert.setAlertType(type.name());
        alert.setAlertLevel(type.getDefaultLevel());
        alert.setCurrentValue(currentValue);
        alert.setThresholdValue(thresholdValue);
        alert.setExpireDate(expireDate);
        alert.setStatus(AlertStatusEnum.UNHANDLED.name());
        alertMapper.insert(alert);
        return new UpsertOutcome(true, true, toVO(alert));
    }

    /** 商品库存恢复正常后，自动关闭对应的库存类预警 */
    private long closeStaleStockAlerts(Long productId, int quantity, int lower, int upper) {
        List<StockAlert> open = alertMapper.selectList(Wrappers.<StockAlert>lambdaQuery()
                .eq(StockAlert::getProductId, productId)
                .eq(StockAlert::getStatus, AlertStatusEnum.UNHANDLED.name())
                .in(StockAlert::getAlertType,
                        AlertTypeEnum.OUT_OF_STOCK.name(),
                        AlertTypeEnum.LOW_STOCK.name(),
                        AlertTypeEnum.OVER_STOCK.name()));

        long closed = 0;
        for (StockAlert alert : open) {
            AlertTypeEnum type;
            try {
                type = AlertTypeEnum.valueOf(alert.getAlertType());
            } catch (IllegalArgumentException e) {
                continue;
            }
            boolean stillValid = resolveStockAlertType(quantity, lower, upper) == type;
            if (!stillValid) {
                autoClose(alert, "库存已恢复正常，系统自动关闭");
                closed++;
            }
        }
        return closed;
    }

    /** 批次已卖光或已被清理后，自动关闭对应的保质期类预警 */
    private long closeStaleBatchAlerts() {
        List<StockAlert> open = alertMapper.selectList(Wrappers.<StockAlert>lambdaQuery()
                .eq(StockAlert::getStatus, AlertStatusEnum.UNHANDLED.name())
                .gt(StockAlert::getBatchId, NO_BATCH)
                .in(StockAlert::getAlertType,
                        AlertTypeEnum.NEAR_EXPIRY.name(),
                        AlertTypeEnum.EXPIRED.name()));
        if (open.isEmpty()) {
            return 0;
        }

        List<Long> batchIds = open.stream().map(StockAlert::getBatchId).distinct().toList();
        Map<Long, Integer> remainMap = new HashMap<>();
        for (StockBatch batch : stockBatchMapper.selectBatchIds(batchIds)) {
            remainMap.put(batch.getId(), batch.getStockQuantity());
        }

        long closed = 0;
        for (StockAlert alert : open) {
            Integer remain = remainMap.get(alert.getBatchId());
            if (remain == null || remain <= 0) {
                autoClose(alert, "该批次已无剩余库存，系统自动关闭");
                closed++;
            }
        }
        return closed;
    }

    private void autoClose(StockAlert alert, String remark) {
        alert.setStatus(AlertStatusEnum.HANDLED.name());
        alert.setHandleRemark(remark);
        alert.setHandleTime(LocalDateTime.now());
        alert.setHandleUser("系统");
        alertMapper.updateById(alert);
    }

    // ==================================================================
    //  处理
    // ==================================================================

    @Transactional(rollbackFor = Exception.class)
    public void handle(Long id, String remark) {
        StockAlert alert = requireUnhandled(id);
        alert.setStatus(AlertStatusEnum.HANDLED.name());
        alert.setHandleRemark(StringUtils.hasText(remark) ? remark : "已处理");
        alert.setHandleTime(LocalDateTime.now());
        alert.setHandleUser(UserContext.currentUserName());
        alertMapper.updateById(alert);
    }

    @Transactional(rollbackFor = Exception.class)
    public void ignore(Long id) {
        StockAlert alert = requireUnhandled(id);
        alert.setStatus(AlertStatusEnum.IGNORED.name());
        alert.setHandleRemark("已忽略");
        alert.setHandleTime(LocalDateTime.now());
        alert.setHandleUser(UserContext.currentUserName());
        alertMapper.updateById(alert);
    }

    private StockAlert requireUnhandled(Long id) {
        StockAlert alert = alertMapper.selectById(id);
        if (alert == null) {
            throw BizException.notFound("预警记录");
        }
        if (!AlertStatusEnum.UNHANDLED.name().equals(alert.getStatus())) {
            throw BizException.statusIllegal("该预警已经处理过了");
        }
        return alert;
    }

    // ==================================================================

    private StockAlertVO toVO(StockAlert alert) {
        StockAlertVO vo = new StockAlertVO();
        vo.setId(alert.getId());
        vo.setProductId(alert.getProductId());
        vo.setProductName(alert.getProductName());
        // 存的是 0（与批次无关），对外统一还原成 null，前端按 null 判断更自然
        vo.setBatchId(alert.getBatchId() == null || alert.getBatchId() == NO_BATCH
                ? null : alert.getBatchId());
        vo.setBatchNo(alert.getBatchNo());
        vo.setAlertType(alert.getAlertType());
        vo.setAlertLevel(alert.getAlertLevel());
        vo.setCurrentValue(alert.getCurrentValue());
        vo.setThresholdValue(alert.getThresholdValue());
        vo.setExpireDate(alert.getExpireDate());
        vo.setStatus(alert.getStatus());
        vo.setStatusLabel(AlertStatusEnum.labelOf(alert.getStatus()));
        vo.setHandleRemark(alert.getHandleRemark());
        vo.setHandleTime(alert.getHandleTime());
        vo.setHandleUser(alert.getHandleUser());
        vo.setCreateTime(alert.getCreateTime());
        try {
            vo.setAlertTypeLabel(AlertTypeEnum.valueOf(alert.getAlertType()).getLabel());
        } catch (IllegalArgumentException e) {
            vo.setAlertTypeLabel(alert.getAlertType());
        }
        return vo;
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        return value instanceof Number n ? n.longValue() : Long.parseLong(value.toString());
    }

    /** 一次 upsert 的结果：是否新建、内容是否变化、新建时的展示对象 */
    private record UpsertOutcome(boolean created, boolean changed, StockAlertVO vo) {
    }
}
