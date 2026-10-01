package com.retail.erp.module.stock.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/** 预警分页查询条件 */
@Data
public class StockAlertQuery {

    /** LOW_STOCK / OVER_STOCK / OUT_OF_STOCK / NEAR_EXPIRY / EXPIRED */
    private String alertType;

    /** WARN / DANGER */
    private String alertLevel;

    /** UNHANDLED / HANDLED / IGNORED */
    private String status;

    /** 商品名模糊匹配 */
    private String keyword;

    @Min(value = 1, message = "页码从 1 开始")
    private Integer page = 1;

    @Min(value = 1, message = "每页至少 1 条")
    @Max(value = 500, message = "每页最多 500 条")
    private Integer size = 10;
}
