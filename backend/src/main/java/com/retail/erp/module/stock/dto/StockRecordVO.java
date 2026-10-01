package com.retail.erp.module.stock.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存流水展示对象
 *
 * batchNo、productName、bizTypeLabel 这三个字段库存流水表里都没有，
 * 全部在查询时补出来：
 *   batchNo     —— 联 stock_batch 取，流水表只存 batch_id
 *   productName —— 联 product 取，避免商品改名后历史流水跟着变
 *   bizTypeLabel—— 由 BizTypeEnum 翻译，是展示层的事，不该落库
 */
@Data
public class StockRecordVO {

    private Long id;
    private Long productId;
    private String productName;
    private Long batchId;
    private String batchNo;

    private String bizType;
    private String bizTypeLabel;

    /** 带符号：入库为正、出库为负 */
    private Integer changeQuantity;
    private Integer beforeQuantity;
    private Integer afterQuantity;

    private BigDecimal unitCost;
    private String bizNo;
    private Long bizId;
    private String operatorName;
    private String remark;
    private LocalDateTime createTime;
}
