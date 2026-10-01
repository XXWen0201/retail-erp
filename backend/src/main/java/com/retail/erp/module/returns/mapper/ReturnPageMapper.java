package com.retail.erp.module.returns.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 退货模块专用查询
 */
@Mapper
public interface ReturnPageMapper {

    /**
     * 统计某原单某商品已被退的数量
     *
     * 用来实现「退货数量不超过原单可退量」的校验。
     * 注意要排除已作废的退货单 —— 作废的退货单对应的货已经还回去了，
     * 不能继续占用可退额度，否则退一次作废一次，额度就被吃光了。
     */
    @Select("""
            SELECT IFNULL(SUM(i.quantity), 0)
            FROM return_order_item i
            JOIN return_order o ON o.id = i.return_id
            WHERE o.deleted = 0
              AND o.status = 'FINISHED'
              AND o.return_type = #{returnType}
              AND o.source_order_id = #{sourceOrderId}
              AND i.product_id = #{productId}
            """)
    int sumReturnedQuantity(@Param("returnType") String returnType,
                            @Param("sourceOrderId") Long sourceOrderId,
                            @Param("productId") Long productId);
}
