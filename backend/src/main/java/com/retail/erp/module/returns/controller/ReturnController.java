package com.retail.erp.module.returns.controller;

import com.retail.erp.common.PageResult;
import com.retail.erp.common.R;
import com.retail.erp.module.returns.dto.ReturnQuery;
import com.retail.erp.module.returns.dto.ReturnSaveDTO;
import com.retail.erp.module.returns.dto.ReturnVO;
import com.retail.erp.module.returns.service.ReturnService;
import com.retail.erp.security.RequireRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "退货管理", description = "采购退货 / 销售退货")
@RestController
@RequestMapping("/api/returns")
@RequiredArgsConstructor
public class ReturnController {

    private final ReturnService returnService;

    @Operation(summary = "退货单分页查询")
    @GetMapping
    public R<PageResult<ReturnVO>> page(@Validated ReturnQuery query) {
        return R.ok(returnService.page(query));
    }

    @Operation(summary = "退货单详情")
    @GetMapping("/{id}")
    public R<ReturnVO> detail(@PathVariable Long id) {
        return R.ok(returnService.detail(id));
    }

    @Operation(summary = "新建退货单",
            description = "保存即生效。采购退货扣库存、销售退货加库存；"
                    + "关联原单时校验可退量，超出返回 4008")
    @PostMapping
    @RequireRole({"MANAGER"})
    public R<Long> create(@Valid @RequestBody ReturnSaveDTO dto) {
        return R.ok(returnService.create(dto));
    }

    @Operation(summary = "作废退货单",
            description = "按当初退货产生的库存流水逐条冲正，回到原批次")
    @PostMapping("/{id}/cancel")
    @RequireRole({"MANAGER"})
    public R<Void> cancel(@PathVariable Long id) {
        returnService.cancel(id);
        return R.ok();
    }
}
