package com.retail.erp.module.ai.dto;

import lombok.Data;

import java.util.List;

/** 库存智能问答结果 */
@Data
public class ChatResponse {

    private String answer;

    private String model;

    /** 模型本次自主调用了哪些库存查询工具，用于向使用者证明「答案是基于真实数据的」 */
    private List<String> toolsUsed;

    private int costMs;

    /** AI 不可用时为 true，answer 是本地兜底提示 */
    private boolean degraded;
}
