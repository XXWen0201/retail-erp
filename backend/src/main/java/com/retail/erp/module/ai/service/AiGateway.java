package com.retail.erp.module.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AI 调用网关
 *
 * 所有跟大模型打交道的地方都必须经过这里，统一负责三件事：
 *   1. 可用性判断 —— ChatClient 不存在时，不能让它变成启动失败
 *   2. 异常兜底 —— 欠费、限流、超时、网络不通全部捕获，转成 degraded 结果，
 *      绝不让一个「锦上添花」的功能把主流程带崩
 *   3. 统一取模型名，方便记录调用日志与前端展示
 *
 * 为什么用 ObjectProvider 而不是直接注入 ChatClient.Builder：
 * Spring AI 只有在能拿到有效 api-key 时才创建 ChatModel。直接注入的话，
 * 一旦 AI 被关掉、或密钥被撤走，应用会在启动阶段就因为找不到 Bean 而整个起不来 ——
 * 一个可选功能不该有这种杀伤力。ObjectProvider 让「没有」也成为一种正常状态。
 *
 * 注意：靠「api-key 留空」是降不了级的 —— Spring AI 的语音等自动配置在密钥为空时
 * 会直接抛 IllegalArgumentException，应用根本起不来。所以配置层必须给一个非空值，
 * 真正的降级点在这里（bean 拿不到就走本地算法），而不在配置层。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiGateway {

    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;

    @Value("${spring.ai.openai.chat.options.model:unknown}")
    private String modelName;

    /** 模型是否可用。前端据此决定要不要显示「AI 暂不可用」提示 */
    public boolean available() {
        return chatClientBuilderProvider.getIfAvailable() != null;
    }

    public String modelName() {
        return modelName;
    }

    /**
     * 同步调用
     *
     * @param systemPrompt 系统提示词，约束模型只基于给定数据回答
     * @param userPrompt   用户问题
     * @param tools        可被模型自主调用的工具对象，可为 null
     */
    public AiResult complete(String systemPrompt, String userPrompt, Object tools) {
        long start = System.currentTimeMillis();
        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        if (builder == null) {
            return AiResult.degraded("未配置 AI 密钥，功能已降级为本地规则计算",
                    System.currentTimeMillis() - start);
        }

        try {
            ChatClient.ChatClientRequestSpec spec = builder.build().prompt()
                    .system(systemPrompt)
                    .user(userPrompt);
            if (tools != null) {
                spec = spec.tools(tools);
            }
            String text = spec.call().content();
            return AiResult.ok(text, modelName, System.currentTimeMillis() - start);
        } catch (Exception e) {
            // 大模型调用失败是很常见的：欠费、限流、网关抖动。
            // 统一记 warn 而不是 error —— 这不属于系统故障，只是外部依赖暂时不可用
            log.warn("AI 调用失败，已降级 model={} err={}", modelName, e.getMessage());
            return AiResult.degraded("AI 服务暂不可用（" + brief(e) + "），已改用本地规则",
                    System.currentTimeMillis() - start);
        }
    }

    /**
     * 流式调用
     *
     * 返回 Flux 由调用方决定怎么推给前端。注意这里不catch异常后发兜底文案，
     * 而是把错误原样传给下游 —— 流式场景下客户端已经收到一半内容了，
     * 半截内容后面再接一段「AI 不可用」反而更让人困惑，
     * 不如让它以错误结束，由前端提示重试。
     */
    public Flux<String> stream(String systemPrompt, String userPrompt, Object tools) {
        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        if (builder == null) {
            return Flux.error(new IllegalStateException("未配置 AI 密钥，无法使用流式问答"));
        }
        try {
            ChatClient.ChatClientRequestSpec spec = builder.build().prompt()
                    .system(systemPrompt)
                    .user(userPrompt);
            if (tools != null) {
                spec = spec.tools(tools);
            }
            return spec.stream().content();
        } catch (Exception e) {
            log.warn("AI 流式调用初始化失败 model={} err={}", modelName, e.getMessage());
            return Flux.error(e);
        }
    }

    private String brief(Exception e) {
        String msg = e.getMessage();
        if (msg == null) {
            return e.getClass().getSimpleName();
        }
        // 异常信息里可能带很长的网关响应体，截断后再放进给用户看的文案里
        return msg.length() > 60 ? msg.substring(0, 60) + "..." : msg;
    }

    /** 一次 AI 调用的结果 */
    public record AiResult(String text, String model, List<String> toolsUsed,
                           int costMs, boolean degraded) {

        static AiResult ok(String text, String model, long costMs) {
            return new AiResult(text, model, List.of(), (int) costMs, false);
        }

        static AiResult degraded(String text, long costMs) {
            return new AiResult(text, null, List.of(), (int) costMs, true);
        }

        public AiResult withTools(List<String> tools) {
            return new AiResult(text, model, tools, costMs, degraded);
        }
    }
}
