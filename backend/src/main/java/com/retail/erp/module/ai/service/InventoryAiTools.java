package com.retail.erp.module.ai.service;

import com.retail.erp.config.AppProperties;
import com.retail.erp.module.ai.dto.AiProductRow;
import com.retail.erp.module.ai.mapper.AiMapper;
import com.retail.erp.module.report.dto.NameStatVO;
import com.retail.erp.module.report.dto.StockReportVO;
import com.retail.erp.module.report.mapper.ReportMapper;
import com.retail.erp.module.stock.dto.StockOverviewVO;
import com.retail.erp.module.stock.mapper.StockPageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 库存智能问答的可调用工具集（Function Calling）
 *
 * 这是「AI 智能问答」能落地而不是瞎编的关键：
 * 模型本身不知道这家店有多少货。我们不给它灌一大段数据让它背，
 * 而是把这些查询方法注册成工具，让模型自己判断该查什么、
 * 拿到真实数据后再组织语言回答。
 *
 * 好处有两个：
 *   1. 答案一定基于实时数据库，不会出现「模型编了一个库存数字」的情况
 *   2. 门店有多少商品都不影响提示词长度，工具是「按需调用」的
 *
 * 工具返回纯文本而不是 JSON：模型的目的是读懂然后转述给用户，
 * 纯文本更省 token，也不容易在字段名上产生歧义。
 */
@Component
@RequiredArgsConstructor
public class InventoryAiTools {

    private final AiMapper aiMapper;
    private final ReportMapper reportMapper;
    private final StockPageMapper stockPageMapper;
    private final AppProperties props;

    /**
     * 记录本次调用中被模型实际用到的工具
     *
     * 用 ThreadLocal 就够了：一次问答在同一次请求线程里完成，
     * 存下来是为了在响应里告诉用户「这个答案查了哪些数据」——
     * 让答案可验证，比让用户盲信模型要可信得多。
     */
    private static final ThreadLocal<List<String>> INVOKED = ThreadLocal.withInitial(ArrayList::new);

    public static void beginTrace() {
        INVOKED.set(new ArrayList<>());
    }

    public static List<String> endTrace() {
        List<String> used = INVOKED.get();
        INVOKED.remove();
        return used == null ? List.of() : used;
    }

    private static void trace(String toolName) {
        List<String> list = INVOKED.get();
        if (list != null && !list.contains(toolName)) {
            list.add(toolName);
        }
    }

    // ==================================================================

    @Tool(description = "查询当前库存低于库存下限的商品清单，每个商品会带出当前库存、库存下限和建议补货量。用于回答「哪些商品该补货了」「有什么缺货」这类问题")
    public String queryLowStock(
            @ToolParam(description = "最多返回几条，默认 10 条") Integer limit) {
        trace("queryLowStock");
        int max = (limit == null || limit <= 0) ? 10 : Math.min(limit, 30);

        List<StockReportVO.LowStockItem> items = reportMapper.selectLowStockList();
        if (items.isEmpty()) {
            return "当前没有库存低于下限的商品。";
        }
        StringBuilder sb = new StringBuilder("库存低于下限的商品共 ")
                .append(items.size()).append(" 个，前 ").append(Math.min(max, items.size()))
                .append(" 个如下：\n");
        items.stream().limit(max).forEach(i -> sb.append("- ")
                .append(i.getProductName())
                .append("：当前库存 ").append(i.getQuantity()).append(i.getUnit() == null ? "" : i.getUnit())
                .append("，下限 ").append(i.getStockLower())
                .append("，建议至少补 ").append(i.getGap()).append("\n"));
        return sb.toString();
    }

    @Tool(description = "查询最近即将到期和已经过期的商品批次，返回商品名、批次号、剩余数量和到期日期。用于回答「有什么快过期了」「临期商品有哪些」这类问题")
    public String queryExpiringBatches(
            @ToolParam(description = "查询未来多少天内到期的批次，不传则用系统默认值（30 天）") Integer withinDays) {
        trace("queryExpiringBatches");
        int days = (withinDays == null || withinDays <= 0)
                ? props.getAlert().getNearExpiryDays() : Math.min(withinDays, 365);

        List<StockReportVO.ExpiringItem> items = reportMapper.selectExpiringList(days);
        if (items.isEmpty()) {
            return "未来 " + days + " 天内没有临近到期的批次。";
        }

        List<StockReportVO.ExpiringItem> expired = items.stream()
                .filter(i -> i.getDaysToExpire() != null && i.getDaysToExpire() < 0).toList();
        List<StockReportVO.ExpiringItem> near = items.stream()
                .filter(i -> i.getDaysToExpire() == null || i.getDaysToExpire() >= 0).toList();

        StringBuilder sb = new StringBuilder();
        if (!expired.isEmpty()) {
            sb.append("已过期批次 ").append(expired.size()).append(" 个（需立即下架）：\n");
            expired.forEach(i -> sb.append("- ").append(i.getProductName())
                    .append(" 批次 ").append(i.getBatchNo())
                    .append("，剩余 ").append(i.getStockQuantity())
                    .append("，已于 ").append(i.getExpireDate()).append(" 过期\n"));
        }
        if (!near.isEmpty()) {
            sb.append("近期到期批次 ").append(near.size()).append(" 个：\n");
            near.stream().limit(15).forEach(i -> sb.append("- ").append(i.getProductName())
                    .append(" 批次 ").append(i.getBatchNo())
                    .append("，剩余 ").append(i.getStockQuantity())
                    .append("，到期日 ").append(i.getExpireDate())
                    .append("（还有 ").append(i.getDaysToExpire()).append(" 天）\n"));
        }
        return sb.toString();
    }

