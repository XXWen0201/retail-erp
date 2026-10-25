package com.retail.erp.module.ai;

import com.retail.erp.module.ai.dto.AiProductRow;
import com.retail.erp.module.ai.dto.ReplenishSuggestionVO;
import com.retail.erp.module.ai.service.ReplenishCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 补货算法测试
 *
 * 为什么优先测这个模块：它是整个系统里唯一「算错了不会报错」的地方 ——
 * 库存扣错会立刻抛异常或对不上账，但补货量算错只会安静地建议你多进或少进一批货，
 * 门店要过一两周才发现压了库存或者断了货。这种静默错误只能靠测试兜住。
 *
 * 好消息是它是个纯函数：不碰数据库、不碰 Redis、不调模型，
 * 所以这里不用 @SpringBootTest，直接 new 出来测，跑完只要几百毫秒。
 *
 * 公式（见 ReplenishCalculator 注释）：
 *   安全库存 = Z × 日销量标准差 × √到货周期     （Z=1.65，到货周期 3 天）
 *   补货点   = 日均销量 × 到货周期 + 安全库存
 *   建议补货 = 目标库存 − 当前库存
 */
@DisplayName("补货算法")
class ReplenishCalculatorTest {

    private final ReplenishCalculator calculator = new ReplenishCalculator();

    /** 造一个商品行，默认上限 0（不限）、下限 0（不设） */
    private AiProductRow product(int quantity, int lower, int upper) {
        AiProductRow row = new AiProductRow();
        row.setProductId(1L);
        row.setProductName("测试商品");
        row.setUnit("个");
        row.setPurchasePrice(new BigDecimal("10.00"));
        row.setQuantity(quantity);
        row.setStockLower(lower);
        row.setStockUpper(upper);
        row.setAvgCost(new BigDecimal("8.00"));
        return row;
    }

    // ==================================================================
    //  核心公式
    // ==================================================================

    @Nested
    @DisplayName("补货点与建议补货量")
    class Formula {

        @Test
        @DisplayName("销量稳定时：补货点 = 日均销量 × 到货周期，标准差为 0 时无安全库存")
        void stableDemandHasNoSafetyStock() {
            // 日均 10，标准差 0 -> 安全库存 0，补货点 = 10 × 3 = 30
            ReplenishSuggestionVO vo = calculator.calculate(
                    product(10, 0, 100), new BigDecimal("10"), BigDecimal.ZERO, 30);

            assertNotNull(vo, "库存 10 已低于补货点 30，应当给出建议");
            assertEquals(0, vo.getSafetyStock(), "销量完全稳定时安全库存应为 0");
            assertEquals(30, vo.getReorderPoint(), "补货点应为日均 10 × 到货周期 3");
            assertEquals(90, vo.getSuggestQuantity(), "上限 100 - 当前 10 = 建议补 90");
        }

        @Test
        @DisplayName("销量波动时安全库存 = Z × 标准差 × √3")
        void safetyStockUsesSqrtOfLeadTime() {
            // 标准差 4 -> 1.65 × 4 × 1.732 ≈ 11.43，向上取整 12
            ReplenishSuggestionVO vo = calculator.calculate(
                    product(5, 0, 200), new BigDecimal("10"), new BigDecimal("4"), 30);

            assertNotNull(vo);
            // 关键点：乘的是 √3≈1.732 而不是 3。乘 3 的话这里会是 20，
            // 安全库存会被系统性放大，门店常年压货
            assertEquals(12, vo.getSafetyStock(),
                    "安全库存应为 ceil(1.65 × 4 × √3) = 12，若为 20 说明误用了 ×3 而不是 ×√3");
            assertEquals(42, vo.getReorderPoint(), "补货点 = 10×3 + 12 = 42");
        }

        @Test
        @DisplayName("未设库存上限时，目标库存按 30 天销量推算")
        void targetStockFallsBackToCoverDays() {
            // 日均 2，无上限 -> 目标 = 2 × 30 = 60，当前 0 -> 建议补 60
            ReplenishSuggestionVO vo = calculator.calculate(
                    product(0, 0, 0), new BigDecimal("2"), BigDecimal.ZERO, 30);

            assertNotNull(vo);
            assertEquals(60, vo.getSuggestQuantity(),
                    "上限为 0 时应按 30 天销量 60 作为目标库存");
        }

        @Test
        @DisplayName("估算成本 = 参考进价 × 建议补货量")
        void estimatedCostUsesPurchasePrice() {
            ReplenishSuggestionVO vo = calculator.calculate(
                    product(0, 0, 100), new BigDecimal("5"), BigDecimal.ZERO, 30);

            assertNotNull(vo);
            assertEquals(100, vo.getSuggestQuantity());
            // 进价 10.00 × 100 = 1000.00
            assertEquals(0, new BigDecimal("1000.00").compareTo(vo.getEstCost()),
                    "估算成本应为进价 10 × 补货 100 = 1000");
        }
    }

