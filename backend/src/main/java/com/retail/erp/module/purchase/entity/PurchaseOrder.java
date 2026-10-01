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
import java.time.LocalDateTime;
import lombok.Data;

/**
 * PurchaseOrder 实体
 *
 * ⚠️ 本文件由 tools/gen_entities.py 依据 db/schema.sql 自动生成，请勿手工修改。
 *    表结构变更后请重新执行生成脚本。
 */
@Data
@TableName("purchase_order")
public class PurchaseOrder implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("order_no")
    /** 采购单号 */
    private String orderNo;

    @TableField("supplier_id")
    private Long supplierId;

    @TableField("supplier_name")
    private String supplierName;

    @TableField("total_quantity")
    private Integer totalQuantity;

    @TableField("total_amount")
    private BigDecimal totalAmount;

    /** DRAFT/PENDING/FINISHED/CANCELED */
    private String status;

    @TableField("order_date")
    /** 采购日期 */
    private LocalDate orderDate;

    @TableField("receive_time")
    /** 实际入库时间 */
    private LocalDateTime receiveTime;

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
