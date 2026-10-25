package com.retail.erp.module.ai.dto;

import lombok.Data;

/**
 * 供应商实际到货周期统计
 *
 * 为什么不直接在 SQL 里算好一个平均值就完事：
 *   还要带上样本量。样本只有 1 单时算出来的「平均到货周期」毫无意义，
 *   但如果不告诉调用方样本量，调用方就只能盲信那个数字 ——
 *   于是补货算法会拿一个偶发值当规律用。
 *   带上 sampleCount 之后，ReplenishService 可以据此决定「信这个值还是退回默认值」。
 */
@Data
public class SupplierLeadTimeRow {

    private Long supplierId;

    private String supplierName;

    /** 参与统计的已完成采购单数量（只算 receive_time 非空的） */
    private Integer sampleCount;

    /** 平均到货天数，保留 1 位小数 */
    private Double avgLeadDays;
}
