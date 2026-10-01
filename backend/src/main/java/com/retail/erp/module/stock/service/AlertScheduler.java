package com.retail.erp.module.stock.service;

import com.retail.erp.config.AppProperties;
import com.retail.erp.module.stock.dto.AlertScanResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 预警定时扫描
 *
 * 默认每天 07:00 与 19:00 各扫一次：开门前一次，让店长上班就看得见该补什么货；
 * 打烊后一次，把当天卖出来的临期情况梳理干净。
 *
 * 由 AppProperties.alert.scheduled 控制开关 —— 压测和本地调试时不希望后台定时任务
 * 突然插进来改数据，能一键关掉比改代码方便。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertScheduler {

    private final AlertService alertService;
    private final AppProperties props;

    @Scheduled(cron = "${app.alert.cron:0 0 7,19 * * ?}")
    public void scanAlerts() {
        if (!props.getAlert().isScheduled()) {
            return;
        }
        long start = System.currentTimeMillis();
        try {
            AlertScanResultVO result = alertService.scan();
            log.info("定时预警扫描完成 新增={} 更新={} 自动关闭={} 未处理={} 耗时={}ms",
                    result.getCreatedCount(), result.getUpdatedCount(), result.getClosedCount(),
                    result.getUnhandledTotal(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            // 定时任务里必须自己吞掉异常并记录：异常一路抛到调度器外层，
            // 只会被记一笔日志，而且容易被其他日志淹没，看不出是哪次扫描失败。
            // 明确捕获后下一轮照常执行，不会因为一次数据库抖动就彻底停摆
            log.error("定时预警扫描失败 time={}", LocalDateTime.now(), e);
        }
    }
}