    // ==================================================================
    //  按供应商的到货周期
    // ==================================================================

    @Nested
    @DisplayName("到货周期按供应商取")
    class LeadTime {

        @Test
        @DisplayName("到货周期越长，补货点越高（文具 7 天 vs 乳品 1 天）")
        void longerLeadTimeRaisesReorderPoint() {
            // 同样的销量与库存，只改到货周期。
            // 库存取 8：日均 10 时，1 天补货点约 13、7 天约 79，
            // 两个场景都低于各自补货点，才能都拿到建议来对比
            ReplenishSuggestionVO fast = calculator.calculate(
                    product(8, 0, 1000), new BigDecimal("10"), new BigDecimal("2"), 30, 1);
            ReplenishSuggestionVO slow = calculator.calculate(
                    product(8, 0, 1000), new BigDecimal("10"), new BigDecimal("2"), 30, 7);

            assertNotNull(fast, "到货 1 天、库存 8 低于补货点约 13，应该触发建议");
            assertNotNull(slow, "到货 7 天、库存 8 远低于补货点约 79，更应该触发建议");

            assertEquals(1, fast.getLeadTimeDays());
            assertEquals(7, slow.getLeadTimeDays());
            assertTrue(slow.getReorderPoint() > fast.getReorderPoint(),
                    "到货周期 7 天的补货点必须高于 1 天的，否则供应商差异没起作用");
        }

        @Test
        @DisplayName("安全库存按 √到货周期缩放，不是线性")
        void safetyStockScalesWithSqrt() {
            // 标准差 2：1 天 -> ceil(1.65×2×1) = 4；9 天 -> ceil(1.65×2×3) = 10
            ReplenishSuggestionVO oneDay = calculator.calculate(
                    product(0, 0, 1000), new BigDecimal("1"), new BigDecimal("2"), 30, 1);
            ReplenishSuggestionVO nineDays = calculator.calculate(
                    product(0, 0, 1000), new BigDecimal("1"), new BigDecimal("2"), 30, 9);

            assertNotNull(oneDay);
            assertNotNull(nineDays);
            assertEquals(4, oneDay.getSafetyStock(), "1 天安全库存 = ceil(1.65×2×1) = 4");
            assertEquals(10, nineDays.getSafetyStock(),
                    "9 天安全库存 = ceil(1.65×2×3) = 10；若为 36 说明误用了线性 ×9");
        }

        @Test
        @DisplayName("到货周期为 0 时按 1 天处理，补货点不会退化成 0")
        void zeroLeadTimeFallsBackToOne() {
            ReplenishSuggestionVO vo = calculator.calculate(
                    product(0, 0, 100), new BigDecimal("10"), BigDecimal.ZERO, 30, 0);

            assertNotNull(vo);
            assertEquals(1, vo.getLeadTimeDays(), "到货周期 0 应被兜底成 1 天");
            assertEquals(10, vo.getReorderPoint(),
                    "补货点应为日均 10 × 1 天 = 10，不能是 0（否则等于没货才补）");
        }
    }

    // ==================================================================
    //  是否需要补货
    // ==================================================================

    @Nested
    @DisplayName("是否触发补货建议")
    class Trigger {

        @Test
        @DisplayName("库存充足且高于补货点时不给建议")
        void noSuggestionWhenStockIsHealthy() {
            // 补货点 30，当前 500，上限 1000 -> 不该建议
            ReplenishSuggestionVO vo = calculator.calculate(
                    product(500, 0, 1000), new BigDecimal("10"), BigDecimal.ZERO, 30);
            assertNull(vo, "库存远高于补货点，不应给出补货建议");
        }

        @Test
        @DisplayName("库存跌破下限时即使高于补货点也要建议")
        void lowerBoundAloneCanTrigger() {
            // 日均 0.1 -> 补货点 1（含安全库存 0），当前 5 高于补货点，
            // 但下限是 10，跌破下限就必须补
            ReplenishSuggestionVO vo = calculator.calculate(
                    product(5, 10, 100), new BigDecimal("0.1"), BigDecimal.ZERO, 30);

            assertNotNull(vo, "跌破库存下限必须给建议，哪怕还在补货点之上");
            assertTrue(vo.getReason().contains("跌破库存下限"),
                    "理由里应说明跌破了下限，实际：" + vo.getReason());
        }

        @Test
        @DisplayName("断货时紧急程度为 HIGH 且理由明确")
        void outOfStockIsHighUrgency() {
            ReplenishSuggestionVO vo = calculator.calculate(
                    product(0, 0, 100), new BigDecimal("8"), new BigDecimal("2"), 30);

            assertNotNull(vo);
            assertEquals("HIGH", vo.getUrgency(), "断货必须是最紧急");
            assertTrue(vo.getReason().contains("断货"), "理由应点明已断货，实际：" + vo.getReason());
        }

