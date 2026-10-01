package com.retail.erp.module.returns.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 退货明细返回对象 */
@Data
public class ReturnItemVO {

    private Long id;
    private Long productId;
    private String productName;
    private Long batchId;
    private String batchNo;
    private Integer quantity;
    private BigDecimal price;
    private BigDecimal amount;
}
