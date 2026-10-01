package com.retail.erp.common;

import lombok.Getter;

/**
 * 业务错误码
 *
 * 分段约定：
 *   200        成功
 *   4xx        通用客户端错误
 *   4001~4099  业务规则错误（库存、单据状态等）
 *   500        服务端错误
 *   5001~      第三方依赖错误
 */
@Getter
public enum ErrorCode {

    SUCCESS(200, "操作成功"),

    BAD_REQUEST(400, "请求参数不合法"),
    UNAUTHORIZED(401, "登录已过期，请重新登录"),
    FORBIDDEN(403, "没有该操作权限"),
    NOT_FOUND(404, "数据不存在"),
    METHOD_NOT_ALLOWED(405, "请求方式不支持"),

    STOCK_NOT_ENOUGH(4001, "库存不足"),
    STOCK_CONCURRENT_MODIFY(4002, "库存正被其他操作修改，请重试"),
    ORDER_STATUS_ILLEGAL(4003, "单据当前状态不允许该操作"),
    DATA_DUPLICATE(4004, "数据已存在，请勿重复提交"),
    BATCH_EXPIRED(4005, "所选批次已过期，不能出库"),
    BATCH_NOT_ENOUGH(4006, "所选批次库存不足"),
    CHECK_ALREADY_FINISHED(4007, "盘点单已调整完成，不能重复提交"),
    RETURN_QTY_EXCEED(4008, "退货数量超过原单可退数量"),
    ACCOUNT_DISABLED(4009, "账号已被禁用，请联系管理员"),
    LOGIN_FAILED(4010, "账号或密码错误"),

    INTERNAL_ERROR(500, "系统繁忙，请稍后重试"),
    DB_ERROR(501, "数据操作失败"),
    AI_SERVICE_ERROR(5001, "AI 服务暂不可用，请稍后重试"),
    AI_NO_API_KEY(5002, "未配置 AI 密钥，请在配置文件中填写");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
