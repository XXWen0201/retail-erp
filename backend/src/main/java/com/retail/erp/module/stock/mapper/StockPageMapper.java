package com.retail.erp.module.stock.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.retail.erp.module.stock.dto.BatchExpiryRow;
import com.retail.erp.module.stock.dto.ProductStockRow;
import com.retail.erp.module.stock.dto.StockBatchVO;
import com.retail.erp.module.stock.dto.StockCheckItemVO;
import com.retail.erp.module.stock.dto.StockOverviewVO;
import com.retail.erp.module.stock.dto.StockRecordVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * 库存查询专用 Mapper（联表 + 聚合）
 *
 * 为什么不复用自动生成的 Mapper：
 *   1. 自动生成的 Mapper 声明了「请勿手工修改」
 *   2. 这几条查询都要联表：批次要商品名、流水要商品名和批次号、总览要跨 3 张表聚合。
 *      用单表 Wrapper 只能拆成多次查询再在 Java 里拼，等于把 SQL 该干的活搬到内存里干
 */
@Mapper
public interface StockPageMapper {

    /**
     * 批次分页
     *
     * ORDER BY 里刻意把「无到期日」排在最后：
     * 不管理保质期的商品（文具、日用品）expire_date 是 NULL，
     * MySQL 默认会把 NULL 排在最前面，那样一打开批次页全是这些没有保质期的批次，
     * 真正需要关注的临期批次反而被挤到后面几页去了。
     */
    @Select("""
            <script>
            SELECT b.id, b.product_id, b.batch_no, b.purchase_order_id, b.production_date,
                   b.expire_date, b.init_quantity, b.out_quantity, b.stock_quantity,
                   b.cost_price, b.status,
                   p.name AS product_name, p.unit AS unit
            FROM stock_batch b
            LEFT JOIN product p ON p.id = b.product_id
            WHERE 1 = 1
            <if test="productId != null">
              AND b.product_id = #{productId}
            </if>
            <if test="expiringSoon != null and expiringSoon">
              AND b.expire_date IS NOT NULL AND b.stock_quantity &gt; 0
              AND b.expire_date &gt;= CURDATE()
              AND b.expire_date &lt;= DATE_ADD(CURDATE(), INTERVAL #{nearExpiryDays} DAY)
            </if>
            <if test="expired != null and expired">
              AND b.expire_date IS NOT NULL AND b.stock_quantity &gt; 0
              AND b.expire_date &lt; CURDATE()
            </if>
            ORDER BY (b.expire_date IS NULL) ASC, b.expire_date ASC, b.id ASC
            </script>
            """)
    IPage<StockBatchVO> selectBatchPage(IPage<StockBatchVO> page,
                                        @Param("productId") Long productId,
                                        @Param("expiringSoon") Boolean expiringSoon,
                                        @Param("expired") Boolean expired,
                                        @Param("nearExpiryDays") int nearExpiryDays);

    /** 库存流水分页：联商品取名称、联批次取批次号 */
    @Select("""
            <script>
            SELECT r.id, r.product_id, r.batch_id, r.biz_type, r.change_quantity,
                   r.before_quantity, r.after_quantity, r.unit_cost, r.biz_no, r.biz_id,
                   r.operator_name, r.remark, r.create_time,
                   p.name AS product_name, b.batch_no AS batch_no
            FROM stock_record r
            LEFT JOIN product     p ON p.id = r.product_id
            LEFT JOIN stock_batch b ON b.id = r.batch_id
            WHERE 1 = 1
            <if test="productId != null">
              AND r.product_id = #{productId}
            </if>
            <if test="bizType != null and bizType != ''">
              AND r.biz_type = #{bizType}
            </if>
            <if test="bizNo != null and bizNo != ''">
              AND r.biz_no LIKE CONCAT('%', #{bizNo}, '%')
            </if>
            <if test="startDate != null">
              AND r.create_time &gt;= #{startDate}
            </if>
            <if test="endDate != null">
              AND r.create_time &lt; DATE_ADD(#{endDate}, INTERVAL 1 DAY)
            </if>
            ORDER BY r.id DESC
            </script>
            """)
    IPage<StockRecordVO> selectRecordPage(IPage<StockRecordVO> page,
                                          @Param("productId") Long productId,
                                          @Param("bizType") String bizType,
                                          @Param("bizNo") String bizNo,
                                          @Param("startDate") LocalDate startDate,
                                          @Param("endDate") LocalDate endDate);

