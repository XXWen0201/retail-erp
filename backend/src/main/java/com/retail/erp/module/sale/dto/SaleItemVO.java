package com.retail.erp.module.sale.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 销售明细返回对象
 *
 * 注意：一个商品可能对应多条明细。
 * 因为出库要按 FEFO 扣批次，同一个商品如果跨了两个批次，
 * 就会拆成两条明细分别挂到各自的批次上 —— 这样成本才能精确到批次进价。
 * 前端展示时同一商品出现两行是正常的，代表「这批货里两批的成本不同」。
 */
@Data
public class SaleItemVO {

    private Long id;
    private Long productId;
    private String productName;
    private Long batchId;
    private String batchNo;
    private Integer quantity;
    private BigDecimal price;
    private BigDecimal amount;
    private BigDecimal costPrice;
    private BigDecimal costAmount;
}
