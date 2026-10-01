package com.retail.erp.module.stock.controller;

import com.retail.erp.common.PageResult;
import com.retail.erp.common.R;
import com.retail.erp.module.stock.dto.AlertScanResultVO;
import com.retail.erp.module.stock.dto.AlertSummaryVO;
import com.retail.erp.module.stock.dto.StockAlertQuery;
import com.retail.erp.module.stock.dto.StockAlertVO;
import com.retail.erp.module.stock.service.AlertService;
import com.retail.erp.security.RequireRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "库存预警", description = "低库存 / 断货 / 超储 / 临期 / 过期")
@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @Operation(summary = "预警分页查询")
    @GetMapping
    public R<PageResult<StockAlertVO>> page(@Validated StockAlertQuery query) {
        return R.ok(alertService.page(query));
    }

    @Operation(summary = "预警汇总", description = "各类型未处理数量，供工作台徽标与页签角标使用")
    @GetMapping("/summary")
    public R<AlertSummaryVO> summary() {
        return R.ok(alertService.summary());
    }

    @Operation(summary = "手动触发全量扫描",
            description = "幂等：同一条问题只更新不重复插入；库存恢复正常时自动关闭对应预警")
    @PostMapping("/scan")
    @RequireRole({"MANAGER"})
    public R<AlertScanResultVO> scan() {
        return R.ok(alertService.scan());
    }

    @Operation(summary = "处理预警")
    @PutMapping("/{id}/handle")
    @RequireRole({"MANAGER"})
    public R<Void> handle(@PathVariable Long id,
                          @Valid @RequestBody(required = false) HandleRequest body) {
        alertService.handle(id, body == null ? null : body.handleRemark());
        return R.ok();
    }

    @Operation(summary = "忽略预警")
    @PutMapping("/{id}/ignore")
    @RequireRole({"MANAGER"})
    public R<Void> ignore(@PathVariable Long id) {
        alertService.ignore(id);
        return R.ok();
    }

    /** 处理备注。@RequestBody 设为非必填，前端不传也能直接点「处理」 */
    public record HandleRequest(
            @Size(max = 255, message = "处理备注不能超过 255 个字符") String handleRemark) {
    }
}
