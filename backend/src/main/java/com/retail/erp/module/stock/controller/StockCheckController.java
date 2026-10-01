package com.retail.erp.module.stock.controller;

import com.retail.erp.common.PageResult;
import com.retail.erp.common.R;
import com.retail.erp.module.stock.dto.StockCheckCreateDTO;
import com.retail.erp.module.stock.dto.StockCheckItemUpdateDTO;
import com.retail.erp.module.stock.dto.StockCheckQuery;
import com.retail.erp.module.stock.dto.StockCheckVO;
import com.retail.erp.module.stock.service.StockCheckService;
import com.retail.erp.security.RequireRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "库存盘点", description = "建账 / 录入实盘数 / 提交差异调整")
@RestController
@RequestMapping("/api/stock-checks")
@RequiredArgsConstructor
public class StockCheckController {

    private final StockCheckService stockCheckService;

    @Operation(summary = "盘点单分页查询")
    @GetMapping
    public R<PageResult<StockCheckVO>> page(@Validated StockCheckQuery query) {
        return R.ok(stockCheckService.page(query));
    }

    @Operation(summary = "盘点单详情", description = "含每条明细的账面数、实盘数、差异与原因")
    @GetMapping("/{id}")
    public R<StockCheckVO> detail(@PathVariable Long id) {
        return R.ok(stockCheckService.detail(id));
    }

    @Operation(summary = "新建盘点单",
            description = "自动抓取账面库存作为快照。不传 productIds 则对全部在售商品建账")
    @PostMapping
    @RequireRole({"MANAGER"})
    public R<Long> create(@Valid @RequestBody StockCheckCreateDTO dto) {
        return R.ok(stockCheckService.create(dto));
    }

    @Operation(summary = "录入实盘数", description = "只需提交有差异的行，未提交的行保持无差异")
    @PutMapping("/{id}/items")
    @RequireRole({"MANAGER"})
    public R<Void> updateItems(@PathVariable Long id,
                               @Valid @RequestBody StockCheckItemUpdateDTO dto) {
        stockCheckService.updateItems(id, dto);
        return R.ok();
    }

    @Operation(summary = "提交盘点",
            description = "以实盘数为准把账面调平：盘盈记 CHECK_GAIN、盘亏记 CHECK_LOSS 写入库存流水。"
                    + "已提交的单据重复提交返回 4007")
    @PostMapping("/{id}/finish")
    @RequireRole({"MANAGER"})
    public R<Void> finish(@PathVariable Long id) {
        stockCheckService.finish(id);
        return R.ok();
    }

    @Operation(summary = "作废盘点单", description = "仅草稿可作废；已完成需重新建单修正")
    @PostMapping("/{id}/cancel")
    @RequireRole({"MANAGER"})
    public R<Void> cancel(@PathVariable Long id) {
        stockCheckService.cancel(id);
        return R.ok();
    }
}
