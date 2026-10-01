package com.retail.erp.module.report.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 趋势数据点
 *
 * 采购、销售、毛利三条趋势线的字段不完全一样，但都塞进这一个对象里：
 * 用不到的字段保持 null，而全局 Jackson 配置了 non_null 不输出，
 * 前端拿到的 JSON 依然是干净的。
 * 好处是 ECharts 的配置可以完全复用一份，不用为每条线各写一套模型。
 */
@Data
public class TrendPointVO {

    /** 按日为 2026-09-01，按月为 2026-09 */
    private String date;

    /** 金额：销售为实收金额，采购为采购金额，毛利为实收金额 */
    private BigDecimal amount;

    private Long quantity;

    /** 单据数 */
    private Integer orderCount;

    /** 成本 */
    private BigDecimal cost;

    /** 毛利 */
    private BigDecimal profit;

    /** 毛利率百分比，例如 33.56 */
    private BigDecimal margin;
}
