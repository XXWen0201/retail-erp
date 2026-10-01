package com.retail.erp.module.stock.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 录入实盘数入参 */
public record StockCheckItemUpdateDTO(

        @NotEmpty(message = "请至少提交一条盘点明细")
        @Valid
        List<Item> items
) {

    public record Item(

            /** 盘点明细 id（不是商品 id） */
            @NotNull(message = "盘点明细 id 不能为空")
            Long id,

            @NotNull(message = "实盘数量不能为空")
            @Min(value = 0, message = "实盘数量不能为负数")
            Integer actualQuantity,

            /** 差异原因。盘亏时必填更利于追溯，这里只限制长度不强求 */
            @Size(max = 255, message = "差异原因不能超过 255 个字符")
            String reason
    ) {
    }
}
