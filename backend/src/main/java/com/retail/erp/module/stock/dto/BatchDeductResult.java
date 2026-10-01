package com.retail.erp.module.stock.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 一次出库操作实际扣掉的某个批次
 *
 * 一个销售明细要的数量，可能要跨多个批次才凑够（FEFO 先扣最早到期的），
 * 所以出库结果是「批次明细列表」，而不是单个批次。
 * 这样每一条销售明细都能精确挂到批次上，退货时才能原路退回原批次，
 * 成本核算也才能精确到批次进价。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchDeductResult {

    private Long batchId;
    private String batchNo;

    /** 本批次实际扣减数量 */
    private Integer quantity;

    /** 本批次进价，用于计算该笔出库成本 */
    private BigDecimal costPrice;

    /** 本批次扣减后的剩余量 */
    private Integer remainQuantity;
}
