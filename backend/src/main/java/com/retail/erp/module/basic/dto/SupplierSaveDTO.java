package com.retail.erp.module.basic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 供应商新增 / 修改入参 */
public record SupplierSaveDTO(

        @NotBlank(message = "供应商编号不能为空")
        @Size(max = 32, message = "供应商编号不能超过 32 个字符")
        String code,

        @NotBlank(message = "供应商名称不能为空")
        @Size(max = 100, message = "供应商名称不能超过 100 个字符")
        String name,

        @Size(max = 50, message = "联系人不能超过 50 个字符")
        String contact,

        @Size(max = 20, message = "联系电话不能超过 20 个字符")
        String phone,

        @Size(max = 255, message = "地址不能超过 255 个字符")
        String address,

        @Size(max = 255, message = "备注不能超过 255 个字符")
        String remark,

        Integer status
) {

    public int statusOrDefault() {
        return status == null ? 1 : status;
    }
}
