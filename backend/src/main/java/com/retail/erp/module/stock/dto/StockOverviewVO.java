package com.retail.erp.module.stock.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 库存总览卡片数据 */
@Data
public class StockOverviewVO {

    /** 商品种类数 */
    private long skuCount;

    /** 库存总件数 */
    private long totalQuantity;

    /** 库存成本总值（按移动加权平均成本计） */
    private BigDecimal totalCostValue = BigDecimal.ZERO;

    /** 低于下限的商品数 */
    private long lowStockCount;

    /** 零库存商品数 */
    private long outOfStockCount;

    /** 临期批次数 */
    private long nearExpiryBatchCount;

    /** 已过期批次数 */
    private long expiredBatchCount;
}
