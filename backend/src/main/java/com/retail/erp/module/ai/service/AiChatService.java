package com.retail.erp.module.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 库存智能问答服务
 *
 * 与补货分析的差别：补货是「数字由算法算、模型负责解释」，
 * 问答是「模型自己决定查什么」。所以这里把工具集交给模型，
 * 由它根据用户问的话去挑合适的查询 —— 这也是整个项目里最能体现
 * Agent 思维的一处：不是把数据塞给模型，而是给模型一套工具。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    private static final String LOG_TYPE_CHAT = "CHAT";

    private static final String SYSTEM_PROMPT = """
            你是一家中小零售门店的库存管理助手，负责回答店长关于商品、库存、销售、保质期的问题。

            你可以调用工具查询真实数据。请严格遵守：
            1. 涉及任何具体数字（库存量、销量、金额、日期）时，必须先调用工具查询，
               绝对不允许凭猜测或经验编造数字
            2. 只使用工具返回的数据回答，工具没有提供的信息就明说「系统里查不到」
            3. 用简体中文回答，语气像一位熟悉门店业务的老店员，简洁、直白
            4. 回答控制在 200 字以内，必要时可以用短横线分点，但不要用 Markdown 标题
            5. 如果查出来存在断货或已过期批次，要在回答里明确提醒，并给出下一步动作建议
            """;

    private final AiGateway aiGateway;
    private final InventoryAiTools tools;
    private final ReplenishService replenishService;
    private final ObjectMapper objectMapper;

    // ==================================================================
    //  同步问答
    // ==================================================================

    public ChatResponseDto chat(String question) {
        InventoryAiTools.beginTrace();
        AiGateway.AiResult result;
        List<String> toolsUsed;
        try {
            result = aiGateway.complete(SYSTEM_PROMPT, question, tools);
        } finally {
            // 一定要在 finally 里收尾：ThreadLocal 没清掉会跟着线程池里的线程
            // 一路带到下一个请求上，工具列表就串了
            toolsUsed = InventoryAiTools.endTrace();
        }

        String answer = result.degraded() ? degradedAnswer(result) : result.text();
        replenishService.writeLog(LOG_TYPE_CHAT, null, briefTitle(question), question, answer, result);

        ChatResponseDto dto = new ChatResponseDto();
        dto.setAnswer(answer);
        dto.setModel(result.model());
        dto.setToolsUsed(toolsUsed);
        dto.setCostMs(result.costMs());
        dto.setDegraded(result.degraded());
        return dto;
    }

    // ==================================================================
    //  流式问答
    // ==================================================================

    public Flux<String> chatStream(String question) {
        if (!aiGateway.available()) {
            String answer = degradedAnswer(
                    new AiGateway.AiResult("未配置 AI 密钥", null, List.of(), 0, true));
            replenishService.writeLog(LOG_TYPE_CHAT, null, briefTitle(question), question, answer,
                    new AiGateway.AiResult(answer, null, List.of(), 0, true));
            return Flux.just(sse(answer), "[DONE]");
        }

        InventoryAiTools.beginTrace();
        StringBuilder collected = new StringBuilder();
        long start = System.currentTimeMillis();

        return aiGateway.stream(SYSTEM_PROMPT, question, tools)
                .map(chunk -> {
                    collected.append(chunk);
                    return sse(chunk);
                })
                .concatWith(Flux.defer(() -> {
                    List<String> used = InventoryAiTools.endTrace();
                    replenishService.writeLog(LOG_TYPE_CHAT, null, briefTitle(question), question,
                            collected.toString(),
                            new AiGateway.AiResult(collected.toString(), aiGateway.modelName(),
                                    used, (int) (System.currentTimeMillis() - start), false));
                    return Flux.just("[DONE]");
                }))
                .onErrorResume(e -> {
                    InventoryAiTools.endTrace();
                    log.warn("智能问答流式调用失败 err={}", e.getMessage());
                    String fallback = degradedAnswer(new AiGateway.AiResult(
                            e.getMessage(), null, List.of(), 0, true));
                    replenishService.writeLog(LOG_TYPE_CHAT, null, briefTitle(question), question,
                            fallback, new AiGateway.AiResult(fallback, null, List.of(), 0, true));
                    return Flux.just(sse(fallback), "[DONE]");
                });
    }

    // ==================================================================

    /**
     * AI 不可用时的兜底回复
     *
     * 不返回一句干巴巴的「服务异常」，而是告诉用户现在还能用什么。
     * 补货建议和预警扫描都是纯本地计算，AI 挂了它们照样能用 ——
     * 把这个事实讲清楚，用户就知道系统没坏，只是这一块暂时降级了。
     */
    private String degradedAnswer(AiGateway.AiResult result) {
        return "AI 智能问答当前不可用（" + (result.degraded() ? "模型服务未就绪或调用失败" : "未知原因") + "）。\n\n"
                + "你仍然可以正常使用这些由系统本地计算的功能：\n"
                + "1. 「智能补货」页的补货建议 —— 由移动平均 + 安全库存算法实时计算，不依赖 AI\n"
                + "2. 「库存预警」页的扫描 —— 低库存、断货、临期、过期全部本地判断\n"
                + "3. 「统计报表」页的采购、销售、库存、毛利报表\n\n"
                + "如需恢复问答，请检查后端配置中的 AI 密钥与账户余额后重试。";
    }

    private String briefTitle(String question) {
        if (question == null) {
            return null;
        }
        return question.length() <= 80 ? question : question.substring(0, 80) + "...";
    }

    private String sse(String content) {
        try {
            return objectMapper.writeValueAsString(Map.of("content", content));
        } catch (Exception e) {
            return "{\"content\":\"\"}";
        }
    }

    /**
     * 同步问答的返回载体
     *
     * 直接复用对外 DTO 会让 ai 模块对外部结构产生耦合，
     * 这里定义一个轻量的内部记录，再由 Controller 转成 ChatResponse。
     */
    public static class ChatResponseDto {
        private String answer;
        private String model;
        private List<String> toolsUsed;
        private int costMs;
        private boolean degraded;

        public String getAnswer() {
            return answer;
        }

        public void setAnswer(String answer) {
            this.answer = answer;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public List<String> getToolsUsed() {
            return toolsUsed;
        }

        public void setToolsUsed(List<String> toolsUsed) {
            this.toolsUsed = toolsUsed;
        }

        public int getCostMs() {
            return costMs;
        }

        public void setCostMs(int costMs) {
            this.costMs = costMs;
        }

        public boolean isDegraded() {
            return degraded;
        }

        public void setDegraded(boolean degraded) {
            this.degraded = degraded;
        }
    }
}
