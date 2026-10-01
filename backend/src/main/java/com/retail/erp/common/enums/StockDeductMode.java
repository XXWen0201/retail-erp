package com.retail.erp.common.enums;

import lombok.Getter;

/**
 * 库存扣减策略
 *
 * 系统同时提供两种扣减方案，通过 application.yml 的 app.stock.deduct-mode 切换，
 * 这也是本项目「库存扣减一致性」专题的核心：用同一套压测脚本对比两种方案的表现。
 *
 *  DB    —— 数据库乐观锁。以 stock.version 为版本号，UPDATE ... WHERE version = ?，
 *           单机小门店足够，实现简单，数据强一致。
 *  REDIS —— Redis + Lua 原子预扣减。把库存放到 Redis，用 Lua 脚本保证「判断 + 扣减」
 *           的原子性，再把结果异步落库，适合秒杀级并发，但需要处理缓存与数据库的对账。
 */
@Getter
public enum StockDeductMode {

    DB("数据库乐观锁"),
    REDIS("Redis+Lua 原子扣减");

    private final String label;

    StockDeductMode(String label) {
        this.label = label;
    }
}
