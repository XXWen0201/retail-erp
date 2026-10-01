package com.retail.erp.module.stock.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 盘点单返回对象 */
@Data
public class StockCheckVO {

    private Long id;
    private String checkNo;
    private String status;
    private String statusLabel;
    private LocalDate checkDate;

    /** 差异总量（各明细差异的绝对值之和），用来衡量这次盘点的「乱」的程度 */
    private Integer totalDiffQuantity;

    /** 有差异的商品数 */
    private Integer diffItemCount;

    private String operatorName;
    private LocalDateTime finishTime;
    private String remark;

    private List<StockCheckItemVO> items;
}
