/** 金额格式化：¥1,234.56 */
export function formatMoney(v?: number | null): string {
  const n = Number(v)
  if (v === null || v === undefined || Number.isNaN(n)) return '¥0.00'
  return '¥' + n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** 数字千分位格式化 */
export function formatNumber(v?: number | null): string {
  const n = Number(v)
  if (v === null || v === undefined || Number.isNaN(n)) return '0'
  return n.toLocaleString('zh-CN')
}

/** 时间格式化：去掉 T，截取到秒 */
export function formatTime(v?: string | null): string {
  if (!v) return '-'
  return String(v).replace('T', ' ').slice(0, 19)
}
