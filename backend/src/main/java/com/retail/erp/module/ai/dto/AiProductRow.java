package com.retail.erp.module.ai.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 补货计算用：商品 + 当前库存 */
@Data
public class AiProductRow {

    private Long productId;
    private String productName;
    private String unit;

    /** 商品的主供应商，用于查该供应商的实际到货周期 */
    private Long supplierId;

    private BigDecimal purchasePrice;
    private Integer stockUpper;
    private Integer stockLower;

    private Integer quantity;
    private BigDecimal avgCost;
}
