package com.retail.erp.module.basic.controller;

import com.retail.erp.common.PageResult;
import com.retail.erp.common.R;
import com.retail.erp.module.basic.dto.OptionVO;
import com.retail.erp.module.basic.dto.SupplierQuery;
import com.retail.erp.module.basic.dto.SupplierSaveDTO;
import com.retail.erp.module.basic.entity.Supplier;
import com.retail.erp.module.basic.service.SupplierService;
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

@Tag(name = "供应商")
@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    @Operation(summary = "供应商分页查询")
    @GetMapping
    public R<PageResult<Supplier>> page(@Validated SupplierQuery query) {
        return R.ok(supplierService.page(query));
    }

    @Operation(summary = "供应商下拉选项")
    @GetMapping("/options")
    public R<List<OptionVO>> options() {
        return R.ok(supplierService.options());
    }

    @Operation(summary = "供应商详情")
    @GetMapping("/{id}")
    public R<Supplier> detail(@PathVariable Long id) {
        return R.ok(supplierService.detail(id));
    }

    @Operation(summary = "新增供应商")
    @PostMapping
    @RequireRole({"MANAGER"})
    public R<Long> create(@Valid @RequestBody SupplierSaveDTO dto) {
        return R.ok(supplierService.create(dto));
    }

    @Operation(summary = "修改供应商")
    @PutMapping("/{id}")
    @RequireRole({"MANAGER"})
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody SupplierSaveDTO dto) {
        supplierService.update(id, dto);
        return R.ok();
    }

    @Operation(summary = "删除供应商")
    @DeleteMapping("/{id}")
    @RequireRole({"MANAGER"})
    public R<Void> delete(@PathVariable Long id) {
        supplierService.delete(id);
        return R.ok();
    }
}
