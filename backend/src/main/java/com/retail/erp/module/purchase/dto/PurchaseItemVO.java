package com.retail.erp.module.purchase.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 采购明细返回对象 */
@Data
public class PurchaseItemVO {

    private Long id;
    private Long productId;
    private String productName;
    private Integer quantity;
    private BigDecimal price;
    private BigDecimal amount;

    /** 入库后回填的批次信息；未入库时为空 */
    private String batchNo;
    private LocalDate productionDate;
    private LocalDate expireDate;
}
