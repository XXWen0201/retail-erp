package com.retail.erp.module.stock.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * StockRecord 实体
 *
 * ⚠️ 本文件由 tools/gen_entities.py 依据 db/schema.sql 自动生成，请勿手工修改。
 *    表结构变更后请重新执行生成脚本。
 */
@Data
@TableName("stock_record")
public class StockRecord implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("product_id")
    private Long productId;

    @TableField("batch_id")
    private Long batchId;

    @TableField("biz_type")
    /** 业务类型，见 BizTypeEnum */
    private String bizType;

    @TableField("change_quantity")
    /** 变动数量（带符号） */
    private Integer changeQuantity;

    @TableField("before_quantity")
    /** 变动前库存 */
    private Integer beforeQuantity;

    @TableField("after_quantity")
    /** 变动后库存 */
    private Integer afterQuantity;

    @TableField("unit_cost")
    private BigDecimal unitCost;

    @TableField("biz_no")
    /** 来源单号 */
    private String bizNo;

    @TableField("biz_id")
    /** 来源单据 id */
    private Long bizId;

    @TableField("operator_id")
    private Long operatorId;

    @TableField("operator_name")
    private String operatorName;

    private String remark;

    @TableField("create_time")
    private LocalDateTime createTime;
}
