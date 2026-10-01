package com.retail.erp.module.sale.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 销售开单入参 */
public record SaleSaveDTO(

        @Size(max = 50, message = "客户名称不能超过 50 个字符")
        String customerName,

        @NotNull(message = "请选择销售日期")
        LocalDate orderDate,

        /** 整单优惠金额，不能超过应收金额 */
        @DecimalMin(value = "0", message = "优惠金额不能为负数")
        BigDecimal discountAmount,

        @Size(max = 255, message = "备注不能超过 255 个字符")
        String remark,

        @NotEmpty(message = "请至少添加一条销售明细")
        @Valid
        List<SaleItemDTO> items
) {

    public String customerNameOrDefault() {
        return (customerName == null || customerName.isBlank()) ? "散客" : customerName;
    }

    public BigDecimal discountOrDefault() {
        return discountAmount == null ? BigDecimal.ZERO : discountAmount;
    }
}
