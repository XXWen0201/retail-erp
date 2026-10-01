package com.retail.erp.module.report.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.retail.erp.common.enums.AlertStatusEnum;
import com.retail.erp.common.enums.OrderStatusEnum;
import com.retail.erp.config.AppProperties;
import com.retail.erp.module.purchase.entity.PurchaseOrder;
import com.retail.erp.module.purchase.mapper.PurchaseOrderMapper;
import com.retail.erp.module.report.dto.DashboardVO;
import com.retail.erp.module.report.dto.NameStatVO;
import com.retail.erp.module.report.dto.ProfitReportVO;
import com.retail.erp.module.report.dto.PurchaseReportVO;
import com.retail.erp.module.report.dto.ReportQuery;
import com.retail.erp.module.report.dto.SalesReportVO;
import com.retail.erp.module.report.dto.StockReportVO;
import com.retail.erp.module.report.dto.TrendPointVO;
import com.retail.erp.module.report.mapper.ReportMapper;
import com.retail.erp.module.stock.dto.StockOverviewVO;
import com.retail.erp.module.stock.entity.StockAlert;
import com.retail.erp.module.stock.mapper.StockAlertMapper;
import com.retail.erp.module.stock.mapper.StockPageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * 报表服务
 *
 * 统一口径的三条约定，避免不同页面上的同一指标对不上：
 *   1. 只统计 status = FINISHED 的单据。草稿、作废都不算真实业务
 *   2. 销售金额一律用实收金额（pay_amount），不是应收
 *   3. 毛利 = 实收 - 出库成本，优惠已经体现在实收里
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    /** 毛利率保留 2 位，四大类报表统一用这个精度 */
    private static final int MARGIN_SCALE = 2;

    private final ReportMapper reportMapper;
    private final StockPageMapper stockPageMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final StockAlertMapper alertMapper;
    private final AppProperties props;

    // ==================================================================
    //  工作台
    // ==================================================================

    public DashboardVO dashboard() {
        LocalDate today = LocalDate.now();
        DashboardVO vo = new DashboardVO();

        // 今日
        TrendPointVO todaySummary = reportMapper.selectSaleSummary(today, today);
        if (todaySummary != null) {
            vo.setTodaySalesAmount(nvl(todaySummary.getAmount()));
            vo.setTodaySalesCount(todaySummary.getOrderCount() == null ? 0 : todaySummary.getOrderCount());
            vo.setTodayGrossProfit(nvl(todaySummary.getProfit()));
        }

        // 本月（从 1 号到今天，而不是「最近 30 天」—— 老板要的是自然月）
        LocalDate monthStart = today.withDayOfMonth(1);
        TrendPointVO monthSummary = reportMapper.selectSaleSummary(monthStart, today);
        if (monthSummary != null) {
            vo.setMonthSalesAmount(nvl(monthSummary.getAmount()));
            vo.setMonthGrossProfit(nvl(monthSummary.getProfit()));
        }

        // 待入库采购单：草稿 + 待入库
        vo.setPendingPurchaseCount(purchaseOrderMapper.selectCount(
                new QueryWrapper<PurchaseOrder>().in("status",
                        OrderStatusEnum.DRAFT.name(), OrderStatusEnum.PENDING.name())));

        vo.setUnhandledAlertCount(alertMapper.selectCount(
                new QueryWrapper<StockAlert>().eq("status", AlertStatusEnum.UNHANDLED.name())));

        StockOverviewVO stockOverview = stockPageMapper.selectStockOverview();
        if (stockOverview != null) {
            vo.setTotalStockValue(nvl(stockOverview.getTotalCostValue()));
        }

        // 注意第三个参数是 MySQL 的 DATE_FORMAT 格式串，不是 groupBy 字面量：
        // 传 "day" 会被当成格式串原样输出，所有行聚成一行
        vo.setSalesTrend(withSaleMargin(
                reportMapper.selectSaleTrend(today.minusDays(29), today, "%Y-%m-%d")));
        vo.setCategoryStock(reportMapper.selectStockByCategory());
        return vo;
    }

    // ==================================================================
    //  四类报表
    // ==================================================================

    public PurchaseReportVO purchase(ReportQuery query) {
        LocalDate start = query.startDateOrDefault();
        LocalDate end = query.endDateOrDefault();
        String fmt = query.dateFormat();

        PurchaseReportVO vo = new PurchaseReportVO();
        List<TrendPointVO> trend = reportMapper.selectPurchaseTrend(start, end, fmt);
        vo.setTrend(trend);
        vo.setTotalAmount(sum(trend, TrendPointVO::getAmount));
        vo.setTotalQuantity(sumQuantity(trend));
        vo.setOrderCount(trend.stream()
                .mapToLong(p -> p.getOrderCount() == null ? 0 : p.getOrderCount()).sum());
        vo.setBySupplier(reportMapper.selectPurchaseBySupplier(start, end));
        return vo;
    }

    public SalesReportVO sales(ReportQuery query) {
        LocalDate start = query.startDateOrDefault();
        LocalDate end = query.endDateOrDefault();
        String fmt = query.dateFormat();

        SalesReportVO vo = new SalesReportVO();
        List<TrendPointVO> trend = withSaleMargin(reportMapper.selectSaleTrend(start, end, fmt));
        vo.setTrend(trend);
        vo.setTotalAmount(sum(trend, TrendPointVO::getAmount));
        vo.setTotalQuantity(sumQuantity(trend));
        vo.setOrderCount(trend.stream()
                .mapToLong(p -> p.getOrderCount() == null ? 0 : p.getOrderCount()).sum());
        vo.setByCategory(withMargin(reportMapper.selectSalesByCategory(start, end)));
        vo.setTopProducts(withMargin(reportMapper.selectTopProducts(start, end, 10)));
        return vo;
    }

    public ProfitReportVO profit(ReportQuery query) {
        LocalDate start = query.startDateOrDefault();
        LocalDate end = query.endDateOrDefault();
        String fmt = query.dateFormat();

        ProfitReportVO vo = new ProfitReportVO();
        List<TrendPointVO> trend = withSaleMargin(reportMapper.selectSaleTrend(start, end, fmt));
        vo.setTrend(trend);
        vo.setTotalAmount(sum(trend, TrendPointVO::getAmount));
        vo.setTotalCost(sum(trend, TrendPointVO::getCost));
        vo.setTotalProfit(sum(trend, TrendPointVO::getProfit));
        vo.setGrossMargin(margin(vo.getTotalAmount(), vo.getTotalProfit()));
        vo.setByCategory(withMargin(reportMapper.selectSalesByCategory(start, end)));
        return vo;
    }

    public StockReportVO stock() {
        StockReportVO vo = new StockReportVO();

        StockOverviewVO overview = stockPageMapper.selectStockOverview();
        if (overview != null) {
            vo.setSkuCount(overview.getSkuCount());
            vo.setTotalQuantity(overview.getTotalQuantity());
            vo.setTotalCostValue(nvl(overview.getTotalCostValue()));
        }
        vo.setByCategory(reportMapper.selectStockByCategory());
        vo.setLowStockList(reportMapper.selectLowStockList());
        vo.setExpiringList(reportMapper.selectExpiringList(props.getAlert().getNearExpiryDays()));
        return vo;
    }

    // ==================================================================
    //  计算辅助
    // ==================================================================

    /**
     * 给销售趋势补上毛利率
     *
     * 毛利率让 SQL 算好还是 Java 算好？这里选 Java —— 因为「金额为 0 时毛利率记为 0」
     * 这类业务规则更适合写在代码里，SQL 里塞 IF 会让语句更难读，
     * 而且不同数据库的除零行为还不一致。
     */
    private List<TrendPointVO> withSaleMargin(List<TrendPointVO> points) {
        points.forEach(p -> p.setMargin(margin(p.getAmount(), p.getProfit())));
        return points;
    }

    private List<NameStatVO> withMargin(List<NameStatVO> rows) {
        rows.forEach(r -> r.setMargin(margin(r.getAmount(), r.getProfit())));
        return rows;
    }

    private BigDecimal margin(BigDecimal amount, BigDecimal profit) {
        if (amount == null || profit == null || amount.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return profit.multiply(BigDecimal.valueOf(100))
                .divide(amount, MARGIN_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal sum(List<TrendPointVO> points,
                           java.util.function.Function<TrendPointVO, BigDecimal> getter) {
        return points.stream()
                .map(getter)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private long sumQuantity(List<TrendPointVO> points) {
        return points.stream().mapToLong(p -> p.getQuantity() == null ? 0 : p.getQuantity()).sum();
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
