package com.retail.erp.module.report.mapper;

import com.retail.erp.module.report.dto.NameStatVO;
import com.retail.erp.module.report.dto.StockReportVO;
import com.retail.erp.module.report.dto.TrendPointVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * 报表聚合查询
 *
 * 全部聚合都放在数据库里做，不把明细捞到 Java 再统计：
 *   90 天 × 每天几十条销售明细 = 上万行。捞上来在内存里 group by，
 *   既慢又占内存，而且分页/排序还得再写一遍。让 MySQL 用它自己的索引
 *   和聚合能力做完再只回传几十行结果，是数量级的差别。
 *
 * 关于 `date` 这个别名：DATE 在 MySQL 里是类型关键字，
 * 当别名时必须加反引号，否则语法报错。
 */
@Mapper
public interface ReportMapper {

    // ==================================================================
    //  趋势
    // ==================================================================

    /** 销售趋势：按 groupBy 聚合实收金额、销量、单数、成本、毛利 */
    @Select("""
            SELECT DATE_FORMAT(order_date, #{dateFormat}) AS `date`,
                   IFNULL(SUM(pay_amount), 0)             AS amount,
                   IFNULL(SUM(total_quantity), 0)         AS quantity,
                   COUNT(*)                               AS orderCount,
                   IFNULL(SUM(total_cost), 0)             AS cost,
                   IFNULL(SUM(gross_profit), 0)           AS profit
            FROM sale_order
            WHERE deleted = 0 AND status = 'FINISHED'
              AND order_date BETWEEN #{start} AND #{end}
            GROUP BY DATE_FORMAT(order_date, #{dateFormat})
            ORDER BY `date` ASC
            """)
    List<TrendPointVO> selectSaleTrend(@Param("start") LocalDate start,
                                       @Param("end") LocalDate end,
                                       @Param("dateFormat") String dateFormat);

    /** 采购趋势：只统计已入库的单据，草稿和作废的不算真实采购 */
    @Select("""
            SELECT DATE_FORMAT(order_date, #{dateFormat}) AS `date`,
                   IFNULL(SUM(total_amount), 0)           AS amount,
                   IFNULL(SUM(total_quantity), 0)         AS quantity,
                   COUNT(*)                               AS orderCount
            FROM purchase_order
            WHERE deleted = 0 AND status = 'FINISHED'
              AND order_date BETWEEN #{start} AND #{end}
            GROUP BY DATE_FORMAT(order_date, #{dateFormat})
            ORDER BY `date` ASC
            """)
    List<TrendPointVO> selectPurchaseTrend(@Param("start") LocalDate start,
                                           @Param("end") LocalDate end,
                                           @Param("dateFormat") String dateFormat);

    /** 区间销售合计（工作台的今日/本月卡片也走这条） */
    @Select("""
            SELECT IFNULL(SUM(pay_amount), 0)     AS amount,
                   IFNULL(SUM(total_quantity), 0) AS quantity,
                   COUNT(*)                       AS orderCount,
                   IFNULL(SUM(total_cost), 0)     AS cost,
                   IFNULL(SUM(gross_profit), 0)   AS profit
            FROM sale_order
            WHERE deleted = 0 AND status = 'FINISHED'
              AND order_date BETWEEN #{start} AND #{end}
            """)
    TrendPointVO selectSaleSummary(@Param("start") LocalDate start,
                                   @Param("end") LocalDate end);

    // ==================================================================
    //  分组统计
    // ==================================================================

    /** 销售按分类：金额、销量、成本、毛利 */
    @Select("""
            SELECT c.name                                 AS categoryName,
                   IFNULL(SUM(i.quantity), 0)             AS quantity,
                   IFNULL(SUM(i.amount), 0)               AS amount,
                   IFNULL(SUM(i.cost_amount), 0)          AS cost,
                   IFNULL(SUM(i.amount - i.cost_amount), 0) AS profit
            FROM sale_order_item i
            JOIN sale_order o ON o.id = i.order_id
            JOIN product    p ON p.id = i.product_id
            LEFT JOIN category c ON c.id = p.category_id
            WHERE o.deleted = 0 AND o.status = 'FINISHED'
              AND o.order_date BETWEEN #{start} AND #{end}
            GROUP BY c.id, c.name
            ORDER BY amount DESC
            """)
    List<NameStatVO> selectSalesByCategory(@Param("start") LocalDate start,
                                           @Param("end") LocalDate end);

