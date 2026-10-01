package com.retail.erp.common.enums;

import lombok.Getter;

/**
 * 预警处理状态
 *
 * 用枚举而不是各处裸写字符串：预警状态是会被前端用来做筛选条件的，
 * 一旦某处拼错成 "unhandled"，那条预警就会从所有筛选结果里凭空消失。
 */
@Getter
public enum AlertStatusEnum {

    UNHANDLED("未处理"),
    HANDLED("已处理"),
    IGNORED("已忽略");

    private final String label;

    AlertStatusEnum(String label) {
        this.label = label;
    }

    public static String labelOf(String name) {
        if (name == null) {
            return null;
        }
        for (AlertStatusEnum e : values()) {
            if (e.name().equals(name)) {
                return e.getLabel();
            }
        }
        return name;
    }
}
