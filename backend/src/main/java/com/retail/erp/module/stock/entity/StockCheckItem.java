package com.retail.erp.module.stock.entity;

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
 * StockCheckItem 实体
 *
 * ⚠️ 本文件由 tools/gen_entities.py 依据 db/schema.sql 自动生成，请勿手工修改。
 *    表结构变更后请重新执行生成脚本。
 */
@Data
@TableName("stock_check_item")
public class StockCheckItem implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("check_id")
    private Long checkId;

    @TableField("product_id")
    private Long productId;

    @TableField("product_name")
    private String productName;

    @TableField("batch_id")
    private Long batchId;

    @TableField("batch_no")
    private String batchNo;

    @TableField("book_quantity")
    /** 账面数量 */
    private Integer bookQuantity;

    @TableField("actual_quantity")
    /** 实盘数量 */
    private Integer actualQuantity;

    @TableField("diff_quantity")
    /** 差异 = 实盘 - 账面 */
    private Integer diffQuantity;

    /** 差异原因说明 */
    private String reason;

    @TableField("create_time")
    private LocalDateTime createTime;
}
