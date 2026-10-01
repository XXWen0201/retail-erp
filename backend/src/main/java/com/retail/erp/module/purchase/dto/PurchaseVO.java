package com.retail.erp.module.purchase.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 采购单返回对象 */
@Data
public class PurchaseVO {

    private Long id;
    private String orderNo;
    private Long supplierId;
    private String supplierName;
    private Integer totalQuantity;
    private BigDecimal totalAmount;

    private String status;
    /** 状态中文标签，前端直接渲染，不用自己维护一份枚举映射 */
    private String statusLabel;

    private LocalDate orderDate;
    private LocalDateTime receiveTime;
    private String operatorName;
    private String remark;

    /** 列表页不带明细（省流量），详情页带 */
    private List<PurchaseItemVO> items;
}
