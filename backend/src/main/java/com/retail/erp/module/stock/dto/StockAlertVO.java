package com.retail.erp.module.stock.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 预警返回对象 */
@Data
public class StockAlertVO {

    private Long id;
    private Long productId;
    private String productName;
    private Long batchId;
    private String batchNo;

    private String alertType;
    private String alertTypeLabel;
    private String alertLevel;

    /** 当前值：库存类预警是库存数量，保质期类预警是「距到期天数」（负数表示已过期） */
    private Integer currentValue;
    private Integer thresholdValue;
    private LocalDate expireDate;

    private String status;
    private String statusLabel;
    private String handleRemark;
    private LocalDateTime handleTime;
    private String handleUser;
    private LocalDateTime createTime;
}
