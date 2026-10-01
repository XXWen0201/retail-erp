package com.retail.erp.module.report.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 库存统计 */
@Data
public class StockReportVO {

    private long skuCount;
    private long totalQuantity;
    private BigDecimal totalCostValue = BigDecimal.ZERO;

    private List<NameStatVO> byCategory;
    private List<LowStockItem> lowStockList;
    private List<ExpiringItem> expiringList;

    /** 低于库存下限的商品 */
    @Data
    public static class LowStockItem {
        private Long productId;
        private String productName;
        private String unit;
        private Integer quantity;
        private Integer stockLower;
        /** 距离下限还差多少，即建议至少补多少 */
        private Integer gap;
    }

    /** 临期或已过期的批次 */
    @Data
    public static class ExpiringItem {
        private Long batchId;
        private Long productId;
        private String productName;
        private String unit;
        private String batchNo;
        private Integer stockQuantity;
        private LocalDate expireDate;
        /** 负数表示已过期多少天 */
        private Long daysToExpire;
    }
}
