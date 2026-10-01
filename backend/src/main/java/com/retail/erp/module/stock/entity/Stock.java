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
 * Stock 实体
 *
 * ⚠️ 本文件由 tools/gen_entities.py 依据 db/schema.sql 自动生成，请勿手工修改。
 *    表结构变更后请重新执行生成脚本。
 */
@Data
@TableName("stock")
public class Stock implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("product_id")
    /** 商品 id */
    private Long productId;

    /** 当前可用库存 */
    private Integer quantity;

    @TableField("locked_quantity")
    /** 锁定库存（已下单未出库） */
    private Integer lockedQuantity;

    @TableField("avg_cost")
    /** 移动加权平均成本 */
    private BigDecimal avgCost;

    @Version
    /** 乐观锁版本号 */
    private Integer version;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
