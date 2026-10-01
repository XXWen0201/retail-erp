package com.retail.erp.module.returns.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** 退货单新增入参 */
public record ReturnSaveDTO(

        /** PURCHASE_RETURN 采购退货 / SALE_RETURN 销售退货 */
        @NotBlank(message = "请选择退货类型")
        String returnType,

        /** 关联的原单 id，可空。填了则校验退货量不超过原单可退量 */
        Long sourceOrderId,

        @NotNull(message = "请选择退货日期")
        LocalDate returnDate,

        @Size(max = 255, message = "退货原因不能超过 255 个字符")
        String reason,

        @NotEmpty(message = "请至少添加一条退货明细")
        @Valid
        List<ReturnItemDTO> items
) {
}
