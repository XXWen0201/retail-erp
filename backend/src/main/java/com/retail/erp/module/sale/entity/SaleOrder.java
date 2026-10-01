package com.retail.erp.module.sale.entity;

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
 * SaleOrder 实体
 *
 * ⚠️ 本文件由 tools/gen_entities.py 依据 db/schema.sql 自动生成，请勿手工修改。
 *    表结构变更后请重新执行生成脚本。
 */
@Data
@TableName("sale_order")
public class SaleOrder implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("order_no")
    /** 销售单号 */
    private String orderNo;

    @TableField("customer_name")
    private String customerName;

    @TableField("total_quantity")
    private Integer totalQuantity;

    @TableField("total_amount")
    /** 应收金额 */
    private BigDecimal totalAmount;

    @TableField("discount_amount")
    /** 优惠金额 */
    private BigDecimal discountAmount;

    @TableField("pay_amount")
    /** 实收金额 */
    private BigDecimal payAmount;

    @TableField("total_cost")
    /** 出库成本合计 */
    private BigDecimal totalCost;

    @TableField("gross_profit")
    /** 毛利 = 实收 - 成本 */
    private BigDecimal grossProfit;

    /** DRAFT/FINISHED/CANCELED */
    private String status;

    @TableField("order_date")
    private LocalDate orderDate;

    @TableField("operator_id")
    private Long operatorId;

    @TableField("operator_name")
    private String operatorName;

    private String remark;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
}
