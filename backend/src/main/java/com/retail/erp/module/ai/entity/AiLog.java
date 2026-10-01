package com.retail.erp.module.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * AiLog 实体
 *
 * ⚠️ 本文件由 tools/gen_entities.py 依据 db/schema.sql 自动生成，请勿手工修改。
 *    表结构变更后请重新执行生成脚本。
 */
@Data
@TableName("ai_log")
public class AiLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("log_type")
    /** REPLENISH 补货建议 / CHAT 智能问答 */
    private String logType;

    @TableField("product_id")
    private Long productId;

    private String title;

    /** 用户提问 / 提示词 */
    private String question;

    /** 模型回答 */
    private String answer;

    private String model;

    @TableField("prompt_tokens")
    private Integer promptTokens;

    @TableField("completion_tokens")
    private Integer completionTokens;

    @TableField("cost_ms")
    /** 耗时(毫秒) */
    private Integer costMs;

    @TableField("operator_name")
    private String operatorName;

    @TableField("create_time")
    private LocalDateTime createTime;
}