    @Tool(description = "按商品名称或商品编码模糊查询该商品的当前库存、库存上下限和参考进价。用于回答「某某商品还有多少库存」这类问题")
    public String queryProductStock(
            @ToolParam(description = "商品名称或编码的关键字，例如「酸奶」「P1001」") String keyword) {
        trace("queryProductStock");
        if (keyword == null || keyword.isBlank()) {
            return "请提供商品名称或编码关键字。";
        }
        List<AiProductRow> rows = aiMapper.searchProductStock(keyword.trim(), 10);
        if (rows.isEmpty()) {
            return "没有找到名称或编码包含「" + keyword + "」的商品。";
        }
        StringBuilder sb = new StringBuilder("匹配到 ").append(rows.size()).append(" 个商品：\n");
        for (AiProductRow row : rows) {
            int qty = row.getQuantity() == null ? 0 : row.getQuantity();
            sb.append("- ").append(row.getProductName())
                    .append("：当前库存 ").append(qty).append(row.getUnit() == null ? "" : row.getUnit())
                    .append("，下限 ").append(row.getStockLower())
                    .append("，上限 ").append(row.getStockUpper())
                    .append("，参考进价 ").append(row.getPurchasePrice()).append(" 元\n");
        }
        return sb.toString();
    }

    @Tool(description = "查询指定时间范围内销量最高的商品排行。用于回答「什么卖得最好」「哪些商品畅销」这类问题")
    public String querySalesRanking(
            @ToolParam(description = "统计最近多少天，默认 30 天") Integer days,
            @ToolParam(description = "返回前几名，默认 10") Integer limit) {
        trace("querySalesRanking");
        int window = (days == null || days <= 0) ? 30 : Math.min(days, 180);
        int max = (limit == null || limit <= 0) ? 10 : Math.min(limit, 20);

        LocalDate end = LocalDate.now();
        List<NameStatVO> rows = reportMapper.selectTopProducts(end.minusDays(window - 1L), end, max);
        if (rows.isEmpty()) {
            return "最近 " + window + " 天没有销售记录。";
        }
        StringBuilder sb = new StringBuilder("最近 ").append(window).append(" 天销量排行：\n");
        int rank = 1;
        for (NameStatVO row : rows) {
            sb.append(rank++).append(". ").append(row.getProductName())
                    .append("：销量 ").append(row.getQuantity())
                    .append("，销售额 ").append(row.getAmount()).append(" 元\n");
        }
        return sb.toString();
    }

    @Tool(description = "查询整体库存概况：商品种类数、库存总件数、库存成本总值、低库存商品数、断货商品数、临期与过期批次数。用于回答「库存情况怎么样」「有多少货」这类整体性问题")
    public String queryStockOverview() {
        trace("queryStockOverview");
        StockOverviewVO vo = stockPageMapper.selectStockOverview();
        StockOverviewVO batch = stockPageMapper.selectBatchOverview(props.getAlert().getNearExpiryDays());
        if (vo == null) {
            return "暂时无法获取库存概况。";
        }
        StringBuilder sb = new StringBuilder("当前库存概况：\n");
        sb.append("- 在售商品种类：").append(vo.getSkuCount()).append(" 种\n");
        sb.append("- 库存总件数：").append(vo.getTotalQuantity()).append("\n");
        sb.append("- 库存成本总值：").append(vo.getTotalCostValue()).append(" 元\n");
        sb.append("- 低于下限的商品：").append(vo.getLowStockCount()).append(" 个\n");
        sb.append("- 零库存断货商品：").append(vo.getOutOfStockCount()).append(" 个\n");
        if (batch != null) {
            sb.append("- 临期批次：").append(batch.getNearExpiryBatchCount()).append(" 个\n");
            sb.append("- 已过期批次：").append(batch.getExpiredBatchCount()).append(" 个\n");
        }
        return sb.toString();
    }
}
