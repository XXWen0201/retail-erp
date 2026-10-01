package com.retail.erp.module.basic.controller;

import com.retail.erp.common.R;
import com.retail.erp.module.basic.dto.CategorySaveDTO;
import com.retail.erp.module.basic.dto.OptionVO;
import com.retail.erp.module.basic.entity.Category;
import com.retail.erp.module.basic.service.CategoryService;
import com.retail.erp.security.RequireRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "商品分类")
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "分类列表", description = "不分页，前端分类树/下拉直接使用")
    @GetMapping
    public R<List<Category>> list() {
        return R.ok(categoryService.listAll());
    }

    @Operation(summary = "分类下拉选项")
    @GetMapping("/options")
    public R<List<OptionVO>> options() {
        return R.ok(categoryService.options());
    }

    @Operation(summary = "新增分类")
    @PostMapping
    @RequireRole({"MANAGER"})
    public R<Long> create(@Valid @RequestBody CategorySaveDTO dto) {
        return R.ok(categoryService.create(dto));
    }

    @Operation(summary = "修改分类")
    @PutMapping("/{id}")
    @RequireRole({"MANAGER"})
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody CategorySaveDTO dto) {
        categoryService.update(id, dto);
        return R.ok();
    }

    @Operation(summary = "删除分类", description = "分类下有商品或子分类时返回 4003")
    @DeleteMapping("/{id}")
    @RequireRole({"MANAGER"})
    public R<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return R.ok();
    }
}
