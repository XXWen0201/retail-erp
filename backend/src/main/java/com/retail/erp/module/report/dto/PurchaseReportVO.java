package com.retail.erp.module.report.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 采购统计 */
@Data
public class PurchaseReportVO {

    private BigDecimal totalAmount = BigDecimal.ZERO;
    private long totalQuantity;
    private long orderCount;

    private List<TrendPointVO> trend;
    private List<NameStatVO> bySupplier;
}
