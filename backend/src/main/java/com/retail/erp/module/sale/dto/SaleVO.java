package com.retail.erp.module.sale.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 销售单返回对象 */
@Data
public class SaleVO {

    private Long id;
    private String orderNo;
    private String customerName;

    private Integer totalQuantity;
    /** 应收金额 = 各明细 amount 之和 */
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    /** 实收金额 = 应收 - 优惠 */
    private BigDecimal payAmount;
    /** 出库成本合计，按各批次的进价算 */
    private BigDecimal totalCost;
    /** 毛利 = 实收 - 成本。优惠直接体现在毛利里，不再单独算 */
    private BigDecimal grossProfit;

    private String status;
    private String statusLabel;
    private LocalDate orderDate;
    private String operatorName;
    private String remark;

    private List<SaleItemVO> items;
}
