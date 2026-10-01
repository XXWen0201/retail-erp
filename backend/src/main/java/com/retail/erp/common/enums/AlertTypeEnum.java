package com.retail.erp.common.enums;

import lombok.Getter;

/**
 * 库存预警类型
 */
@Getter
public enum AlertTypeEnum {

    /** 当前库存低于商品设定的库存下限 */
    LOW_STOCK("低于库存下限", "WARN"),

    /** 当前库存高于商品设定的库存上限，资金占用过高 */
    OVER_STOCK("高于库存上限", "WARN"),

    /** 库存为 0，已经断货 */
    OUT_OF_STOCK("零库存断货", "DANGER"),

    /** 批次临近到期（默认 30 天内），需要促销或退供应商 */
    NEAR_EXPIRY("批次临近到期", "WARN"),

    /** 批次已过期，必须下架 */
    EXPIRED("批次已过期", "DANGER");

    private final String label;
    private final String defaultLevel;

    AlertTypeEnum(String label, String defaultLevel) {
        this.label = label;
        this.defaultLevel = defaultLevel;
    }
}
