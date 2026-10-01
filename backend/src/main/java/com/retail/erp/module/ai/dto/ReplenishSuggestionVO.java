package com.retail.erp.module.ai.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 单品补货建议 */
@Data
public class ReplenishSuggestionVO {

    private Long productId;
    private String productName;
    private String unit;

    private Integer currentStock;
    private Integer stockLower;
    private Integer stockUpper;

    /** 近 N 天日均销量 */
    private BigDecimal avgDailySales;

    /** 近 N 天周均销量，门店按周补货时更好用 */
    private BigDecimal avgWeeklySales;

    /** 安全库存：应对日销量波动预留的缓冲量 */
    private Integer safetyStock;

    /** 供应商到货周期（天） */
    private Integer leadTimeDays;

    /** 补货点：库存跌到这个值就该下单了 */
    private Integer reorderPoint;

    /** 建议补货数量 */
    private Integer suggestQuantity;

    /** 预计采购金额 */
    private BigDecimal estCost;

    /** 当前库存还能卖多少天 */
    private BigDecimal stockDays;

    /** HIGH 紧急 / MEDIUM 关注 / LOW 正常 */
    private String urgency;

    /** 人类可读的推荐理由 */
    private String reason;
}
