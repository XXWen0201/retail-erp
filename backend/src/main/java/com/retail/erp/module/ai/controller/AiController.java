package com.retail.erp.module.ai.controller;

import com.retail.erp.common.PageResult;
import com.retail.erp.common.R;
import com.retail.erp.module.ai.dto.AiLogQuery;
import com.retail.erp.module.ai.dto.ChatRequest;
import com.retail.erp.module.ai.dto.ChatResponse;
import com.retail.erp.module.ai.dto.ReplenishAnalyzeRequest;
import com.retail.erp.module.ai.dto.ReplenishAnalyzeVO;
import com.retail.erp.module.ai.dto.ReplenishSuggestionsVO;
import com.retail.erp.module.ai.entity.AiLog;
import com.retail.erp.module.ai.service.AiChatService;
import com.retail.erp.module.ai.service.ReplenishService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * AI 智能助手接口
 *
 * 两组能力：
 *   智能补货 —— 数量由本地算法算（永远可用），文字说明由模型写（可选增强）
 *   智能问答 —— 用 Function Calling 让模型自己查数据库，答案基于真实数据
 *
 * 流式接口返回 Flux 而不是 SseEmitter，是因为 Spring AI 的流本身就是 Reactor Flux，
 * 直接透传比手工订阅再 re-emit 少一层转换，也不会在中间层丢掉背压信号。
 * 前端要用 fetch + ReadableStream 读取（EventSource 没法自定义 Authorization 头）。
 */
@Tag(name = "AI 智能助手", description = "智能补货建议 / 库存智能问答")
@RestController
@RequestMapping("/api/ai")
@Validated
@RequiredArgsConstructor
public class AiController {

    /** SSE 流结束标记，前端收到它就关闭连接 */
    private static final String SSE_DONE = "[DONE]";

    private final ReplenishService replenishService;
    private final AiChatService aiChatService;

    // ==================================================================
    //  智能补货
    // ==================================================================

    @Operation(summary = "补货建议列表",
            description = "纯本地算法（移动平均 + 安全库存），不依赖 AI 服务，断网也可用")
    @GetMapping("/replenish/suggestions")
    public R<ReplenishSuggestionsVO> suggestions(
            @RequestParam(defaultValue = "30")
            @Min(value = 7, message = "统计窗口至少 7 天")
            @Max(value = 180, message = "统计窗口最多 180 天") int days) {
        return R.ok(replenishService.suggestions(days));
    }

    @Operation(summary = "AI 补货分析",
            description = "针对单个商品生成自然语言分析。AI 不可用时 degraded=true，"
                    + "analysis 为本地规则文案，功能不会中断")
    @PostMapping("/replenish/analyze")
    public R<ReplenishAnalyzeVO> analyze(@Valid @RequestBody ReplenishAnalyzeRequest request) {
        return R.ok(replenishService.analyze(request.productId(), request.daysOrDefault()));
    }

    @Operation(summary = "AI 补货分析（SSE 流式）",
            description = "每行 data: {\"content\":\"片段\"}，结束时发送 data: [DONE]")
    @PostMapping(value = "/replenish/analyze/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> analyzeStream(@Valid @RequestBody ReplenishAnalyzeRequest request) {
        return replenishService.analyzeStream(request.productId(), request.daysOrDefault());
    }

    // ==================================================================
    //  智能问答
    // ==================================================================

    @Operation(summary = "库存智能问答",
            description = "Function Calling：模型自主调用库存查询工具后再回答，"
                    + "toolsUsed 返回本次实际用到的工具，便于验证答案来源")
    @PostMapping("/chat")
    public R<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        AiChatService.ChatResponseDto dto = aiChatService.chat(request.question());
        ChatResponse vo = new ChatResponse();
        vo.setAnswer(dto.getAnswer());
        vo.setModel(dto.getModel());
        vo.setToolsUsed(dto.getToolsUsed());
        vo.setCostMs(dto.getCostMs());
        vo.setDegraded(dto.isDegraded());
        return R.ok(vo);
    }

    @Operation(summary = "库存智能问答（SSE 流式）",
            description = "每行 data: {\"content\":\"片段\"}，结束时发送 data: [DONE]")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@Valid @RequestBody ChatRequest request) {
        return aiChatService.chatStream(request.question());
    }

    // ==================================================================
    //  调用记录
    // ==================================================================

    @Operation(summary = "AI 调用记录", description = "补货分析与智能问答共用一个列表，便于查看用量与效果")
    @GetMapping("/logs")
    public R<PageResult<AiLog>> logs(@Validated AiLogQuery query) {
        return R.ok(replenishService.logs(query));
    }
}
