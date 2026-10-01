package com.retail.erp.module.report.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 工作台汇总 */
@Data
public class DashboardVO {

    private BigDecimal todaySalesAmount = BigDecimal.ZERO;
    private long todaySalesCount;
    private BigDecimal todayGrossProfit = BigDecimal.ZERO;

    private BigDecimal monthSalesAmount = BigDecimal.ZERO;
    private BigDecimal monthGrossProfit = BigDecimal.ZERO;

    /** 待入库的采购单数（草稿 + 待入库） */
    private long pendingPurchaseCount;

    /** 未处理预警数 */
    private long unhandledAlertCount;

    private BigDecimal totalStockValue = BigDecimal.ZERO;

    /** 近 30 天销售趋势 */
    private java.util.List<TrendPointVO> salesTrend;

    /** 各分类库存分布 */
    private java.util.List<NameStatVO> categoryStock;
}
