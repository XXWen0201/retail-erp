package com.retail.erp.module.stock.dto;

import lombok.Data;

/** 预警扫描用：商品库存行 */
@Data
public class ProductStockRow {

    private Long productId;
    private String productName;
    private String unit;

    /** 库存下限，0 表示不设限 */
    private Integer stockLower;

    /** 库存上限，0 表示不设限 */
    private Integer stockUpper;

    private Integer quantity;
}
