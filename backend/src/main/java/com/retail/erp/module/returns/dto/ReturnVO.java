package com.retail.erp.module.returns.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 退货单返回对象 */
@Data
public class ReturnVO {

    private Long id;
    private String orderNo;

    private String returnType;
    private String returnTypeLabel;

    private Long sourceOrderId;
    private String sourceOrderNo;
    private Long partnerId;
    private String partnerName;

    private Integer totalQuantity;
    private BigDecimal totalAmount;

    private String status;
    private String statusLabel;
    private LocalDate returnDate;
    private String operatorName;
    private String reason;

    private List<ReturnItemVO> items;
}
