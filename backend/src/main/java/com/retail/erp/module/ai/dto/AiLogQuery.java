package com.retail.erp.module.ai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/** AI 调用记录查询条件 */
@Data
public class AiLogQuery {

    /** REPLENISH 补货分析 / CHAT 智能问答 */
    private String logType;

    @Min(value = 1, message = "页码从 1 开始")
    private Integer page = 1;

    @Min(value = 1, message = "每页至少 1 条")
    @Max(value = 200, message = "每页最多 200 条")
    private Integer size = 10;
}