    /** 商品销量排行 */
    @Select("""
            SELECT i.product_id                          AS productId,
                   i.product_name                        AS productName,
                   IFNULL(SUM(i.quantity), 0)            AS quantity,
                   IFNULL(SUM(i.amount), 0)              AS amount,
                   IFNULL(SUM(i.cost_amount), 0)         AS cost,
                   IFNULL(SUM(i.amount - i.cost_amount), 0) AS profit
            FROM sale_order_item i
            JOIN sale_order o ON o.id = i.order_id
            WHERE o.deleted = 0 AND o.status = 'FINISHED'
              AND o.order_date BETWEEN #{start} AND #{end}
            GROUP BY i.product_id, i.product_name
            ORDER BY quantity DESC
            LIMIT #{limit}
            """)
    List<NameStatVO> selectTopProducts(@Param("start") LocalDate start,
                                       @Param("end") LocalDate end,
                                       @Param("limit") int limit);

    /** 采购按供应商 */
    @Select("""
            SELECT supplier_id                      AS supplierId,
                   supplier_name                    AS supplierName,
                   IFNULL(SUM(total_amount), 0)     AS amount,
                   IFNULL(SUM(total_quantity), 0)   AS quantity,
                   COUNT(*)                         AS orderCount
            FROM purchase_order
            WHERE deleted = 0 AND status = 'FINISHED'
              AND order_date BETWEEN #{start} AND #{end}
            GROUP BY supplier_id, supplier_name
            ORDER BY amount DESC
            """)
    List<NameStatVO> selectPurchaseBySupplier(@Param("start") LocalDate start,
                                              @Param("end") LocalDate end);

    /**
     * 库存按分类
     *
     * 成本值用「数量 × 移动加权平均成本」而不是进价：
     * 同一个商品多次进货价格不同，用进价会让库存金额长期偏离真实占用资金。
     */
    @Select("""
            SELECT c.name                                        AS categoryName,
                   COUNT(DISTINCT p.id)                          AS skuCount,
                   IFNULL(SUM(IFNULL(s.quantity, 0)), 0)         AS quantity,
                   IFNULL(SUM(IFNULL(s.quantity, 0) * IFNULL(s.avg_cost, 0)), 0) AS costValue
            FROM product p
            LEFT JOIN stock    s ON s.product_id = p.id
            LEFT JOIN category c ON c.id = p.category_id
            WHERE p.deleted = 0 AND p.status = 1
            GROUP BY c.id, c.name
            ORDER BY costValue DESC
            """)
    List<NameStatVO> selectStockByCategory();

    // ==================================================================
    //  明细清单
    // ==================================================================

    /** 低于库存下限的商品清单，gap 为建议补货量的下限 */
    @Select("""
            SELECT p.id                                  AS productId,
                   p.name                                AS productName,
                   p.unit                                AS unit,
                   IFNULL(s.quantity, 0)                 AS quantity,
                   p.stock_lower                         AS stockLower,
                   (p.stock_lower - IFNULL(s.quantity, 0)) AS gap
            FROM product p
            LEFT JOIN stock s ON s.product_id = p.id
            WHERE p.deleted = 0 AND p.status = 1
              AND p.stock_lower > 0
              AND IFNULL(s.quantity, 0) < p.stock_lower
            ORDER BY gap DESC
            """)
    List<StockReportVO.LowStockItem> selectLowStockList();

    /** 临期与已过期批次清单，daysToExpire 为负数表示已过期 */
    @Select("""
            SELECT b.id                                        AS batchId,
                   b.product_id                                AS productId,
                   p.name                                      AS productName,
                   p.unit                                      AS unit,
                   b.batch_no                                  AS batchNo,
                   b.stock_quantity                           AS stockQuantity,
                   b.expire_date                              AS expireDate,
                   DATEDIFF(b.expire_date, CURDATE())         AS daysToExpire
            FROM stock_batch b
            JOIN product p ON p.id = b.product_id
            WHERE b.status = 1
              AND b.stock_quantity > 0
              AND b.expire_date IS NOT NULL
              AND p.deleted = 0
              AND b.expire_date <= DATE_ADD(CURDATE(), INTERVAL #{nearExpiryDays} DAY)
            ORDER BY b.expire_date ASC
            """)
    List<StockReportVO.ExpiringItem> selectExpiringList(@Param("nearExpiryDays") int nearExpiryDays);
}
