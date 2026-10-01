package com.retail.erp.module.purchase.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采购明细入参
 *
 * productionDate / expireDate / batchNo 都可以不填：
 *   - 填了生产日期且商品配置了保质期 → 后端自动推算到期日
 *   - 都不填 → 不管理保质期的商品（文具、日用品）也能正常入库
 */
public record PurchaseItemDTO(

        @NotNull(message = "请选择采购商品")
        Long productId,

        @NotNull(message = "采购数量不能为空")
        @Min(value = 1, message = "采购数量至少为 1")
        Integer quantity,

        @NotNull(message = "采购单价不能为空")
        @DecimalMin(value = "0", message = "采购单价不能为负数")
        BigDecimal price,

        LocalDate productionDate,

        LocalDate expireDate,

        @Size(max = 64, message = "批次号不能超过 64 位")
        String batchNo
) {
}
