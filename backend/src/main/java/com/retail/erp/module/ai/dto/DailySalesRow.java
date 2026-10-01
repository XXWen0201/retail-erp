package com.retail.erp.module.ai.dto;

import lombok.Data;

/** 补货计算用：某个商品某一天的销量 */
@Data
public class DailySalesRow {

    private Long productId;

    /** yyyy-MM-dd */
    private String statDate;

    private Long quantity;
}