    /**
     * 库存总览（商品维度）
     *
     * 用 CASE WHEN 一次扫描算出「低于下限」和「零库存」两个指标，
     * 而不是发两条 COUNT 查询 —— 前者扫一遍表，后者扫两遍。
     */
    @Select("""
            SELECT COUNT(*)                                                    AS skuCount,
                   IFNULL(SUM(IFNULL(s.quantity, 0)), 0)                       AS totalQuantity,
                   IFNULL(SUM(IFNULL(s.quantity, 0) * IFNULL(s.avg_cost, 0)), 0) AS totalCostValue,
                   IFNULL(SUM(CASE WHEN p.stock_lower > 0
                                    AND IFNULL(s.quantity, 0) > 0
                                    AND IFNULL(s.quantity, 0) < p.stock_lower
                                   THEN 1 ELSE 0 END), 0)                      AS lowStockCount,
                   IFNULL(SUM(CASE WHEN IFNULL(s.quantity, 0) <= 0
                                   THEN 1 ELSE 0 END), 0)                      AS outOfStockCount
            FROM product p
            LEFT JOIN stock s ON s.product_id = p.id
            WHERE p.deleted = 0 AND p.status = 1
            """)
    StockOverviewVO selectStockOverview();

    /** 库存总览（批次维度：临期 / 过期） */
    @Select("""
            SELECT IFNULL(SUM(CASE WHEN expire_date IS NOT NULL
                                    AND stock_quantity > 0
                                    AND expire_date >= CURDATE()
                                    AND expire_date <= DATE_ADD(CURDATE(), INTERVAL #{nearExpiryDays} DAY)
                                   THEN 1 ELSE 0 END), 0) AS nearExpiryBatchCount,
                   IFNULL(SUM(CASE WHEN expire_date IS NOT NULL
                                    AND stock_quantity > 0
                                    AND expire_date < CURDATE()
                                   THEN 1 ELSE 0 END), 0) AS expiredBatchCount
            FROM stock_batch
            WHERE status = 1
            """)
    StockOverviewVO selectBatchOverview(@Param("nearExpiryDays") int nearExpiryDays);

    /** 查某个商品当前库存（压测前后对比用，直接读库绕过缓存，才是真实值） */
    @Select("SELECT IFNULL(quantity, 0) FROM stock WHERE product_id = #{productId}")
    Integer selectQuantityByProductId(@Param("productId") Long productId);

    /** 盘点明细（联商品取单位） */
    @Select("""
            SELECT i.id, i.product_id, i.product_name, i.batch_id, i.batch_no,
                   i.book_quantity, i.actual_quantity, i.diff_quantity, i.reason,
                   p.unit AS unit
            FROM stock_check_item i
            LEFT JOIN product p ON p.id = i.product_id
            WHERE i.check_id = #{checkId}
            ORDER BY i.id ASC
            """)
    List<StockCheckItemVO> selectCheckItems(@Param("checkId") Long checkId);

    /** 预警扫描：全部在售商品及其当前库存 */
    @Select("""
            SELECT p.id              AS product_id,
                   p.name            AS product_name,
                   p.unit            AS unit,
                   p.stock_lower     AS stock_lower,
                   p.stock_upper     AS stock_upper,
                   IFNULL(s.quantity, 0) AS quantity
            FROM product p
            LEFT JOIN stock s ON s.product_id = p.id
            WHERE p.deleted = 0 AND p.status = 1
            ORDER BY p.id ASC
            """)
    List<ProductStockRow> selectProductStockRows();

    /**
     * 预警扫描：临期与已过期批次
     *
     * 只查「还有剩余量」的批次 —— 已经卖光的批次再报警没有意义，
     * 只会让预警列表被历史空批次淹没。
     */
    @Select("""
            SELECT b.id             AS batch_id,
                   b.batch_no       AS batch_no,
                   b.product_id     AS product_id,
                   p.name           AS product_name,
                   b.stock_quantity AS stock_quantity,
                   b.expire_date    AS expire_date
            FROM stock_batch b
            JOIN product p ON p.id = b.product_id
            WHERE b.status = 1
              AND b.stock_quantity > 0
              AND b.expire_date IS NOT NULL
              AND p.deleted = 0
              AND b.expire_date <= DATE_ADD(CURDATE(), INTERVAL #{nearExpiryDays} DAY)
            ORDER BY b.expire_date ASC, b.id ASC
            """)
    List<BatchExpiryRow> selectExpiringBatches(@Param("nearExpiryDays") int nearExpiryDays);
}
