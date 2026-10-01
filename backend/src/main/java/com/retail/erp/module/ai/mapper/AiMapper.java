package com.retail.erp.module.ai.mapper;

import com.retail.erp.module.ai.dto.AiProductRow;
import com.retail.erp.module.ai.dto.DailySalesRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * AI 模块取数
 *
 * 补货算法需要「按商品按天的销量序列」来算标准差，而不是只要一个总量。
 * 所以这里返回的是明细序列，由 Java 侧算均值与波动 ——
 * 用一条 SQL 把标准差也算出来当然可行，但 SQL 里写 STDDEV 之后
 * 「没销量的那些天要按 0 计入」这件事就很容易被漏掉，算出来的波动会偏小。
 */
@Mapper
public interface AiMapper {

    /** 全部在售商品及其当前库存、上下限、进价 */
    @Select("""
            SELECT p.id              AS productId,
                   p.name            AS productName,
                   p.unit            AS unit,
                   p.purchase_price  AS purchasePrice,
                   p.stock_upper     AS stockUpper,
                   p.stock_lower     AS stockLower,
                   IFNULL(s.quantity, 0) AS quantity,
                   IFNULL(s.avg_cost, 0) AS avgCost
            FROM product p
            LEFT JOIN stock s ON s.product_id = p.id
            WHERE p.deleted = 0 AND p.status = 1
            ORDER BY p.id ASC
            """)
    List<AiProductRow> selectProductRows();

    /** 指定商品在时间窗口内的按天销量 */
    @Select("""
            SELECT i.product_id                     AS productId,
                   DATE_FORMAT(o.order_date, '%Y-%m-%d') AS statDate,
                   SUM(i.quantity)                  AS quantity
            FROM sale_order_item i
            JOIN sale_order o ON o.id = i.order_id
            WHERE o.deleted = 0 AND o.status = 'FINISHED'
              AND o.order_date BETWEEN #{start} AND #{end}
            GROUP BY i.product_id, DATE_FORMAT(o.order_date, '%Y-%m-%d')
            """)
    List<DailySalesRow> selectDailySales(@Param("start") LocalDate start,
                                         @Param("end") LocalDate end);

    /** 按名称或编码模糊搜索商品及其库存，供 AI 工具回答「某某商品还有多少」 */
    @Select("""
            SELECT p.id              AS productId,
                   p.name            AS productName,
                   p.unit            AS unit,
                   p.purchase_price  AS purchasePrice,
                   p.stock_upper     AS stockUpper,
                   p.stock_lower     AS stockLower,
                   IFNULL(s.quantity, 0) AS quantity,
                   IFNULL(s.avg_cost, 0) AS avgCost
            FROM product p
            LEFT JOIN stock s ON s.product_id = p.id
            WHERE p.deleted = 0
              AND (p.name LIKE CONCAT('%', #{keyword}, '%')
                OR p.code LIKE CONCAT('%', #{keyword}, '%'))
            ORDER BY p.id ASC
            LIMIT #{limit}
            """)
    List<AiProductRow> searchProductStock(@Param("keyword") String keyword,
                                          @Param("limit") int limit);
}
