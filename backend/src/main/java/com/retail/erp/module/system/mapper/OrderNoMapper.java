package com.retail.erp.module.system.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 单号序号查询
 *
 * 只干一件事：给定「前缀 + 日期」（如 PO20260930），返回库里已存在的最大流水号。
 * 由 OrderNoGenerator 在每个日期首次发号时调用，用来和历史数据对齐，避免撞号。
 *
 * 这里没法写成一条通用 SQL 是因为表名不能用绑定参数（会被当成字符串字面量），
 * 所以每张单据表各写一个方法 —— 一共 5 个，比动态拼接表名安全得多。
 */
@Mapper
public interface OrderNoMapper {

    @Select("""
            SELECT IFNULL(MAX(CAST(SUBSTRING(order_no, LENGTH(#{prefix}) + 1) AS UNSIGNED)), 0)
            FROM purchase_order
            WHERE order_no LIKE CONCAT(#{prefix}, '%')
            """)
    Long selectMaxPurchaseSuffix(@Param("prefix") String prefix);

    @Select("""
            SELECT IFNULL(MAX(CAST(SUBSTRING(order_no, LENGTH(#{prefix}) + 1) AS UNSIGNED)), 0)
            FROM sale_order
            WHERE order_no LIKE CONCAT(#{prefix}, '%')
            """)
    Long selectMaxSaleSuffix(@Param("prefix") String prefix);

    @Select("""
            SELECT IFNULL(MAX(CAST(SUBSTRING(order_no, LENGTH(#{prefix}) + 1) AS UNSIGNED)), 0)
            FROM return_order
            WHERE order_no LIKE CONCAT(#{prefix}, '%')
            """)
    Long selectMaxReturnSuffix(@Param("prefix") String prefix);

    @Select("""
            SELECT IFNULL(MAX(CAST(SUBSTRING(check_no, LENGTH(#{prefix}) + 1) AS UNSIGNED)), 0)
            FROM stock_check
            WHERE check_no LIKE CONCAT(#{prefix}, '%')
            """)
    Long selectMaxCheckSuffix(@Param("prefix") String prefix);

    @Select("""
            SELECT IFNULL(MAX(CAST(SUBSTRING(batch_no, LENGTH(#{prefix}) + 1) AS UNSIGNED)), 0)
            FROM stock_batch
            WHERE batch_no LIKE CONCAT(#{prefix}, '%')
            """)
    Long selectMaxBatchSuffix(@Param("prefix") String prefix);
}
