package com.retail.erp.module.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 库存智能问答请求 */
public record ChatRequest(

        @NotBlank(message = "请输入要问的问题")
        @Size(max = 500, message = "问题长度不能超过 500 个字符")
        String question
) {
}
