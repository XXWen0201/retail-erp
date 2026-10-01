package com.retail.erp.module.stock.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 并发扣减压测入参 */
@Data
public class BenchmarkRequest {

    /** 并发线程数。上限 100：连接池才 20，线程开太多只会让请求在池外排队，测不出真实吞吐 */
    @Min(value = 1, message = "线程数至少为 1")
    @Max(value = 100, message = "线程数最多 100")
    private Integer threads = 20;

    /** 每个线程执行的扣减次数 */
    @Min(value = 1, message = "每线程次数至少为 1")
    @Max(value = 500, message = "每线程次数最多 500")
    private Integer timesPerThread = 20;

    /** 被压测的商品 id */
    @NotNull(message = "请选择压测商品")
    private Long productId;

    /** 每次扣减的数量，默认 1 */
    @Min(value = 1, message = "每次扣减数量至少为 1")
    private Integer quantityPerRequest = 1;

    /** 扣减方案：DB 数据库乐观锁 / REDIS Redis+Lua。不传则用配置文件里的默认值 */
    private String mode;
}
