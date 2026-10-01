package com.retail.erp.module.ai.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.retail.erp.common.BizException;
import com.retail.erp.common.PageResult;
import com.retail.erp.module.ai.dto.AiLogQuery;
import com.retail.erp.module.ai.dto.AiProductRow;
import com.retail.erp.module.ai.dto.DailySalesRow;
import com.retail.erp.module.ai.dto.ReplenishAnalyzeVO;
import com.retail.erp.module.ai.dto.ReplenishSuggestionVO;
import com.retail.erp.module.ai.dto.ReplenishSuggestionsVO;
import com.retail.erp.module.ai.entity.AiLog;
import com.retail.erp.module.ai.mapper.AiLogMapper;
import com.retail.erp.module.ai.mapper.AiMapper;
import com.retail.erp.module.basic.entity.Product;
import com.retail.erp.module.basic.mapper.ProductMapper;
import com.retail.erp.security.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 补货建议服务
 *
 * 职责划分很明确：
 *   补货数量 —— 本地算法算（ReplenishCalculator），永远可用，不依赖网络
 *   分析文字 —— 大模型写（AiGateway），可选增强，失败就降级成本地拼的文案
 *
 * 这么切分是因为「补货多少」是门店每天都要用的核心决策，不能因为模型欠费就用不了；
 * 而「帮我用一句话说明为什么」只是让界面更好读，模型不可用时退化成规则文案完全可接受。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplenishService {

    private static final String LOG_TYPE_REPLENISH = "REPLENISH";

    /** 一次最多返回多少条建议，避免门店商品多时前端直接卡死 */
    private static final int MAX_SUGGESTIONS = 50;

    private static final String ANALYZE_SYSTEM_PROMPT = """
            你是一家中小零售门店的库存管理助手。
            用户会给你某个商品的销售与库存数据，请给出简短、具体、可直接执行的补货建议。

            要求：
            1. 用中文回答，控制在 150 字以内，不要分点罗列数据，要用连贯的句子
            2. 只依据用户给出的数据说话，不要编造任何未提供的数字
            3. 要说明「为什么现在要补」「大概补多少」「如果不补会怎样」三件事
            4. 不要输出 Markdown 标题或代码块，直接给一段话
            """;

    private final AiMapper aiMapper;
    private final ReplenishCalculator calculator;
    private final AiGateway aiGateway;
    private final AiLogMapper aiLogMapper;
    private final ProductMapper productMapper;
    private final ObjectMapper objectMapper;

    // ==================================================================
    //  补货建议列表
    // ==================================================================

    public ReplenishSuggestionsVO suggestions(int days) {
        LocalDate today = LocalDate.now();
        LocalDate start = calculator.windowStart(days, today);

        List<AiProductRow> products = aiMapper.selectProductRows();
        Map<Long, Map<String, Long>> salesByProduct = groupSalesByProduct(
                aiMapper.selectDailySales(start, today));

        List<ReplenishSuggestionVO> items = new ArrayList<>();
        for (AiProductRow product : products) {
            List<Long> series = calculator.toDailySeries(
                    salesByProduct.getOrDefault(product.getProductId(), Map.of()), start, days);
            ReplenishCalculator.Stats stats = calculator.computeStats(series, days);

            ReplenishSuggestionVO vo = calculator.calculate(
                    product, stats.avgDaily(), stats.stdDev(), days);
            if (vo != null) {
                items.add(vo);
            }
        }

        // 紧急的排前面；同样紧急时，可支撑天数少的排前面。
        // 「断货」的可支撑天数是 0，天然会排在所有 HIGH 里的第一位
        items.sort(Comparator
                .comparingInt((ReplenishSuggestionVO v) -> urgencyRank(v.getUrgency()))
                .thenComparing(ReplenishSuggestionVO::getStockDays));

        List<ReplenishSuggestionVO> limited = items.size() > MAX_SUGGESTIONS
                ? items.subList(0, MAX_SUGGESTIONS) : items;

        ReplenishSuggestionsVO vo = new ReplenishSuggestionsVO();
        vo.setAlgorithm(ReplenishCalculator.ALGORITHM_DESC);
        vo.setDays(days);
        vo.setGeneratedAt(LocalDateTime.now());
        vo.setItems(limited);
        return vo;
    }

    /** 单个商品的补货建议，找不到（说明不需要补货）时返回 null */
    public ReplenishSuggestionVO suggestionFor(Long productId, int days) {
        AiProductRow product = aiMapper.selectProductRows().stream()
                .filter(p -> p.getProductId().equals(productId))
                .findFirst()
                .orElseThrow(() -> BizException.notFound("商品"));

        LocalDate today = LocalDate.now();
        LocalDate start = calculator.windowStart(days, today);
        Map<String, Long> byDate = new HashMap<>();
        for (DailySalesRow row : aiMapper.selectDailySales(start, today)) {
            if (row.getProductId().equals(productId)) {
                byDate.put(row.getStatDate(), row.getQuantity() == null ? 0L : row.getQuantity());
            }
        }

        List<Long> series = calculator.toDailySeries(byDate, start, days);
        ReplenishCalculator.Stats stats = calculator.computeStats(series, days);
        return calculator.calculate(product, stats.avgDaily(), stats.stdDev(), days);
    }

    // ==================================================================
    //  AI 分析
    // ==================================================================

    public ReplenishAnalyzeVO analyze(Long productId, int days) {
        ReplenishSuggestionVO suggestion = suggestionFor(productId, days);
        if (suggestion == null) {
            suggestion = zeroSuggestion(productId, days);
        }

        String prompt = buildAnalyzePrompt(suggestion, days);
        AiGateway.AiResult result = aiGateway.complete(ANALYZE_SYSTEM_PROMPT, prompt, null);

        String analysis = result.degraded() ? localAnalysis(suggestion) : result.text();
        writeLog(productId, suggestion.getProductName(), prompt, analysis, result);

        ReplenishAnalyzeVO vo = new ReplenishAnalyzeVO();
        vo.setProductId(productId);
        vo.setProductName(suggestion.getProductName());
        vo.setAnalysis(analysis);
        vo.setModel(result.model());
        vo.setDegraded(result.degraded());
        return vo;
    }

    /** 流式分析：每个元素是一段 SSE 数据体（JSON 或 [DONE]），由 Controller 直接推给浏览器 */
    public Flux<String> analyzeStream(Long productId, int days) {
        ReplenishSuggestionVO suggestion = suggestionFor(productId, days);
        boolean needFallback = suggestion == null;
        if (needFallback) {
            suggestion = zeroSuggestion(productId, days);
        }

        String prompt = buildAnalyzePrompt(suggestion, days);
        ReplenishSuggestionVO finalSuggestion = suggestion;

        if (!aiGateway.available()) {
            writeLog(productId, suggestion.getProductName(), prompt, localAnalysis(suggestion),
                    AiGateway.AiResult.degraded("未配置 AI 密钥", 0));
            return Flux.just(sse(localAnalysis(suggestion)), "[DONE]");
        }

        StringBuilder collected = new StringBuilder();
        return aiGateway.stream(ANALYZE_SYSTEM_PROMPT, prompt, null)
                .map(chunk -> {
                    collected.append(chunk);
                    return sse(chunk);
                })
                .concatWith(Flux.defer(() -> {
                    // 流结束时落库：只有拿到完整回答才能记录，
                    // 边收边写会把同一次问答拆成几十条日志
                    writeLog(productId, finalSuggestion.getProductName(), prompt,
                            collected.toString(),
                            new AiGateway.AiResult(collected.toString(), aiGateway.modelName(),
                                    List.of(), 0, false));
                    return Flux.just("[DONE]");
                }))
                .onErrorResume(e -> {
                    log.warn("AI 补货分析流式调用失败，降级为本地文案 err={}", e.getMessage());
                    String fallback = localAnalysis(finalSuggestion);
                    writeLog(productId, finalSuggestion.getProductName(), prompt, fallback,
                            AiGateway.AiResult.degraded("流式调用失败", 0));
                    return Flux.just(sse(fallback), "[DONE]");
                });
    }

    // ==================================================================
    //  调用日志
    // ==================================================================

    public PageResult<AiLog> logs(AiLogQuery query) {
        IPage<AiLog> page = aiLogMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()),
                Wrappers.<AiLog>lambdaQuery()
                        .eq(StringUtils.hasText(query.getLogType()), AiLog::getLogType, query.getLogType())
                        .orderByDesc(AiLog::getId));
        return PageResult.of(page);
    }

    /** 供智能问答模块记录 CHAT 类型日志，两个模块共用同一张表，便于统一查看用量 */
    void writeLog(String logType, Long productId, String title, String question,
                  String answer, AiGateway.AiResult result) {
        try {
            AiLog entity = new AiLog();
            entity.setLogType(logType);
            entity.setProductId(productId);
            entity.setTitle(title);
            entity.setQuestion(truncate(question, 6000));
            entity.setAnswer(truncate(answer, 6000));
            entity.setModel(result.model());
            entity.setCostMs(result.costMs());
            entity.setOperatorName(UserContext.currentUserName());
            aiLogMapper.insert(entity);
        } catch (Exception e) {
            // 记日志失败绝不能影响主流程返回结果
            log.warn("写入 AI 调用日志失败: {}", e.getMessage());
        }
    }

    private void writeLog(Long productId, String productName, String prompt,
                          String answer, AiGateway.AiResult result) {
        writeLog(LOG_TYPE_REPLENISH, productId, productName, prompt, answer, result);
    }

    // ==================================================================
    //  内部工具
    // ==================================================================

    private Map<Long, Map<String, Long>> groupSalesByProduct(List<DailySalesRow> rows) {
        Map<Long, Map<String, Long>> result = new HashMap<>();
        for (DailySalesRow row : rows) {
            result.computeIfAbsent(row.getProductId(), k -> new HashMap<>())
                    .put(row.getStatDate(), row.getQuantity() == null ? 0L : row.getQuantity());
        }
        return result;
    }

    /** 商品在窗口内一笔没卖、库存也正常时，构造一个「无需补货」的载体供 AI 分析使用 */
    private ReplenishSuggestionVO zeroSuggestion(Long productId, int days) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw BizException.notFound("商品");
        }
        ReplenishSuggestionVO vo = new ReplenishSuggestionVO();
        vo.setProductId(productId);
        vo.setProductName(product.getName());
        vo.setUnit(product.getUnit());
        vo.setCurrentStock(0);
        vo.setStockLower(product.getStockLower());
        vo.setStockUpper(product.getStockUpper());
        vo.setAvgDailySales(java.math.BigDecimal.ZERO);
        vo.setAvgWeeklySales(java.math.BigDecimal.ZERO);
        vo.setSafetyStock(0);
        vo.setLeadTimeDays(ReplenishCalculator.LEAD_TIME_DAYS);
        vo.setReorderPoint(0);
        vo.setSuggestQuantity(0);
        vo.setEstCost(java.math.BigDecimal.ZERO);
        vo.setStockDays(java.math.BigDecimal.valueOf(999));
        vo.setUrgency("LOW");
        vo.setReason("近 " + days + " 天没有销售记录，库存也处于正常区间，暂不需要补货");
        return vo;
    }

    private String buildAnalyzePrompt(ReplenishSuggestionVO s, int days) {
        return """
                商品名称：%s
                统计窗口：近 %d 天
                日均销量：%s %s
                周均销量：%s %s
                当前库存：%d %s
                库存下限：%d
                库存上限：%d
                安全库存：%d
                补货点：%d
                供应商到货周期：%d 天
                系统建议补货量：%d %s
                预计采购金额：%s 元
                当前库存可支撑天数：%s 天
                系统判定的紧急程度：%s
                """.formatted(
                s.getProductName(), days,
                s.getAvgDailySales().toPlainString(), s.getUnit(),
                s.getAvgWeeklySales().toPlainString(), s.getUnit(),
                s.getCurrentStock(), s.getUnit(),
                s.getStockLower(), s.getStockUpper(),
                s.getSafetyStock(), s.getReorderPoint(), s.getLeadTimeDays(),
                s.getSuggestQuantity(), s.getUnit(),
                s.getEstCost().toPlainString(),
                s.getStockDays().toPlainString(),
                s.getUrgency());
    }

    /**
     * 本地兜底文案
     *
     * 写成和模型输出相近的口吻，是为了让降级前后的界面观感一致 ——
     * 如果降级时只输出一串原始字段，用户会以为功能坏了。
     */
    private String localAnalysis(ReplenishSuggestionVO s) {
        if (s.getCurrentStock() <= 0) {
            return "%s 目前已经断货。近 %d 天日均卖出 %s %s，按供应商 %d 天的到货周期，"
                    .formatted(s.getProductName(), 0, s.getAvgDailySales().toPlainString(),
                            s.getUnit(), s.getLeadTimeDays())
                    + "现在下单也要等几天才能到，期间会持续缺货。"
                    + "建议立即补货 " + s.getSuggestQuantity() + " " + s.getUnit()
                    + "，预计采购金额 " + s.getEstCost().toPlainString() + " 元。";
        }
        return "%s 当前库存 %d %s，按近 30 天日均销量 %s %s 计算仅可支撑 %s 天，"
                .formatted(s.getProductName(), s.getCurrentStock(), s.getUnit(),
                        s.getAvgDailySales().toPlainString(), s.getUnit(),
                        s.getStockDays().toPlainString())
                + "已经低于补货点 " + s.getReorderPoint() + "。"
                + "考虑到到货需要 " + s.getLeadTimeDays() + " 天且日销量有波动（安全库存 "
                + s.getSafetyStock() + "），建议尽快补货 " + s.getSuggestQuantity() + " "
                + s.getUnit() + "，预计采购金额 " + s.getEstCost().toPlainString() + " 元。";
    }

    /** 把一段文本包成 SSE 的数据体 */
    private String sse(String content) {
        try {
            return objectMapper.writeValueAsString(Map.of("content", content));
        } catch (Exception e) {
            // 序列化失败说明内容里有异常字符，退回纯文本，不能因为转义失败就断流
            return "{\"content\":\"\"}";
        }
    }

    private int urgencyRank(String urgency) {
        return switch (urgency == null ? "LOW" : urgency) {
            case "HIGH" -> 0;
            case "MEDIUM" -> 1;
            default -> 2;
        };
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max) + "...(已截断)";
    }
}
