package com.retail.erp.config;

import com.retail.erp.common.enums.StockDeductMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * 应用配置
 *
 * 所有业务可调参数集中在这里，并在启动时校验。
 * 好处：密钥、阈值这类关键项一旦配错，应用直接启动失败（快速失败），
 * 而不是等到线上某个业务跑起来才 NPE。
 */
@Data
@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** 系统名称，用于页面标题与文档 */
    @NotBlank(message = "app.name 不能为空")
    private String name = "中小零售门店进销存与库存预警管理系统";

    @Valid
    @NotNull
    private Jwt jwt = new Jwt();

    @Valid
    @NotNull
    private Stock stock = new Stock();

    @Valid
    @NotNull
    private Alert alert = new Alert();

    @Valid
    @NotNull
    private Cors cors = new Cors();

    @Data
    public static class Jwt {
        /** 签名密钥，HMAC-SHA256 要求至少 32 字节。生产环境必须用环境变量覆盖 */
        @NotBlank(message = "app.jwt.secret 不能为空")
        private String secret;

        /** 访问令牌有效期（分钟）。故意设短，配合刷新令牌实现无感续期 */
        @Min(value = 1, message = "app.jwt.access-token-minutes 至少为 1")
        @Max(value = 1440, message = "app.jwt.access-token-minutes 不应超过 1440")
        private int accessTokenMinutes = 30;

        /** 刷新令牌有效期（天） */
        @Min(value = 1, message = "app.jwt.refresh-token-days 至少为 1")
        private int refreshTokenDays = 7;

        /** 刷新令牌 Cookie 是否只在 HTTPS 下发送。本地 http 调试设为 false */
        private boolean cookieSecure = false;

        /** 刷新令牌 Cookie 的 SameSite 策略 */
        private String cookieSameSite = "Lax";
    }

    @Data
    public static class Stock {
        /** 扣减策略：DB 或 REDIS */
        @NotNull
        private StockDeductMode deductMode = StockDeductMode.DB;

        /** 乐观锁冲突时的最大重试次数 */
        @Min(0)
        @Max(10)
        private int maxRetry = 3;

        /** 缓存中的库存键过期时间（秒），防止长期不用的商品常驻内存 */
        @Min(60)
        private int cacheTtlSeconds = 3600;
    }

    @Data
    public static class Alert {
        /** 距今多少天内的批次算「临期」 */
        @Min(1)
        @Max(365)
        private int nearExpiryDays = 30;

        /** 是否开启定时预警扫描 */
        private boolean scheduled = true;

        /** 预警扫描 cron，默认每天 07:00 与 19:00（门店开门前和打烊后各一次） */
        private String cron = "0 0 7,19 * * ?";
    }

    @Data
    public static class Cors {
        /**
         * 允许的跨域来源白名单。
         *
         * 刻意不用通配符 "*"：因为要带 Cookie 传刷新令牌（allowCredentials=true），
         * 浏览器规定此时来源必须显式指定，否则请求会被直接拒绝。
         * 生产环境请把这里改成真实域名。
         */
        private List<String> allowedOrigins = List.of(
                "http://localhost:5173",
                "http://127.0.0.1:5173"
        );

        /** 允许携带凭证（Cookie），刷新令牌依赖它 */
        private boolean allowCredentials = true;

        /** 预检请求缓存秒数，减少 OPTIONS 往返 */
        private long maxAge = 3600L;
    }
}
