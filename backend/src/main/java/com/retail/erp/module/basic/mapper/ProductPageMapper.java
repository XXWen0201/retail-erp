package com.retail.erp.module.basic.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.retail.erp.module.basic.dto.ProductQuery;
import com.retail.erp.module.basic.dto.ProductVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 商品联表分页查询
 *
 * 为什么单独建一个 Mapper 而不是塞进自动生成的 ProductMapper：
 *   1. 自动生成的 Mapper 声明了「请勿手工修改」，改它会在下次重新生成时被覆盖
 *   2. 这条查询需要 LEFT JOIN 分类表（取分类名）和库存表（取实时库存），
 *      用一个 SQL 取全而不是「查商品 → 循环查库存」（N+1），
 *      30 个商品会从 31 次查询降到 1 次
 */
@Mapper
public interface ProductPageMapper {

    /**
     * 商品分页查询
     *
     * 注意「只看低于下限」这个条件必须写在 SQL 里而不是查出后再用 Java 过滤：
     * 后者会让分页总数失真 —— 第 1 页显示 8 条、total 却写着 30，
     * 翻到第 3 页全是空的。
     */
    @Select("""
            <script>
            SELECT p.id, p.code, p.barcode, p.name, p.category_id, c.name AS category_name,
                   p.spec, p.unit, p.purchase_price, p.sale_price,
                   p.stock_upper, p.stock_lower, p.shelf_life_days, p.status,
                   IFNULL(s.quantity, 0)  AS stock_quantity,
                   IFNULL(s.avg_cost, 0)  AS avg_cost
            FROM product p
            LEFT JOIN category c ON c.id = p.category_id AND c.deleted = 0
            LEFT JOIN stock    s ON s.product_id = p.id
            WHERE p.deleted = 0
            <if test="q.keyword != null and q.keyword != ''">
              AND (p.name LIKE CONCAT('%', #{q.keyword}, '%')
                OR p.code LIKE CONCAT('%', #{q.keyword}, '%')
                OR p.barcode LIKE CONCAT('%', #{q.keyword}, '%'))
            </if>
            <if test="q.categoryId != null">
              AND p.category_id = #{q.categoryId}
            </if>
            <if test="q.status != null">
              AND p.status = #{q.status}
            </if>
            <if test="q.lowStockOnly != null and q.lowStockOnly">
              AND p.stock_lower &gt; 0 AND IFNULL(s.quantity, 0) &lt; p.stock_lower
            </if>
            ORDER BY p.id ASC
            </script>
            """)
    IPage<ProductVO> selectProductPage(IPage<ProductVO> page, @Param("q") ProductQuery query);
}
