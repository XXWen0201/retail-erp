package com.retail.erp.module.returns.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** 退货明细入参 */
public record ReturnItemDTO(

        @NotNull(message = "请选择退货商品")
        Long productId,

        @NotNull(message = "退货数量不能为空")
        @Min(value = 1, message = "退货数量至少为 1")
        Integer quantity,

        @NotNull(message = "退货单价不能为空")
        @DecimalMin(value = "0", message = "退货单价不能为负数")
        BigDecimal price,

        /**
         * 采购退货时可指定退哪一批。
         * 不填则由系统按 FEFO（先到期先出）自动挑批次 —— 优先把临期货退给供应商，
         * 这比硬性要求操作员手选批次更省事，也更符合门店「先清临期」的实际做法。
         */
        Long batchId
) {
}
