package com.retail.erp.module.basic.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 商品新增 / 修改入参
 *
 * 用 record 而不是 Entity 作为入参有两个原因：
 *   1. 客户端不能直接改 updateTime、deleted 这些不该由它控制的字段（防越权赋值）
 *   2. 校验规则写在边界处，Service 里无需再判空
 */
public record ProductSaveDTO(

        @NotBlank(message = "商品编码不能为空")
        @Size(max = 32, message = "商品编码不能超过 32 个字符")
        String code,

        @Size(max = 64, message = "条形码不能超过 64 个字符")
        String barcode,

        @NotBlank(message = "商品名称不能为空")
        @Size(max = 100, message = "商品名称不能超过 100 个字符")
        String name,

        @NotNull(message = "请选择商品分类")
        Long categoryId,

        @Size(max = 64, message = "规格不能超过 64 个字符")
        String spec,

        @NotBlank(message = "单位不能为空")
        @Size(max = 16, message = "单位不能超过 16 个字符")
        String unit,

        @NotNull(message = "参考进价不能为空")
        @DecimalMin(value = "0", message = "参考进价不能为负数")
        BigDecimal purchasePrice,

        @NotNull(message = "零售价不能为空")
        @DecimalMin(value = "0", message = "零售价不能为负数")
        BigDecimal salePrice,

        @NotNull(message = "库存上限不能为空")
        @Min(value = 0, message = "库存上限不能为负数")
        Integer stockUpper,

        @NotNull(message = "库存下限不能为空")
        @Min(value = 0, message = "库存下限不能为负数")
        Integer stockLower,

        @NotNull(message = "保质期天数不能为空")
        @Min(value = 0, message = "保质期天数不能为负数")
        Integer shelfLifeDays,

        /** 1 在售 0 停售，不传默认在售 */
        Integer status
) {

    public int statusOrDefault() {
        return status == null ? 1 : status;
    }
}
