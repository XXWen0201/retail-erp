package com.retail.erp.module.basic.dto;

/**
 * 下拉选项通用结构
 *
 * 统一用 value / label 命名，前端 el-select 可以直接
 * :value="item.value" :label="item.label" 绑定，不需要每个页面写字段映射。
 */
public record OptionVO(Long value, String label) {
}
