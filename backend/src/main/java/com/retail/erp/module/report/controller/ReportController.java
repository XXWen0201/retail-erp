package com.retail.erp.module.report.controller;

import com.retail.erp.common.R;
import com.retail.erp.module.report.dto.DashboardVO;
import com.retail.erp.module.report.dto.ProfitReportVO;
import com.retail.erp.module.report.dto.PurchaseReportVO;
import com.retail.erp.module.report.dto.ReportQuery;
import com.retail.erp.module.report.dto.SalesReportVO;
import com.retail.erp.module.report.dto.StockReportVO;
import com.retail.erp.module.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "统计报表", description = "工作台 / 采购 / 销售 / 库存 / 毛利")
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "工作台汇总", description = "今日与本月销售、待入库采购、未处理预警、库存总值、近 30 天趋势")
    @GetMapping("/dashboard")
    public R<DashboardVO> dashboard() {
        return R.ok(reportService.dashboard());
    }

    @Operation(summary = "采购统计", description = "支持 startDate / endDate / groupBy=day|month")
    @GetMapping("/purchase")
    public R<PurchaseReportVO> purchase(@Validated ReportQuery query) {
        return R.ok(reportService.purchase(query));
    }

    @Operation(summary = "销售统计", description = "含趋势、分类占比、商品销量排行")
    @GetMapping("/sales")
    public R<SalesReportVO> sales(@Validated ReportQuery query) {
        return R.ok(reportService.sales(query));
    }

    @Operation(summary = "库存统计", description = "含分类分布、低于下限清单、临期过期清单")
    @GetMapping("/stock")
    public R<StockReportVO> stock() {
        return R.ok(reportService.stock());
    }

    @Operation(summary = "毛利统计", description = "含综合毛利率、趋势毛利率、分类毛利")
    @GetMapping("/profit")
    public R<ProfitReportVO> profit(@Validated ReportQuery query) {
        return R.ok(reportService.profit(query));
    }

    @Operation(summary = "商品销量排行", description = "默认取前 10，按销量倒序")
    @GetMapping("/top-products")
    public R<SalesReportVO> topProducts(@Validated ReportQuery query) {
        SalesReportVO vo = reportService.sales(query);
        // 只回传排行部分，避免前端为了拿一个榜单还要多传一份趋势数据
        vo.setTrend(null);
        vo.setByCategory(null);
        return R.ok(vo);
    }
}
