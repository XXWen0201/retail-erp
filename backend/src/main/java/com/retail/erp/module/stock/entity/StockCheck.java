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
 * StockCheck 实体
 *
 * ⚠️ 本文件由 tools/gen_entities.py 依据 db/schema.sql 自动生成，请勿手工修改。
 *    表结构变更后请重新执行生成脚本。
 */
@Data
@TableName("stock_check")
public class StockCheck implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("check_no")
    /** 盘点单号 */
    private String checkNo;

    /** DRAFT/FINISHED/CANCELED */
    private String status;

    @TableField("check_date")
    private LocalDate checkDate;

    @TableField("total_diff_quantity")
    /** 差异总量（绝对值之和） */
    private Integer totalDiffQuantity;

    @TableField("diff_item_count")
    /** 有差异的商品数 */
    private Integer diffItemCount;

    @TableField("operator_id")
    private Long operatorId;

    @TableField("operator_name")
    private String operatorName;

    @TableField("finish_time")
    private LocalDateTime finishTime;

    private String remark;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
}
