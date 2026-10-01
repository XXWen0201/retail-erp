/**
 * 展示层格式化工具
 *
 * 后端返回的都是原始数值（分/元、ISO 日期、枚举英文码），
 * 统一在这里转成中文界面文案，页面里不再散落判断逻辑。
 */

/** 金额：保留两位小数，带千分位 */
export function money(value) {
  const n = Number(value)
  if (!isFinite(n)) return '0.00'
  return n.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })
}

/** 数量：整数不带小数，小数最多两位 */
export function quantity(value) {
  const n = Number(value)
  if (!isFinite(n)) return '0'
  return Number.isInteger(n) ? String(n) : n.toFixed(2)
}

/** 把 "2026-09-29 09:20:00" 截成 "2026-09-29 09:20" */
export function shortTime(value) {
  if (!value) return '-'
  const s = String(value).replace('T', ' ')
  return s.length >= 16 ? s.slice(0, 16) : s
}

/** 只要日期部分 */
export function dateOnly(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 10)
}

/** 今天的 yyyy-MM-dd，用于日期选择器默认值 */
export function today() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

/** N 天前的 yyyy-MM-dd */
export function daysAgo(n) {
  const d = new Date()
  d.setDate(d.getDate() - n)
  const p = (v) => String(v).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

/** 库存状态：后端返回英文枚举，这里转中文 + 配色 */
const STOCK_STATUS = {
  NORMAL: { text: '正常', type: 'success' },
  LOW: { text: '低于下限', type: 'danger' },
  OVER: { text: '高于上限', type: 'warning' },
  OUT: { text: '已断货', type: 'danger' }
}

export function stockStatus(code) {
  return STOCK_STATUS[code] || { text: '未知', type: 'info' }
}

/** 批次保质期状态 */
const EXPIRE_STATUS = {
  NORMAL: { text: '正常', type: 'success' },
  NEAR_EXPIRY: { text: '临期', type: 'warning' },
  EXPIRED: { text: '已过期', type: 'danger' }
}

export function expireStatus(code) {
  return EXPIRE_STATUS[code] || { text: '正常', type: 'success' }
}

/** 单据状态 */
export const ORDER_STATUS = {
  DRAFT: { text: '草稿', type: 'info' },
  PENDING: { text: '待入库', type: 'warning' },
  FINISHED: { text: '已完成', type: 'success' },
  CANCELED: { text: '已作废', type: 'info' }
}

export function orderStatus(code) {
  return ORDER_STATUS[code] || { text: code || '-', type: 'info' }
}

/** 补货紧急度 */
const URGENCY = {
  HIGH: { text: '紧急', type: 'danger' },
  MEDIUM: { text: '关注', type: 'warning' },
  LOW: { text: '充足', type: 'success' }
}

export function urgency(code) {
  return URGENCY[code] || { text: '关注', type: 'warning' }
}

/** 权限角色 */
const ROLE = {
  ADMIN: '管理员',
  MANAGER: '店长',
  STAFF: '店员'
}

export function roleText(code) {
  return ROLE[code] || code || '-'
}

/** 距过期天数 → 人话 */
export function expireDesc(days) {
  const n = Number(days)
  if (!isFinite(n)) return '-'
  if (n < 0) return `已过期 ${Math.abs(n)} 天`
  if (n === 0) return '今天到期'
  return `${n} 天后到期`
}
