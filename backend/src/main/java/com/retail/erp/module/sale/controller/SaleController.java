package com.retail.erp.module.sale.controller;

import com.retail.erp.common.PageResult;
import com.retail.erp.common.R;
import com.retail.erp.module.sale.dto.SaleQuery;
import com.retail.erp.module.sale.dto.SaleSaveDTO;
import com.retail.erp.module.sale.dto.SaleVO;
import com.retail.erp.module.sale.service.SaleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "销售管理", description = "开单出库 / 作废回滚")
@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SaleController {

    private final SaleService saleService;

    @Operation(summary = "销售单分页查询")
    @GetMapping
    public R<PageResult<SaleVO>> page(@Validated SaleQuery query) {
        return R.ok(saleService.page(query));
    }

    @Operation(summary = "销售单详情", description = "含明细、出库批次与成本")
    @GetMapping("/{id}")
    public R<SaleVO> detail(@PathVariable Long id) {
        return R.ok(saleService.detail(id));
    }

    @Operation(summary = "开单并出库",
            description = "保存即出库：按 FEFO（先到期先出）扣减批次库存，"
                    + "任一明细库存不足则整单回滚。库存不足返回 4001")
    @PostMapping
    public R<Long> create(@Valid @RequestBody SaleSaveDTO dto) {
        return R.ok(saleService.create(dto));
    }

    @Operation(summary = "作废销售单", description = "按原批次精确回滚库存")
    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        saleService.cancel(id);
        return R.ok();
    }

    @Operation(summary = "删除销售单", description = "需先作废并回滚库存")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        saleService.delete(id);
        return R.ok();
    }
}
