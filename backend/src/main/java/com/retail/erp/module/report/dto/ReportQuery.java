package com.retail.erp.module.report.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** 报表通用查询条件 */
@Data
public class ReportQuery {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    /** 聚合粒度：day 按日 / month 按月 */
    @Pattern(regexp = "day|month", message = "groupBy 只支持 day 或 month")
    private String groupBy = "day";

    /** 默认统计最近 30 天。空窗口会让报表退化成全表聚合，所以必须兜底 */
    public LocalDate startDateOrDefault() {
        return startDate == null ? LocalDate.now().minusDays(29) : startDate;
    }

    public LocalDate endDateOrDefault() {
        return endDate == null ? LocalDate.now() : endDate;
    }

    /** 传给 SQL 的日期格式串。用参数传而不是拼字符串，避免 SQL 注入 */
    public String dateFormat() {
        return "month".equalsIgnoreCase(groupBy) ? "%Y-%m" : "%Y-%m-%d";
    }
}
