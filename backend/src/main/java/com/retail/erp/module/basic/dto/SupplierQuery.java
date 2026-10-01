package com.retail.erp.module.basic.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/** 供应商列表查询条件 */
@Data
public class SupplierQuery {

    /** 关键字，匹配供应商名称 / 编号 / 联系人 */
    private String keyword;

    /** 1 启用 0 停用，不传查全部 */
    private Integer status;

    @Min(value = 1, message = "页码从 1 开始")
    private Integer page = 1;

    @Min(value = 1, message = "每页至少 1 条")
    @Max(value = 500, message = "每页最多 500 条")
    private Integer size = 10;
}
