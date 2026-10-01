package com.retail.erp.module.basic.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 分类新增 / 修改入参 */
public record CategorySaveDTO(

        @NotBlank(message = "分类名称不能为空")
        @Size(max = 50, message = "分类名称不能超过 50 个字符")
        String name,

        /** 父分类 id，0 表示顶级分类 */
        @NotNull(message = "父分类不能为空，顶级分类请传 0")
        Long parentId,

        @Min(value = 0, message = "排序号不能为负数")
        Integer sort,

        Integer status
) {

    public int sortOrDefault() {
        return sort == null ? 0 : sort;
    }

    public int statusOrDefault() {
        return status == null ? 1 : status;
    }
}
