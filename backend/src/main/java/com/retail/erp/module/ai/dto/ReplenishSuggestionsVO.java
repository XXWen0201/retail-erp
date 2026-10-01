package com.retail.erp.module.ai.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 补货建议列表返回 */
@Data
public class ReplenishSuggestionsVO {

    /** 用的什么算法，答辩时要能一句话讲清楚 */
    private String algorithm;

    /** 统计窗口天数 */
    private int days;

    private LocalDateTime generatedAt;

    private List<ReplenishSuggestionVO> items;
}
