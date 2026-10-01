package com.retail.erp.module.basic.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.retail.erp.common.BizException;
import com.retail.erp.common.ErrorCode;
import com.retail.erp.module.basic.dto.CategorySaveDTO;
import com.retail.erp.module.basic.dto.OptionVO;
import com.retail.erp.module.basic.entity.Category;
import com.retail.erp.module.basic.entity.Product;
import com.retail.erp.module.basic.mapper.CategoryMapper;
import com.retail.erp.module.basic.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 商品分类服务
 *
 * 分类是商品的强依赖项，所以删除前必须检查引用。
 * 若直接删掉，商品表里的 category_id 会变成悬空外键，
 * 商品列表上「分类」一列就全是空白 —— 这类数据坏了很难被发现和修复。
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryMapper categoryMapper;
    private final ProductMapper productMapper;

    public List<Category> listAll() {
        return categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId));
    }

    /** 下拉选项：只返回启用中的分类 */
    public List<OptionVO> options() {
        return categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                        .eq(Category::getStatus, 1)
                        .orderByAsc(Category::getSort))
                .stream()
                .map(c -> new OptionVO(c.getId(), c.getName()))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(CategorySaveDTO dto) {
        checkNameDuplicate(dto.name(), null);
        checkParentExists(dto.parentId());

        Category entity = new Category();
        entity.setName(dto.name());
        entity.setParentId(dto.parentId());
        entity.setSort(dto.sortOrDefault());
        entity.setStatus(dto.statusOrDefault());
        categoryMapper.insert(entity);
        return entity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, CategorySaveDTO dto) {
        Category exist = categoryMapper.selectById(id);
        if (exist == null) {
            throw BizException.notFound("分类");
        }
        if (id.equals(dto.parentId())) {
            throw BizException.badRequest("父分类不能是自己");
        }
        checkNameDuplicate(dto.name(), id);
        checkParentExists(dto.parentId());

        exist.setName(dto.name());
        exist.setParentId(dto.parentId());
        exist.setSort(dto.sortOrDefault());
        exist.setStatus(dto.statusOrDefault());
        categoryMapper.updateById(exist);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (categoryMapper.selectById(id) == null) {
            throw BizException.notFound("分类");
        }
        Long productCount = productMapper.selectCount(Wrappers.<Product>lambdaQuery()
                .eq(Product::getCategoryId, id));
        if (productCount != null && productCount > 0) {
            throw new BizException(ErrorCode.ORDER_STATUS_ILLEGAL,
                    "该分类下还有 " + productCount + " 个商品，请先移走商品再删除分类");
        }
        Long childCount = categoryMapper.selectCount(Wrappers.<Category>lambdaQuery()
                .eq(Category::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BizException(ErrorCode.ORDER_STATUS_ILLEGAL, "请先删除该分类下的子分类");
        }
        categoryMapper.deleteById(id);
    }

    // ------------------------------------------------------------------

    private void checkNameDuplicate(String name, Long excludeId) {
        Long count = categoryMapper.selectCount(Wrappers.<Category>lambdaQuery()
                .eq(Category::getName, name)
                .ne(excludeId != null, Category::getId, excludeId));
        if (count != null && count > 0) {
            throw BizException.duplicate("分类【" + name + "】已存在");
        }
    }

    private void checkParentExists(Long parentId) {
        if (parentId == null || parentId == 0L) {
            return;
        }
        if (categoryMapper.selectById(parentId) == null) {
            throw BizException.notFound("父分类");
        }
    }
}
