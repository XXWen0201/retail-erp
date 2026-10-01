package com.retail.erp.module.stock.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** 库存流水分页查询条件 */
@Data
public class StockRecordQuery {

    private Long productId;

    /** 业务类型，见 BizTypeEnum，如 SALE_OUT */
    private String bizType;

    /** 来源单号，模糊匹配 */
    private String bizNo;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    @Min(value = 1, message = "页码从 1 开始")
    private Integer page = 1;

    @Min(value = 1, message = "每页至少 1 条")
    @Max(value = 500, message = "每页最多 500 条")
    private Integer size = 10;
}
