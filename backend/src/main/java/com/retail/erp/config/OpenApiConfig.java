package com.retail.erp.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档配置
 *
 * 访问地址：http://localhost:8080/swagger-ui/index.html
 * 调试方式：先在 /api/auth/login 拿到 accessToken，点右上角 Authorize 填入即可。
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI retailErpOpenAPI(AppProperties props) {
        return new OpenAPI()
                .info(new Info()
                        .title(props.getName())
                        .description("""
                                中小零售门店进销存与库存预警管理系统 后端接口

                                核心能力：
                                1. 商品 / 供应商 / 采购管理
                                2. 采购入库、销售出库、退货、盘点
                                3. 批次与保质期管理、库存上下限与临期预警
                                4. 采购 / 销售 / 库存 / 毛利统计报表
                                5. 库存扣减一致性（乐观锁 + Redis Lua 双方案）
                                6. Spring AI 智能补货建议与库存问答
                                """)
                        .version("1.0.0")
                        .contact(new Contact().name("文嘉仪"))
                        .license(new License().name("毕业设计")))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("填入登录接口返回的 accessToken")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME));
    }
}
