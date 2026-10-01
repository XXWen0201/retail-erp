package com.retail.erp.module.stock.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * StockAlert 实体
 *
 * ⚠️ 本文件由 tools/gen_entities.py 依据 db/schema.sql 自动生成，请勿手工修改。
 *    表结构变更后请重新执行生成脚本。
 */
@Data
@TableName("stock_alert")
public class StockAlert implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("product_id")
    private Long productId;

    @TableField("product_name")
    private String productName;

    @TableField("batch_id")
    private Long batchId;

    @TableField("batch_no")
    private String batchNo;

    @TableField("alert_type")
    /** LOW_STOCK/OVER_STOCK/NEAR_EXPIRY/EXPIRED/OUT_OF_STOCK */
    private String alertType;

    @TableField("alert_level")
    /** WARN/DANGER */
    private String alertLevel;

    @TableField("current_value")
    /** 当前值（库存量或剩余天数） */
    private Integer currentValue;

    @TableField("threshold_value")
    /** 阈值 */
    private Integer thresholdValue;

    @TableField("expire_date")
    private LocalDate expireDate;

    /** UNHANDLED/HANDLED/IGNORED */
    private String status;

    @TableField("handle_remark")
    private String handleRemark;

    @TableField("handle_time")
    private LocalDateTime handleTime;

    @TableField("handle_user")
    private String handleUser;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
