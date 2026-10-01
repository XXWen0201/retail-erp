package com.retail.erp.module.report.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 毛利统计 */
@Data
public class ProfitReportVO {

    private BigDecimal totalAmount = BigDecimal.ZERO;
    private BigDecimal totalCost = BigDecimal.ZERO;
    private BigDecimal totalProfit = BigDecimal.ZERO;

    /** 综合毛利率百分比 */
    private BigDecimal grossMargin = BigDecimal.ZERO;

    private List<TrendPointVO> trend;
    private List<NameStatVO> byCategory;
}
