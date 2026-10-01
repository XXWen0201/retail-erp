package com.retail.erp.module.basic.controller;

import com.retail.erp.common.PageResult;
import com.retail.erp.common.R;
import com.retail.erp.module.basic.dto.OptionVO;
import com.retail.erp.module.basic.dto.ProductQuery;
import com.retail.erp.module.basic.dto.ProductSaveDTO;
import com.retail.erp.module.basic.dto.ProductVO;
import com.retail.erp.module.basic.service.ProductService;
import com.retail.erp.security.RequireRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "商品")
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "商品分页查询", description = "支持 keyword / categoryId / status / lowStockOnly")
    @GetMapping
    public R<PageResult<ProductVO>> page(@Validated ProductQuery query) {
        return R.ok(productService.page(query));
    }

    @Operation(summary = "商品下拉选项")
    @GetMapping("/options")
    public R<List<OptionVO>> options() {
        return R.ok(productService.options());
    }

    @Operation(summary = "商品详情", description = "含实时库存与移动加权平均成本")
    @GetMapping("/{id}")
    public R<ProductVO> detail(@PathVariable Long id) {
        return R.ok(productService.detail(id));
    }

    @Operation(summary = "新增商品", description = "同时初始化一条 0 库存记录")
    @PostMapping
    @RequireRole({"MANAGER"})
    public R<Long> create(@Valid @RequestBody ProductSaveDTO dto) {
        return R.ok(productService.create(dto));
    }

    @Operation(summary = "修改商品", description = "注意：不能通过本接口修改库存，库存只走采购/销售/盘点")
    @PutMapping("/{id}")
    @RequireRole({"MANAGER"})
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody ProductSaveDTO dto) {
        productService.update(id, dto);
        return R.ok();
    }

    @Operation(summary = "删除商品", description = "仍有库存时不允许删除")
    @DeleteMapping("/{id}")
    @RequireRole({"MANAGER"})
    public R<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return R.ok();
    }
}
