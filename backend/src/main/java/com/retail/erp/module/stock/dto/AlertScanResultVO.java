package com.retail.erp.module.stock.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** 一次预警扫描的结果 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlertScanResultVO {

    /** 本次新产生的预警数 */
    private int createdCount;

    /** 已存在但数值有变化的预警数（只更新，不重复插入） */
    private int updatedCount;

    /** 因库存恢复正常而自动关闭的预警数 */
    private int closedCount;

    /** 当前未处理预警总数 */
    private long unhandledTotal;

    private LocalDateTime scanTime;

    /** 本次新产生的预警明细，前端扫完可以直接高亮展示 */
    private List<StockAlertVO> newAlerts;
}
