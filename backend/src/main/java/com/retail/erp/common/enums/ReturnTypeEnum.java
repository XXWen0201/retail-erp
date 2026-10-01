package com.retail.erp.common.enums;

import lombok.Getter;

/**
 * 退货类型
 *
 * PURCHASE_RETURN 采购退货：把货退给供应商，库存减少，走 BizTypeEnum.PURCHASE_RETURN_OUT
 * SALE_RETURN     销售退货：顾客把货退回来，库存增加，走 BizTypeEnum.SALE_RETURN_IN
 */
@Getter
public enum ReturnTypeEnum {

    PURCHASE_RETURN("采购退货", BizTypeEnum.PURCHASE_RETURN_OUT),
    SALE_RETURN("销售退货", BizTypeEnum.SALE_RETURN_IN);

    private final String label;
    private final BizTypeEnum bizType;

    ReturnTypeEnum(String label, BizTypeEnum bizType) {
        this.label = label;
        this.bizType = bizType;
    }
}
