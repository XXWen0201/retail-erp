package com.retail.erp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 中小零售门店进销存与库存预警管理系统 —— 启动类
 *
 * @author 文嘉仪
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.retail.erp.module.**.mapper")
@EnableScheduling
@EnableAsync
public class RetailErpApplication {

    public static void main(String[] args) {
        SpringApplication.run(RetailErpApplication.class, args);
    }
}
