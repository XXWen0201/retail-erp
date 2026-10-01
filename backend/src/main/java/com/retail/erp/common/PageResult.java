package com.retail.erp.common;

import lombok.Data;

import java.util.List;
import java.util.function.Function;

/**
 * 统一分页结果
 *
 * 前端表格组件统一按 records / total / current / size 取值。
 *
 * @param <T> 行数据类型
 */
@Data
public class PageResult<T> {

    /** 当前页数据 */
    private List<T> records;

    /** 总记录数 */
    private long total;

    /** 当前页码，从 1 开始 */
    private long current;

    /** 每页条数 */
    private long size;

    /** 总页数 */
    private long pages;

    public static <T> PageResult<T> of(List<T> records, long total, long current, long size) {
        PageResult<T> r = new PageResult<>();
        r.records = records;
        r.total = total;
        r.current = current;
        r.size = size;
        r.pages = size <= 0 ? 0 : (total + size - 1) / size;
        return r;
    }

    public static <T> PageResult<T> empty(long current, long size) {
        return of(List.of(), 0, current, size);
    }

    /** 从 MyBatis-Plus 的 IPage 转换 */
    public static <T> PageResult<T> of(com.baomidou.mybatisplus.core.metadata.IPage<T> page) {
        return of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 行数据类型转换，例如 Entity -> VO */
    public <R> PageResult<R> map(Function<T, R> mapper) {
        return of(records.stream().map(mapper).toList(), total, current, size);
    }
}
