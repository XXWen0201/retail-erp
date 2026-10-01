package com.retail.erp.module.purchase.controller;

import com.retail.erp.common.PageResult;
import com.retail.erp.common.R;
import com.retail.erp.module.purchase.dto.PurchaseQuery;
import com.retail.erp.module.purchase.dto.PurchaseSaveDTO;
import com.retail.erp.module.purchase.dto.PurchaseVO;
import com.retail.erp.module.purchase.service.PurchaseService;
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

@Tag(name = "采购管理", description = "采购单录入 / 入库 / 作废")
@RestController
@RequestMapping("/api/purchases")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseService purchaseService;

    @Operation(summary = "采购单分页查询")
    @GetMapping
    public R<PageResult<PurchaseVO>> page(@Validated PurchaseQuery query) {
        return R.ok(purchaseService.page(query));
    }

    @Operation(summary = "采购单详情", description = "含明细与入库批次信息")
    @GetMapping("/{id}")
    public R<PurchaseVO> detail(@PathVariable Long id) {
        return R.ok(purchaseService.detail(id));
    }

    @Operation(summary = "新建采购单", description = "保存为草稿，此时不影响库存")
    @PostMapping
    @RequireRole({"MANAGER"})
    public R<Long> create(@Valid @RequestBody PurchaseSaveDTO dto) {
        return R.ok(purchaseService.create(dto));
    }

    @Operation(summary = "修改采购单", description = "仅草稿状态可改")
    @PutMapping("/{id}")
    @RequireRole({"MANAGER"})
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody PurchaseSaveDTO dto) {
        purchaseService.update(id, dto);
        return R.ok();
    }

    @Operation(summary = "采购入库",
            description = "生成库存批次（含生产日期/到期日期）、增加总库存、写入库存流水")
    @PostMapping("/{id}/receive")
    @RequireRole({"MANAGER"})
    public R<Void> receive(@PathVariable Long id) {
        purchaseService.receive(id);
        return R.ok();
    }

    @Operation(summary = "作废采购单", description = "已入库的单据不能作废，请走采购退货")
    @PostMapping("/{id}/cancel")
    @RequireRole({"MANAGER"})
    public R<Void> cancel(@PathVariable Long id) {
        purchaseService.cancel(id);
        return R.ok();
    }

    @Operation(summary = "删除采购单", description = "仅草稿状态可删")
    @DeleteMapping("/{id}")
    @RequireRole({"MANAGER"})
    public R<Void> delete(@PathVariable Long id) {
        purchaseService.delete(id);
        return R.ok();
    }
}
