package com.retail.erp.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置
 *
 * 三个插件的职责：
 *   1. 分页插件       —— 让 IPage 查询自动拼 LIMIT，不写手写分页 SQL
 *   2. 乐观锁插件     —— 让 @Version 字段在 updateById 时自动带上 version 条件，
 *                        这是本项目库存扣减一致性的第一道防线
 *   3. 防全表更新插件 —— 拦截没有 where 条件的 update/delete，避免误操作清库
 *
 * 拦截器顺序有讲究：分页必须在最后，否则其他插件改写 SQL 时会绕过分页。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 乐观锁：作用于实体上标注了 @Version 的字段
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        // 防止误写全表：没有 where 的 update/delete 直接抛异常
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());

        // 分页插件，务必放在最后
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(500L);   // 单页上限，防止前端传 size=999999 拖垮数据库
        pagination.setOverflow(false);  // 页码越界时返回空列表而不是第一页，避免误导
        interceptor.addInnerInterceptor(pagination);

        return interceptor;
    }
}
