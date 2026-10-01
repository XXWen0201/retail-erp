package com.retail.erp.module.basic.entity;

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
 * Product 实体
 *
 * ⚠️ 本文件由 tools/gen_entities.py 依据 db/schema.sql 自动生成，请勿手工修改。
 *    表结构变更后请重新执行生成脚本。
 */
@Data
@TableName("product")
public class Product implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商品编码 */
    private String code;

    /** 条形码 */
    private String barcode;

    /** 商品名称 */
    private String name;

    @TableField("category_id")
    /** 分类 id */
    private Long categoryId;

    /** 规格 */
    private String spec;

    /** 单位 */
    private String unit;

    @TableField("purchase_price")
    /** 参考进价 */
    private BigDecimal purchasePrice;

    @TableField("sale_price")
    /** 零售价 */
    private BigDecimal salePrice;

    @TableField("stock_upper")
    /** 库存上限，0=不限 */
    private Integer stockUpper;

    @TableField("stock_lower")
    /** 库存下限，触发低库存预警 */
    private Integer stockLower;

    @TableField("shelf_life_days")
    /** 保质期天数，0=不管理 */
    private Integer shelfLifeDays;

    /** 1在售 0停售 */
    private Integer status;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
}
