package com.retail.erp.module.sale.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.io.Serializable;
import java.math.BigDecimal;
import lombok.Data;

/**
 * SaleOrderItem 实体
 *
 * ⚠️ 本文件由 tools/gen_entities.py 依据 db/schema.sql 自动生成，请勿手工修改。
 *    表结构变更后请重新执行生成脚本。
 */
@Data
@TableName("sale_order_item")
public class SaleOrderItem implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("order_id")
    private Long orderId;

    @TableField("product_id")
    private Long productId;

    @TableField("product_name")
    private String productName;

    @TableField("batch_id")
    /** 实际出库批次 */
    private Long batchId;

    @TableField("batch_no")
    private String batchNo;

    private Integer quantity;

    /** 销售单价 */
    private BigDecimal price;

    private BigDecimal amount;

    @TableField("cost_price")
    /** 该批次成本单价 */
    private BigDecimal costPrice;

    @TableField("cost_amount")
    private BigDecimal costAmount;
}
