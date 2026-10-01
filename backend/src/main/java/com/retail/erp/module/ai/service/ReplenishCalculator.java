package com.retail.erp.module.ai.service;

import com.retail.erp.module.ai.dto.AiProductRow;
import com.retail.erp.module.ai.dto.ReplenishSuggestionVO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 补货量计算器（纯本地算法，不依赖任何外部服务）
 *
 * 用「移动平均 + 安全库存」这套在零售业已经跑了几十年的经典方法，
 * 而不是一上来就上时序预测模型。原因很实在：
 *   1. 小门店单品日销量只有个位数，样本太稀疏，复杂模型的过拟合风险远大于收益
 *   2. 这套公式的每个参数店长都能看懂、能自己调，出了偏差能解释
 *   3. 不依赖网络，断网、AI 欠费、模型限流都不影响补货这件最核心的日常事
 * AI 在这里的角色是「把算出来的数字讲成人话」，而不是替代计算。
 *
 * 公式：
 *   安全库存 = Z × 日销量标准差 × √到货周期         （Z=1.65 对应 95% 服务水平）
 *   补货点   = 日均销量 × 到货周期 + 安全库存
 *   建议补货 = 目标库存 − 当前库存
 *
 * 为什么要乘 √到货周期而不是直接乘到货周期：
 * 需求波动是按天独立累加的，多天累计波动的增长是 √t 而不是 t。
 * 直接乘天数会把安全库存算大一倍多，结果是店里常年压着一堆货。
 */
@Component
public class ReplenishCalculator {

    /** 服务水平 95% 对应的 Z 值 */
    public static final BigDecimal Z_95 = BigDecimal.valueOf(1.65);

    /** 供应商到货周期（天）。真实项目里应该按供应商分别配置，这里统一取 3 天 */
    public static final int LEAD_TIME_DAYS = 3;

    /** 没有设置库存上限时，按多少天的销量作为目标库存 */
    private static final int DEFAULT_COVER_DAYS = 30;

    /** 算法说明，直接展示给用户，也方便答辩时解释 */
    public static final String ALGORITHM_DESC =
            "移动平均 + 安全库存（服务水平 95%，Z=1.65，到货周期 3 天）";

