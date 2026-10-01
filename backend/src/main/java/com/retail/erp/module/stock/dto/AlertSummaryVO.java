package com.retail.erp.module.stock.dto;

import lombok.Data;

/** 预警汇总：各类型未处理数量，用于工作台徽标与预警页顶部标签 */
@Data
public class AlertSummaryVO {

    private long lowStockCount;
    private long overStockCount;
    private long outOfStockCount;
    private long nearExpiryCount;
    private long expiredCount;

    /** 未处理总数 */
    private long unhandledTotal;
}
