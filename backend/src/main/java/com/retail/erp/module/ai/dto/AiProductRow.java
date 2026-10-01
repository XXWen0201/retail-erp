package com.retail.erp.module.ai.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 补货计算用：商品 + 当前库存 */
@Data
public class AiProductRow {

    private Long productId;
    private String productName;
    private String unit;

    private BigDecimal purchasePrice;
    private Integer stockUpper;
    private Integer stockLower;

    private Integer quantity;
    private BigDecimal avgCost;
}
