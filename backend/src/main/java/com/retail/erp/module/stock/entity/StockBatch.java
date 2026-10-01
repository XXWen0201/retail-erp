package com.retail.erp.module.stock.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * StockBatch 实体
 *
 * ⚠️ 本文件由 tools/gen_entities.py 依据 db/schema.sql 自动生成，请勿手工修改。
 *    表结构变更后请重新执行生成脚本。
 */
@Data
@TableName("stock_batch")
public class StockBatch implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("product_id")
    private Long productId;

    @TableField("batch_no")
    /** 批次号 */
    private String batchNo;

    @TableField("purchase_order_id")
    /** 来源采购单 id */
    private Long purchaseOrderId;

    @TableField("production_date")
    /** 生产日期 */
    private LocalDate productionDate;

    @TableField("expire_date")
    /** 到期日期 */
    private LocalDate expireDate;

    @TableField("init_quantity")
    /** 入库数量 */
    private Integer initQuantity;

    @TableField("out_quantity")
    /** 已出库数量 */
    private Integer outQuantity;

    @TableField("stock_quantity")
    /** 批次剩余数量 */
    private Integer stockQuantity;

    @TableField("cost_price")
    /** 批次进价 */
    private BigDecimal costPrice;

    /** 1有效 0已耗尽 */
    private Integer status;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
