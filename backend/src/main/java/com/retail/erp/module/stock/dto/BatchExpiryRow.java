package com.retail.erp.module.stock.dto;

import lombok.Data;

import java.time.LocalDate;

/** 预警扫描用：临期/过期批次行 */
@Data
public class BatchExpiryRow {

    private Long batchId;
    private String batchNo;
    private Long productId;
    private String productName;
    private Integer stockQuantity;
    private LocalDate expireDate;
}
