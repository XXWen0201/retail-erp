package com.retail.erp.module.sale.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** 销售明细入参 */
public record SaleItemDTO(

        @NotNull(message = "请选择销售商品")
        Long productId,

        @NotNull(message = "销售数量不能为空")
        @Min(value = 1, message = "销售数量至少为 1")
        Integer quantity,

        @NotNull(message = "销售单价不能为空")
        @DecimalMin(value = "0", message = "销售单价不能为负数")
        BigDecimal price
) {
}
