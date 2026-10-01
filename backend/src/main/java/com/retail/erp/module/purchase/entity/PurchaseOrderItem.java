package com.retail.erp.module.purchase.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

/**
 * PurchaseOrderItem 实体
 *
 * ⚠️ 本文件由 tools/gen_entities.py 依据 db/schema.sql 自动生成，请勿手工修改。
 *    表结构变更后请重新执行生成脚本。
 */
@Data
@TableName("purchase_order_item")
public class PurchaseOrderItem implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("order_id")
    private Long orderId;

    @TableField("product_id")
    private Long productId;

    @TableField("product_name")
    private String productName;

    /** 采购数量 */
    private Integer quantity;

    /** 采购单价 */
    private BigDecimal price;

    /** 金额 */
    private BigDecimal amount;

    @TableField("batch_no")
    /** 入库批次号 */
    private String batchNo;

    @TableField("production_date")
    private LocalDate productionDate;

    @TableField("expire_date")
    private LocalDate expireDate;
}
