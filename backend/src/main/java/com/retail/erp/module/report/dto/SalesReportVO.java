package com.retail.erp.module.report.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 销售统计 */
@Data
public class SalesReportVO {

    private BigDecimal totalAmount = BigDecimal.ZERO;
    private long totalQuantity;
    private long orderCount;

    private List<TrendPointVO> trend;
    private List<NameStatVO> byCategory;
    private List<NameStatVO> topProducts;
}
