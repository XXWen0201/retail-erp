package com.retail.erp.module.basic.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品列表 / 详情返回对象
 *
 * 比实体多出 categoryName 与实时库存字段：前端表格直接展示，避免前端二次请求。
 * 库存是高频变化的数据，所以单独从 stock 表取，而不是冗余进 product 表 —— 冗余会造成双写不一致。
 */
@Data
public class ProductVO {

    private Long id;
    private String code;
    private String barcode;
    private String name;
    private Long categoryId;
    private String categoryName;
    private String spec;
    private String unit;
    private BigDecimal purchasePrice;
    private BigDecimal salePrice;
    private Integer stockUpper;
    private Integer stockLower;
    private Integer shelfLifeDays;
    private Integer status;

    // ---- 来自 stock 表 ----
    private Integer stockQuantity;
    private BigDecimal avgCost;

    /**
     * 库存状态，前端据此上色：
     *   OUT     零库存（红）
     *   LOW     低于下限（橙）
     *   OVER    高于上限（蓝）
     *   NORMAL  正常（绿）
     */
    private String stockStatus;

    /**
     * 计算库存状态
     *
     * 判定顺序刻意是 OUT → LOW → OVER → NORMAL：
     * 断货是最严重的情况，必须优先判定。否则一个「下限 0、上限 100」的商品
     * 在库存为 0 时会被判成 NORMAL，预警就漏了。
     *
     * 上限/下限为 0 表示「不设限制」，此时不参与判定。
     */
    public void computeStockStatus() {
        int qty = stockQuantity == null ? 0 : stockQuantity;
        if (qty <= 0) {
            this.stockStatus = "OUT";
        } else if (stockLower != null && stockLower > 0 && qty < stockLower) {
            this.stockStatus = "LOW";
        } else if (stockUpper != null && stockUpper > 0 && qty > stockUpper) {
            this.stockStatus = "OVER";
        } else {
            this.stockStatus = "NORMAL";
        }
    }
}
