package com.retail.erp.module.report.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 按维度聚合的统计行
 *
 * 一份模型覆盖「按分类」「按供应商」「按商品排行」三种分组 ——
 * 它们的分组键不同，但数值口径完全一致（数量、金额、成本、毛利），
 * 拆成三个类只会带来三份重复的字段定义。
 */
@Data
public class NameStatVO {

    /** 按分类分组时使用 */
    private String categoryName;

    /** 按供应商分组时使用 */
    private Long supplierId;
    private String supplierName;

    /** 按商品排行时使用 */
    private Long productId;
    private String productName;

    /** 库存统计专用：该分组下的商品种数 */
    private Long skuCount;

    private Long quantity;
    private BigDecimal amount;
    private BigDecimal cost;
    private BigDecimal profit;
    private BigDecimal margin;

    /** 成本金额（库存统计里叫 costValue，语义更贴切） */
    private BigDecimal costValue;
}
