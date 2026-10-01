package com.retail.erp.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置
 *
 * 序列化方案说明（这里踩过坑）：
 *   key 用 StringRedisSerializer —— 保证 redis-cli 里能直接看懂键名
 *   value 用 Jackson JSON        —— 默认的 JdkSerializationRedisSerializer 会写出乱码，
 *                                   且实体类改字段后反序列化直接炸
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer valueSerializer =
                new GenericJackson2JsonRedisSerializer(buildObjectMapper());

        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();
        return template;
    }

    private ObjectMapper buildObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // 带类型信息，反序列化时才能还原成原始实体而不是 LinkedHashMap
        mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        mapper.registerModule(new JavaTimeModule());   // 支持 LocalDateTime
        return mapper;
    }

    /**
     * 库存原子扣减 Lua 脚本
     *
     * 为什么必须用 Lua：如果先 GET 判断余量、再 DECRBY，两步之间会有窗口期，
     * 并发下多个请求可能同时通过判断，导致超卖。Redis 执行 Lua 是单线程原子的，
     * 判断与扣减之间不会被插入其他命令，从根上消除竞态。
     *
     * 返回值约定：
     *   >= 0  扣减成功，返回扣减后的剩余库存
     *   -1    缓存中无该商品库存，需回源数据库加载后重试
     *   -2    库存不足，扣减失败
     */
    @Bean
    public DefaultRedisScript<Long> stockDeductScript() {
        String lua = """
                local key     = KEYS[1]
                local qty     = tonumber(ARGV[1])
                local current = redis.call('GET', key)
                if not current then
                    return -1
                end
                current = tonumber(current)
                if current < qty then
                    return -2
                end
                return redis.call('DECRBY', key, qty)
                """;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(lua);
        script.setResultType(Long.class);
        return script;
    }

    /**
     * 库存回滚脚本：把已预扣的数量加回去。
     * 常用于销售单后续步骤失败时的补偿，保证 Redis 与数据库最终一致。
     */
    @Bean
    public DefaultRedisScript<Long> stockRollbackScript() {
        String lua = """
                local key = KEYS[1]
                local qty = tonumber(ARGV[1])
                if not redis.call('EXISTS', key) then
                    return -1
                end
                return redis.call('INCRBY', key, qty)
                """;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(lua);
        script.setResultType(Long.class);
        return script;
    }
}
