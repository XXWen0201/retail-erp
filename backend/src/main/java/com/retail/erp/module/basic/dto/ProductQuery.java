package com.retail.erp.module.basic.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 商品列表查询条件
 *
 * 用普通类而不是 record：字段多且都可选，需要默认值，record 反而不方便。
 */
@Data
public class ProductQuery {

    /** 关键字，匹配商品名称 / 编码 / 条形码 */
    private String keyword;

    private Long categoryId;

    /** 1 在售 0 停售，不传查全部 */
    private Integer status;

    /** 只看低于库存下限的商品，用于「待补货」快捷筛选 */
    private Boolean lowStockOnly;

    @Min(value = 1, message = "页码从 1 开始")
    private Integer page = 1;

    @Min(value = 1, message = "每页至少 1 条")
    @Max(value = 500, message = "每页最多 500 条")
    private Integer size = 10;
}
