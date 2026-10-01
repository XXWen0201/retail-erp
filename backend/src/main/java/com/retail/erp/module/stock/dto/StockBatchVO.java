package com.retail.erp.module.stock.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 批次展示对象
 *
 * 比实体多出 productName 与保质期推算字段：
 * 剩余天数是随「今天」变化的，绝不能存进数据库 —— 存了第二天就是错的，
 * 还得写定时任务去刷。这里每次查询实时算。
 */
@Data
public class StockBatchVO {

    private Long id;
    private Long productId;
    private String productName;
    private String unit;
    private String batchNo;
    private Long purchaseOrderId;
    private LocalDate productionDate;
    private LocalDate expireDate;
    private Integer initQuantity;
    private Integer outQuantity;
    private Integer stockQuantity;
    private BigDecimal costPrice;
    private Integer status;

    /** 距到期还有多少天，负数表示已过期 */
    private Long daysToExpire;

    /** NORMAL / NEAR_EXPIRY（临期）/ EXPIRED（已过期）/ NO_LIMIT（不管理保质期） */
    private String expireStatus;

    /**
     * 计算保质期状态
     *
     * @param nearExpiryDays 多少天内算临期，来自配置 app.alert.near-expiry-days
     */
    public void computeExpire(LocalDate today, int nearExpiryDays) {
        if (expireDate == null) {
            this.expireStatus = "NO_LIMIT";
            return;
        }
        this.daysToExpire = java.time.temporal.ChronoUnit.DAYS.between(today, expireDate);
        if (daysToExpire < 0) {
            this.expireStatus = "EXPIRED";
        } else if (daysToExpire <= nearExpiryDays) {
            this.expireStatus = "NEAR_EXPIRY";
        } else {
            this.expireStatus = "NORMAL";
        }
    }
}
