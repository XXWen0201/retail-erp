package com.retail.erp.module.stock.controller;

import com.retail.erp.common.PageResult;
import com.retail.erp.common.R;
import com.retail.erp.module.stock.dto.BenchmarkRequest;
import com.retail.erp.module.stock.dto.BenchmarkResult;
import com.retail.erp.module.stock.dto.StockBatchQuery;
import com.retail.erp.module.stock.dto.StockBatchVO;
import com.retail.erp.module.stock.dto.StockOverviewVO;
import com.retail.erp.module.stock.dto.StockRecordQuery;
import com.retail.erp.module.stock.dto.StockRecordVO;
import com.retail.erp.module.stock.service.StockQueryService;
import com.retail.erp.security.RequireRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "库存", description = "总览 / 批次 / 流水 / 并发压测")
@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockQueryService stockQueryService;

    @Operation(summary = "库存总览", description = "工作台与库存页顶部的统计卡片数据")
    @GetMapping("/overview")
    public R<StockOverviewVO> overview() {
        return R.ok(stockQueryService.overview());
    }

    @Operation(summary = "批次分页", description = "支持按商品筛选、只看临期、只看过期")
    @GetMapping("/batches")
    public R<PageResult<StockBatchVO>> batches(@Validated StockBatchQuery query) {
        return R.ok(stockQueryService.batchPage(query));
    }

    @Operation(summary = "库存流水分页", description = "盘点差异追溯的唯一凭据")
    @GetMapping("/records")
    public R<PageResult<StockRecordVO>> records(@Validated StockRecordQuery query) {
        return R.ok(stockQueryService.recordPage(query));
    }

    @Operation(summary = "并发扣减压测",
            description = "同一批数据分别跑数据库乐观锁与 Redis+Lua 两种扣减方案，"
                    + "返回 QPS 与「成功次数是否等于实际扣减量」的一致性结论。"
                    + "⚠️ 会真实扣减库存并写入 bizNo=BENCHMARK 的流水")
    @PostMapping("/benchmark")
    @RequireRole({"MANAGER"})
    public R<BenchmarkResult> benchmark(@Valid @RequestBody BenchmarkRequest request) {
        return R.ok(stockQueryService.benchmark(request));
    }
}
