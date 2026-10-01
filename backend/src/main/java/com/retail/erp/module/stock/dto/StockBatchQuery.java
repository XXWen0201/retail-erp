package com.retail.erp.module.stock.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/** 批次分页查询条件 */
@Data
public class StockBatchQuery {

    private Long productId;

    /** true = 只看临期批次（默认 30 天内到期） */
    private Boolean expiringSoon;

    /** true = 只看已过期批次 */
    private Boolean expired;

    @Min(value = 1, message = "页码从 1 开始")
    private Integer page = 1;

    @Min(value = 1, message = "每页至少 1 条")
    @Max(value = 500, message = "每页最多 500 条")
    private Integer size = 10;
}
