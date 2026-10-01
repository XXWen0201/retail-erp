package com.retail.erp.common;

import lombok.Getter;

/**
 * 业务异常
 *
 * 约定：所有可预期的业务失败都抛这个异常，由 GlobalExceptionHandler 统一转成响应体。
 * 绝不要把数据库异常、空指针等底层异常直接抛给前端。
 */
@Getter
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 用更具体的文案覆盖默认提示，例如「商品【可乐】库存不足，当前 3，需要 10」 */
    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    // ---------- 语义化快捷方法，让业务代码读起来更顺 ----------

    public static BizException notFound(String what) {
        return new BizException(ErrorCode.NOT_FOUND, what + "不存在");
    }

    public static BizException badRequest(String message) {
        return new BizException(ErrorCode.BAD_REQUEST, message);
    }

    public static BizException stockNotEnough(String productName, int current, int need) {
        return new BizException(ErrorCode.STOCK_NOT_ENOUGH,
                String.format("商品【%s】库存不足，当前 %d，需要 %d", productName, current, need));
    }

    public static BizException statusIllegal(String message) {
        return new BizException(ErrorCode.ORDER_STATUS_ILLEGAL, message);
    }

    public static BizException duplicate(String message) {
        return new BizException(ErrorCode.DATA_DUPLICATE, message);
    }
}
