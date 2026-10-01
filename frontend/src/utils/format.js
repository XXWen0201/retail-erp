/**
 * 通用格式化工具。金额统一两位小数，数量保留原样（后端是整数或 4 位小数的成本价）。
 */

/** 金额：1234.5 -> "1,234.50" */
export function money(value, digits = 2) {
  if (value === null || value === undefined || value === '') return '0.00'
  const num = Number(value)
  if (Number.isNaN(num)) return '0.00'
  return num.toLocaleString('zh-CN', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits
  })
}

/** 带货币符号 */
export function yuan(value, digits = 2) {
  return '¥' + money(value, digits)
}

/** 数量：整数不带小数，4 位成本价保留小数 */
export function num(value, digits = 0) {
  if (value === null || value === undefined || value === '') return '0'
  const n = Number(value)
  if (Number.isNaN(n)) return '0'
  return digits > 0 ? n.toFixed(digits) : String(Math.round(n))
}

/** 百分比 */
export function percent(value, digits = 2) {
  if (value === null || value === undefined || value === '') return '0.00%'
  return Number(value).toFixed(digits) + '%'
}

/** 今天 yyyy-MM-dd */
export function today() {
  return toDateStr(new Date())
}

/** n 天前的 yyyy-MM-dd */
export function daysAgo(n) {
  const d = new Date()
  d.setDate(d.getDate() - n)
  return toDateStr(d)
}

export function toDateStr(date) {
  const d = date instanceof Date ? date : new Date(date)
  if (Number.isNaN(d.getTime())) return ''
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

/** 日期时间只取到分钟，表格里更好看 */
export function shortTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

/** 空值占位 */
export function dash(value) {
  return value === null || value === undefined || value === '' ? '-' : value
}

/* ---------------- 业务字典 ---------------- */

export const PURCHASE_STATUS = {
  DRAFT: { label: '草稿', type: 'info' },
  PENDING: { label: '待入库', type: 'warning' },
  FINISHED: { label: '已入库', type: 'success' },
  CANCELED: { label: '已作废', type: 'danger' }
}

export const SALE_STATUS = {
  DRAFT: { label: '草稿', type: 'info' },
  FINISHED: { label: '已完成', type: 'success' },
  CANCELED: { label: '已作废', type: 'danger' }
}

export const RETURN_TYPE = {
  PURCHASE_RETURN: { label: '采购退货', type: 'warning' },
  SALE_RETURN: { label: '销售退货', type: 'success' }
}

export const STOCK_STATUS = {
  NORMAL: { label: '正常', type: 'success' },
  LOW: { label: '低于下限', type: 'warning' },
  OVER: { label: '高于上限', type: 'danger' },
  OUT: { label: '零库存', type: 'danger' }
}

export const EXPIRE_STATUS = {
  NORMAL: { label: '正常', type: 'success' },
  NEAR_EXPIRY: { label: '临期', type: 'warning' },
  EXPIRED: { label: '已过期', type: 'danger' }
}

export const ALERT_TYPE = {
  LOW_STOCK: { label: '低于库存下限', type: 'warning' },
  OVER_STOCK: { label: '高于库存上限', type: 'danger' },
  OUT_OF_STOCK: { label: '库存断货', type: 'danger' },
  NEAR_EXPIRY: { label: '批次临期', type: 'warning' },
  EXPIRED: { label: '批次过期', type: 'danger' }
}

export const ALERT_STATUS = {
  UNHANDLED: { label: '未处理', type: 'danger' },
  HANDLED: { label: '已处理', type: 'success' },
  IGNORED: { label: '已忽略', type: 'info' }
}

export const URGENCY = {
  HIGH: { label: '紧急', type: 'danger' },
  MEDIUM: { label: '关注', type: 'warning' },
  LOW: { label: '正常', type: 'success' }
}

export const BIZ_TYPE = {
  PURCHASE_IN: '采购入库',
  SALE_OUT: '销售出库',
  SALE_CANCEL: '销售作废回滚',
  PURCHASE_RETURN: '采购退货',
  SALE_RETURN: '销售退货',
  CHECK_PROFIT: '盘盈入库',
  CHECK_LOSS: '盘亏出库',
  CHECK_ADJUST: '盘点调整',
  MANUAL_ADJUST: '手工调整'
}
