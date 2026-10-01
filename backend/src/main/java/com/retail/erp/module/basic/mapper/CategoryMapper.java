package com.retail.erp.module.basic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.retail.erp.module.basic.entity.Category;
import org.apache.ibatis.annotations.Mapper;

/**
 * Category 实体 Mapper
 *
 * ⚠️ 本文件由 tools/gen_entities.py 自动生成，请勿手工修改。
 *    复杂查询请写在 Service 里用 LambdaQueryWrapper 组装，或在本接口中新增 @Select 方法。
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
}
