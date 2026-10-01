package com.retail.erp.module.ai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** AI 补货分析请求 */
public record ReplenishAnalyzeRequest(

        @NotNull(message = "请选择商品")
        Long productId,

        /** 统计窗口天数，默认 30 */
        @Min(value = 7, message = "统计窗口至少 7 天，样本太少算出来的日均没有意义")
        @Max(value = 180, message = "统计窗口最多 180 天")
        Integer days
) {

    public int daysOrDefault() {
        return days == null ? 30 : days;
    }
}
