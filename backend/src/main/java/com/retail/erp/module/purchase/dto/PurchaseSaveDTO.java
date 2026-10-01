package com.retail.erp.module.purchase.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** 采购单新增 / 修改入参 */
public record PurchaseSaveDTO(

        @NotNull(message = "请选择供应商")
        Long supplierId,

        @NotNull(message = "请选择采购日期")
        LocalDate orderDate,

        @Size(max = 255, message = "备注不能超过 255 个字符")
        String remark,

        /** @Valid 不能漏：少了它，List 里每个元素的校验注解都不会生效 */
        @NotEmpty(message = "请至少添加一条采购明细")
        @Valid
        List<PurchaseItemDTO> items
) {
}