        @Test
        @DisplayName("能支撑天数不足 3 天判为 HIGH，3~7 天判为 MEDIUM")
        void urgencyFollowsStockDays() {
            // 日均 10，当前 20 -> 可撑 2 天 -> HIGH
            ReplenishSuggestionVO high = calculator.calculate(
                    product(20, 0, 1000), new BigDecimal("10"), BigDecimal.ZERO, 30);
            assertNotNull(high);
            assertEquals("HIGH", high.getUrgency(), "可撑 2 天应为 HIGH");

            // 日均 10，当前 50 -> 可撑 5 天，但要先能触发建议：
            // 补货点是 30，50 高于补货点，必须靠下限 100 把它拉进来
            ReplenishSuggestionVO medium = calculator.calculate(
                    product(50, 100, 1000), new BigDecimal("10"), BigDecimal.ZERO, 30);
            assertNotNull(medium, "跌破下限 100 时应触发建议");
            assertEquals("MEDIUM", medium.getUrgency(), "可撑 5 天应为 MEDIUM");
        }

        @Test
        @DisplayName("高于补货点且未跌破下限时不给建议（可撑天数再多也不该补）")
        void healthyStockDaysDoesNotTrigger() {
            // 日均 10，当前 50 -> 可撑 5 天，但补货点只有 30，
            // 且没有设下限，属于「还够卖」的状态，不该打扰用户
            ReplenishSuggestionVO vo = calculator.calculate(
                    product(50, 0, 1000), new BigDecimal("10"), BigDecimal.ZERO, 30);
            assertNull(vo, "库存高于补货点且未设下限时，不应给出补货建议");
        }

        @Test
        @DisplayName("已经超过上限时不给补货建议（避免越补越压）")
        void noSuggestionWhenOverUpperBound() {
            // 上限 100，当前 200，补货点 30 -> 目标 100 - 当前 200 为负，不给建议
            ReplenishSuggestionVO vo = calculator.calculate(
                    product(200, 0, 100), new BigDecimal("10"), BigDecimal.ZERO, 30);
            assertNull(vo, "已超上限时补货量为负，应返回 null 而不是负数量");
        }
    }

    // ==================================================================
    //  统计工具：补齐零销量天
    // ==================================================================

    @Nested
    @DisplayName("日均与标准差统计")
    class Stats {

        @Test
        @DisplayName("缺销量的天必须按 0 补齐，否则日均会被严重高估")
        void missingDaysCountAsZero() {
            // 30 天里只有 3 天有销量，共 9 件
            // 正确：9 / 30 = 0.3；错误做法：9 / 3 = 3（高估 10 倍）
            List<Long> sparse = List.of(3L, 3L, 3L);
            ReplenishCalculator.Stats stats = calculator.computeStats(sparse, 30);

            assertEquals(0, new BigDecimal("0.3").compareTo(stats.avgDaily()),
                    "日均应为 9/30=0.3，若为 3 说明分母用了「有销量的天数」而不是统计窗口天数");
        }

        @Test
        @DisplayName("无销量时日均与标准差都是 0")
        void noSalesMeansZero() {
            ReplenishCalculator.Stats stats = calculator.computeStats(List.of(), 30);
            assertEquals(0, BigDecimal.ZERO.compareTo(stats.avgDaily()));
            assertEquals(0, BigDecimal.ZERO.compareTo(stats.stdDev()));
        }

        @Test
        @DisplayName("销量完全相同时标准差为 0")
        void constantSeriesHasZeroStdDev() {
            List<Long> constant = java.util.Collections.nCopies(10, 5L);
            ReplenishCalculator.Stats stats = calculator.computeStats(constant, 10);
            assertEquals(0, new BigDecimal("5").compareTo(stats.avgDaily()));
            assertEquals(0, BigDecimal.ZERO.compareTo(stats.stdDev()),
                    "每天都卖 5 件，标准差应为 0");
        }

        @Test
        @DisplayName("按日期铺开时缺失日期补 0，顺序正确")
        void toDailySeriesFillsGaps() {
            Map<String, Long> byDate = new LinkedHashMap<>();
            LocalDate start = LocalDate.of(2026, 10, 1);
            byDate.put("2026-10-01", 5L);
            byDate.put("2026-10-03", 7L);

            List<Long> series = calculator.toDailySeries(byDate, start, 4);

            assertEquals(List.of(5L, 0L, 7L, 0L), series,
                    "10-02 与 10-04 没有销量，应补 0 且保持日期顺序");
        }

        @Test
        @DisplayName("统计窗口起始日含今天，所以是 days-1 天前")
        void windowStartIncludesToday() {
            LocalDate today = LocalDate.of(2026, 10, 25);
            // 30 天窗口：10-25 往前数 29 天 = 09-26
            assertEquals(LocalDate.of(2026, 9, 26), calculator.windowStart(30, today),
                    "30 天窗口应覆盖今天，起点是 29 天前而不是 30 天前");
        }
    }
}
