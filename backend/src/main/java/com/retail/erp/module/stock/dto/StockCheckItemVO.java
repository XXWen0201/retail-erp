package com.retail.erp.module.stock.dto;

import lombok.Data;

/** 盘点明细返回对象 */
@Data
public class StockCheckItemVO {

    private Long id;
    private Long productId;
    private String productName;
    private String unit;
    private Long batchId;
    private String batchNo;

    /** 账面数量：建账那一刻从库存表抓的快照 */
    private Integer bookQuantity;
    private Integer actualQuantity;

    /** 差异 = 实盘 - 账面。正数是盘盈（多出来），负数是盘亏（少了） */
    private Integer diffQuantity;

    private String reason;
}
