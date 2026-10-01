package com.retail.erp.module.stock.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** 新建盘点单入参 */
public record StockCheckCreateDTO(

        @NotNull(message = "请选择盘点日期")
        LocalDate checkDate,

        @Size(max = 255, message = "备注不能超过 255 个字符")
        String remark,

        /**
         * 参与盘点的商品 id。
         * 不传或传空则对全部在售商品建账 —— 全盘。
         * 传了就只盘这几样 —— 抽盘，日常更常用，因为不可能天天把整个门店数一遍。
         */
        List<Long> productIds
) {
}
