package com.retail.erp.module.ai.dto;

import lombok.Data;

/** AI 补货分析结果 */
@Data
public class ReplenishAnalyzeVO {

    private Long productId;
    private String productName;

    /** 模型生成的分析文字；degraded=true 时是本地规则生成的兜底文案 */
    private String analysis;

    private String model;

    /**
     * 是否降级返回
     *
     * AI 调用失败（欠费、网络不通、限流）时为 true，此时 analysis 由本地规则拼出，
     * 内容依然可用，只是不如模型写得细致。前端据此提示「AI 暂不可用，已展示本地分析」。
     * 这样 AI 服务挂了也不会让整个补货功能变成白屏。
     */
    private boolean degraded;
}
