package com.retail.erp.common.enums;

import lombok.Getter;

/**
 * 库存变动业务类型
 *
 * direction 表示对库存的作用方向：+1 增加、-1 减少。
 * 所有写 stock_record 的地方都必须从这里取值，禁止裸写字符串，
 * 否则盘点差异追溯时会出现「查不到来源」的孤儿流水。
 */
@Getter
public enum BizTypeEnum {

    PURCHASE_IN("采购入库", 1),
    PURCHASE_RETURN_OUT("采购退货出库", -1),
    PURCHASE_RETURN_CANCEL("采购退货作废回滚", 1),
    SALE_OUT("销售出库", -1),
    SALE_RETURN_IN("销售退货入库", 1),
    SALE_RETURN_CANCEL("销售退货作废回滚", -1),
    SALE_CANCEL_ROLLBACK("销售单作废回滚", 1),
    CHECK_GAIN("盘盈", 1),
    CHECK_LOSS("盘亏", -1);

    private final String label;
    private final int direction;

    BizTypeEnum(String label, int direction) {
        this.label = label;
        this.direction = direction;
    }

    /**
     * 按枚举名取中文标签
     *
     * 历史数据里如果出现已下线的业务类型，原样返回枚举名而不是 null ——
     * 界面上显示一个「UNKNOWN_XXX」总比显示空白好排查。
     */
    public static String labelOf(String name) {
        if (name == null) {
            return null;
        }
        for (BizTypeEnum e : values()) {
            if (e.name().equals(name)) {
                return e.getLabel();
            }
        }
        return name;
    }
}
