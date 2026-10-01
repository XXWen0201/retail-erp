package com.retail.erp.module.system.entity;

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
 * RefreshToken 实体
 *
 * ⚠️ 本文件由 tools/gen_entities.py 依据 db/schema.sql 自动生成，请勿手工修改。
 *    表结构变更后请重新执行生成脚本。
 */
@Data
@TableName("refresh_token")
public class RefreshToken implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("token_id")
    /** 令牌唯一标识 */
    private String tokenId;

    @TableField("user_id")
    private Long userId;

    private String username;

    @TableField("expire_time")
    private LocalDateTime expireTime;

    private Integer revoked;

    @TableField("create_time")
    private LocalDateTime createTime;
}
