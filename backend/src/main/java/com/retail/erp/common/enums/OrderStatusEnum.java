package com.retail.erp.common.enums;

import lombok.Getter;

/**
 * 单据状态（采购单 / 销售单 / 退货单共用）
 *
 * 采购单：DRAFT → PENDING → FINISHED
 * 销售单：DRAFT → FINISHED（保存即扣库存）
 * 退货单：DRAFT → FINISHED（保存即回退库存）
 */
@Getter
public enum OrderStatusEnum {

    DRAFT("草稿"),
    PENDING("待入库"),
    FINISHED("已完成"),
    CANCELED("已作废");

    private final String label;

    OrderStatusEnum(String label) {
        this.label = label;
    }

    public boolean is(String code) {
        return this.name().equalsIgnoreCase(code);
    }

    /** 按枚举名取中文标签，未知值原样返回，避免界面上出现空白单元格 */
    public static String labelOf(String name) {
        if (name == null) {
            return null;
        }
        for (OrderStatusEnum e : values()) {
            if (e.name().equals(name)) {
                return e.getLabel();
            }
        }
        return name;
    }
}