    /**
     * 计算单品补货建议
     *
     * @param product  商品与当前库存
     * @param avgDaily 近 N 天日均销量
     * @param stdDev   近 N 天日销量标准差（没有销量的天按 0 计入）
     * @param days     统计窗口天数
     * @return 补货建议；如果这个商品不需要补货则返回 null
     */
    public ReplenishSuggestionVO calculate(AiProductRow product,
                                           BigDecimal avgDaily,
                                           BigDecimal stdDev,
                                           int days) {
        int current = product.getQuantity() == null ? 0 : product.getQuantity();
        int lower = product.getStockLower() == null ? 0 : product.getStockLower();
        int upper = product.getStockUpper() == null ? 0 : product.getStockUpper();

        int safetyStock = Z_95.multiply(stdDev)
                .multiply(BigDecimal.valueOf(Math.sqrt(LEAD_TIME_DAYS)))
                .setScale(0, RoundingMode.CEILING)
                .intValue();

        int leadTimeDemand = avgDaily.multiply(BigDecimal.valueOf(LEAD_TIME_DAYS))
                .setScale(0, RoundingMode.CEILING)
                .intValue();
        int reorderPoint = leadTimeDemand + safetyStock;

        // 该不该补：库存已经跌破补货点，或者跌破了商品自己设的下限
        boolean belowReorderPoint = current <= reorderPoint;
        boolean belowLower = lower > 0 && current < lower;
        if (!belowReorderPoint && !belowLower) {
            return null;
        }

        int targetStock = upper > 0
                ? upper
                : avgDaily.multiply(BigDecimal.valueOf(DEFAULT_COVER_DAYS))
                        .setScale(0, RoundingMode.CEILING).intValue();
        int suggestQuantity = Math.max(targetStock - current, 0);
        if (suggestQuantity <= 0) {
            return null;
        }

        ReplenishSuggestionVO vo = new ReplenishSuggestionVO();
        vo.setProductId(product.getProductId());
        vo.setProductName(product.getProductName());
        vo.setUnit(product.getUnit());
        vo.setCurrentStock(current);
        vo.setStockLower(lower);
        vo.setStockUpper(upper);
        vo.setAvgDailySales(avgDaily.setScale(2, RoundingMode.HALF_UP));
        vo.setAvgWeeklySales(avgDaily.multiply(BigDecimal.valueOf(7)).setScale(2, RoundingMode.HALF_UP));
        vo.setSafetyStock(safetyStock);
        vo.setLeadTimeDays(LEAD_TIME_DAYS);
        vo.setReorderPoint(reorderPoint);
        vo.setSuggestQuantity(suggestQuantity);

        BigDecimal price = product.getPurchasePrice() == null ? BigDecimal.ZERO : product.getPurchasePrice();
        vo.setEstCost(price.multiply(BigDecimal.valueOf(suggestQuantity)).setScale(2, RoundingMode.HALF_UP));

        BigDecimal stockDays = avgDaily.signum() > 0
                ? BigDecimal.valueOf(current).divide(avgDaily, 1, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(999);
        vo.setStockDays(stockDays);

        vo.setUrgency(resolveUrgency(current, stockDays));
        vo.setReason(buildReason(vo, days));
        return vo;
    }

    /**
     * 紧急程度
     *
     * 断货永远是最紧急的，其次才是「还能撑几天」。
     * 把断货单独拎出来判，是因为断货时 stockDays 算出来是 0，
     * 虽然也会落进 HIGH，但理由文案完全不同 —— 一个是「已经没货了」，
     * 一个是「快没货了」，给店员的行动指令不一样。
     */
    private String resolveUrgency(int current, BigDecimal stockDays) {
        if (current <= 0) {
            return "HIGH";
        }
        double days = stockDays.doubleValue();
        if (days < 3) {
            return "HIGH";
        }
        if (days < 7) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String buildReason(ReplenishSuggestionVO vo, int days) {
        StringBuilder sb = new StringBuilder();
        sb.append("近 ").append(days).append(" 天日均销量 ")
                .append(vo.getAvgDailySales().toPlainString()).append(" ").append(vo.getUnit());

        if (vo.getCurrentStock() <= 0) {
            sb.append("；当前已经断货，需立即补货");
        } else {
            sb.append("；当前库存 ").append(vo.getCurrentStock())
                    .append("，约可支撑 ").append(vo.getStockDays().toPlainString()).append(" 天");
        }

        sb.append("；补货点 ").append(vo.getReorderPoint())
                .append("（含到货周期 ").append(vo.getLeadTimeDays())
                .append(" 天用量与安全库存 ").append(vo.getSafetyStock()).append("）");

        if (vo.getStockLower() != null && vo.getStockLower() > 0
                && vo.getCurrentStock() < vo.getStockLower()) {
            sb.append("；已跌破库存下限 ").append(vo.getStockLower());
        }
        return sb.toString();
    }

    // ==================================================================
    //  统计工具
    // ==================================================================

    /**
     * 把稀疏的按天销量补齐成完整的 N 天序列，再算日均与标准差
     *
     * 「补齐」这一步是必须的，也是最容易漏的：
     * 数据库里只会有「卖出去的那几天」的记录。如果直接对查出来的行求平均，
     * 分母变成有销量的天数而不是统计窗口天数，
     * 一个 30 天里只卖了 3 天的商品，日均会被算成「3 天销量 / 3」，
     * 严重高估，最后补货量算出来大得离谱。
     */
    public Stats computeStats(List<Long> dailyQuantities, int days) {
        if (days <= 0) {
            return new Stats(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        List<Long> series = new ArrayList<>(dailyQuantities);
        while (series.size() < days) {
            series.add(0L);
        }

        double sum = 0;
        for (Long q : series) {
            sum += (q == null ? 0 : q);
        }
        double mean = sum / days;

        double variance = 0;
        for (Long q : series) {
            double value = (q == null ? 0 : q);
            variance += (value - mean) * (value - mean);
        }
        variance /= days;
        double stdDev = Math.sqrt(variance);

        return new Stats(
                BigDecimal.valueOf(mean).setScale(4, RoundingMode.HALF_UP),
                BigDecimal.valueOf(stdDev).setScale(4, RoundingMode.HALF_UP));
    }

    /** 按日期顺序把销量行铺进 N 天序列 */
    public List<Long> toDailySeries(Map<String, Long> quantityByDate, LocalDate start, int days) {
        List<Long> series = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            String key = start.plusDays(i).toString();
            series.add(quantityByDate.getOrDefault(key, 0L));
        }
        return series;
    }

    /** 统计窗口的起始日。含今天，所以是 days-1 天前 */
    public LocalDate windowStart(int days, LocalDate today) {
        return today.minusDays(Math.max(days - 1L, 0L));
    }

    /** 实际覆盖天数，用于展示「近 N 天」时对齐真实数据范围 */
    public long actualDays(LocalDate start, LocalDate end) {
        return ChronoUnit.DAYS.between(start, end) + 1;
    }

    /** 日均销量与标准差 */
    public record Stats(BigDecimal avgDaily, BigDecimal stdDev) {
    }
}
