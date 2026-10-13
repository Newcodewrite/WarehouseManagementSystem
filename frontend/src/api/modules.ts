import { get, post, put, del, postT, upload, type Paginated } from './index'

/* ==================== 类型定义 ==================== */

export interface UserInfo {
  id: number
  username: string
  nickname: string
  roles: string[]
  perms: string[]
}

export interface LoginResult {
  token: string
  user: UserInfo
}

export interface DashboardStats {
  productCount: number
  warehouseCount: number
  supplierCount: number
  customerCount: number
  inboundPending: number
  outboundPending: number
  orderTransit: number
  vehicleIdle: number
  inventoryTotal: number
  inventoryValue: number
  alertCount: number
}

export interface TrendData {
  labels: string[]
  inData: number[]
  outData: number[]
}

export interface KpiData {
  turnoverRate30: number
  outQty30: number
  outQtyAll: number
  ageDistribution: { name: string; value: number }[]
  dormantTop10: { productCode: string; productName: string; quantity: number; stockValue: number }[]
  inboundOnTimeRate: number
  inboundTotal: number
  stocktakeAccuracy: number
  stocktakeCount: number
}

export interface Location {
  id: number
  warehouseId: number
  warehouseName?: string
  code: string
  zone: string
  zoneName?: string
  capacity: number
  used?: number
  status: number
  remark?: string
  createTime?: string
}

export interface StocktakeItem {
  id: number
  productId: number
  productCode?: string
  productName?: string
  unit?: string
  bookQty: number
  actualQty?: number | null
  diffQty: number
}

export interface StocktakeOrder {
  id: number
  orderNo: string
  warehouseId: number
  warehouseName?: string
  type: number
  typeName?: string
  status: number
  statusName?: string
  totalCount: number
  gainQty: number
  lossQty: number
  remark?: string
  createBy?: string
  auditBy?: string
  auditTime?: string
  createTime?: string
  items?: StocktakeItem[]
}

export interface ReturnItem {
  id: number
  productId: number
  productCode?: string
  productName?: string
  unit?: string
  quantity: number
  qcGrade?: number | null
  qcGradeName?: string
  qcRemark?: string
}

export interface ReturnOrder {
  id: number
  orderNo: string
  warehouseId: number
  warehouseName?: string
  customerId?: number
  customerName?: string
  status: number
  statusName?: string
  totalQty: number
  resaleQty: number
  scrapQty: number
  remark?: string
  createBy?: string
  auditBy?: string
  auditTime?: string
  createTime?: string
  items?: ReturnItem[]
}

export interface WmsTask {
  id: number
  taskNo: string
  type: number
  typeName?: string
  refType?: string
  refId?: number
  warehouseId: number
  warehouseName?: string
  locationId?: number
  content?: string
  assigneeId?: number
  assigneeName?: string
  status: number
  statusName?: string
  finishTime?: string
  remark?: string
  createBy?: string
  createTime?: string
}

export interface BatchInfo {
  warehouseName: string
  productCode: string
  productName: string
  batchNo: string
  quantity: number
  productionDate?: string
  expiryDate?: string
  daysLeft?: number | null
  expired: boolean
  nearExpiry: boolean
  inboundOrderNo: string
}

export interface Product {
  id: number
  code: string
  name: string
  category?: string
  unit?: string
  price?: number
  warnStock?: number
  status: number
  remark?: string
  createTime?: string
}

export interface Warehouse {
  id: number
  code: string
  name: string
  address?: string
  capacity?: number
  managerName?: string
  managerPhone?: string
  status: number
  remark?: string
  createTime?: string
}

export interface Partner {
  id: number
  code: string
  name: string
  contactName?: string
  contactPhone?: string
  address?: string
  remark?: string
  createTime?: string
}

export interface OrderItem {
  id?: number
  productId: number
  productCode?: string
  productName?: string
  unit?: string
  quantity: number
  price: number
}

export interface WmsOrder {
  id: number
  orderNo: string
  warehouseName: string
  supplierName?: string
  customerName?: string
  totalAmount?: number
  status: number
  remark?: string
  createBy?: string
  auditBy?: string
  auditTime?: string
  createTime?: string
  itemCount?: number
  items?: OrderItem[]
}

export interface Inventory {
  id: number
  warehouseId: number
  warehouseName: string
  productId: number
  productCode: string
  productName: string
  category?: string
  unit?: string
  quantity: number
  warnStock?: number
  alert: boolean
}

export interface InventoryAlert {
  warehouseName: string
  productCode: string
  productName: string
  category?: string
  unit?: string
  quantity: number
  warnStock: number
  suggestQty: number
}

export interface InventoryRecord {
  id: number
  warehouseName: string
  productName: string
  type: 'IN' | 'OUT' | 'ADJUST'
  quantity: number
  afterQuantity: number
  orderId?: number
  remark?: string
  createBy?: string
  createTime?: string
}

export interface LogisticsOrder {
  id: number
  orderNo: string
  outboundOrderNo?: string
  receiverName: string
  receiverPhone: string
  receiverAddress: string
  vehicleCode?: string
  driverName?: string
  status: number
  remark?: string
  createTime?: string
}

export interface LogTrack {
  location: string
  content: string
  trackTime: string
}

export interface Vehicle {
  id: number
  code: string
  vehicleType?: string
  driverName?: string
  driverPhone?: string
  capacityKg?: number
  status: number
  remark?: string
  createTime?: string
}

export interface SysUser {
  id: number
  username: string
  nickname?: string
  phone?: string
  email?: string
  status: number
  roleIds: number[]
  createTime?: string
}

export interface UserForm {
  username: string
  password?: string
  nickname?: string
  phone?: string
  email?: string
  status: number
  roleIds: number[]
}

export interface SysRole {
  id: number
  code: string
  name: string
  perms?: string
  remark?: string
  createTime?: string
}

export interface RoleForm {
  code: string
  name: string
  perms?: string
  remark?: string
}

export interface SysLog {
  id: number
  username?: string
  module?: string
  description?: string
  method?: string
  params?: string
  ip?: string
  costMs?: number
  status: number
  errorMsg?: string
  createTime?: string
}

export interface AiResult {
  reply: string
  mode: 'ai' | 'rule'
}

export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
}

export interface FilePreview {
  fileToken: string
  fileName: string
  rowCount: number
  columns: string[]
  detectedType: 'PRODUCT' | 'STOCK' | 'UNKNOWN'
  preview: Record<string, string>[]
}

/* ==================== 认证 ==================== */

export const authApi = {
  login: (data: { username: string; password: string }) => post<LoginResult>('/auth/login', data),
  me: () => get<UserInfo>('/auth/me'),
  logout: () => post<void>('/auth/logout')
}

/* ==================== 驾驶舱 ==================== */

export const dashboardApi = {
  stats: () => get<DashboardStats>('/dashboard/stats'),
  trend: (days = 7) => get<TrendData>('/dashboard/trend', { days }),
  kpi: () => get<KpiData>('/dashboard/kpi')
}

/* ==================== 基础数据 ==================== */

export const productApi = {
  page: (params: Record<string, unknown>) => get<Paginated<Product>>('/base/product/page', params),
  list: () => get<Product[]>('/base/product/list'),
  add: (data: Partial<Product>) => post<void>('/base/product', data),
  update: (data: Product) => put<void>('/base/product', data),
  remove: (id: number) => del<void>(`/base/product/${id}`)
}

export const warehouseApi = {
  page: (params: Record<string, unknown>) => get<Paginated<Warehouse>>('/base/warehouse/page', params),
  list: () => get<Warehouse[]>('/base/warehouse/list'),
  add: (data: Partial<Warehouse>) => post<void>('/base/warehouse', data),
  update: (data: Warehouse) => put<void>('/base/warehouse', data),
  remove: (id: number) => del<void>(`/base/warehouse/${id}`)
}

export const supplierApi = {
  page: (params: Record<string, unknown>) => get<Paginated<Partner>>('/base/supplier/page', params),
  add: (data: Partial<Partner>) => post<void>('/base/supplier', data),
  update: (data: Partner) => put<void>('/base/supplier', data),
  remove: (id: number) => del<void>(`/base/supplier/${id}`)
}

export const customerApi = {
  page: (params: Record<string, unknown>) => get<Paginated<Partner>>('/base/customer/page', params),
  add: (data: Partial<Partner>) => post<void>('/base/customer', data),
  update: (data: Partner) => put<void>('/base/customer', data),
  remove: (id: number) => del<void>(`/base/customer/${id}`)
}

export const locationApi = {
  page: (params: Record<string, unknown>) => get<Paginated<Location>>('/base/location/page', params),
  list: (warehouseId: number) => get<Location[]>('/base/location/list', { warehouseId }),
  add: (data: Partial<Location>) => post<void>('/base/location', data),
  update: (data: Location) => put<void>('/base/location', data),
  remove: (id: number) => del<void>(`/base/location/${id}`)
}

/* ==================== 仓储作业 ==================== */

export const inboundApi = {
  page: (params: Record<string, unknown>) => get<Paginated<WmsOrder>>('/wms/inbound/page', params),
  detail: (id: number) => get<WmsOrder>(`/wms/inbound/${id}`),
  add: (data: { warehouseId: number; supplierId: number; remark?: string; items: OrderItem[] }) =>
    post<void>('/wms/inbound', data),
  audit: (id: number, approved: boolean) => put<void>(`/wms/inbound/${id}/audit`, undefined, { approved }),
  remove: (id: number) => del<void>(`/wms/inbound/${id}`)
}

export const outboundApi = {
  page: (params: Record<string, unknown>) => get<Paginated<WmsOrder>>('/wms/outbound/page', params),
  detail: (id: number) => get<WmsOrder>(`/wms/outbound/${id}`),
  add: (data: { warehouseId: number; customerId: number; remark?: string; items: OrderItem[] }) =>
    post<void>('/wms/outbound', data),
  audit: (id: number, approved: boolean) => put<void>(`/wms/outbound/${id}/audit`, undefined, { approved }),
  remove: (id: number) => del<void>(`/wms/outbound/${id}`)
}

/* ==================== 库存 ==================== */

export const inventoryApi = {
  page: (params: Record<string, unknown>) => get<Paginated<Inventory>>('/wms/inventory/page', params),
  alertPage: (params: Record<string, unknown>) => get<Paginated<InventoryAlert>>('/wms/inventory/alert/page', params),
  recordPage: (params: Record<string, unknown>) => get<Paginated<InventoryRecord>>('/wms/inventory/record/page', params),
  adjust: (data: { warehouseId: number; productId: number; quantity: number; remark?: string }) =>
    post<void>('/wms/inventory/adjust', data)
}

export const stocktakeApi = {
  page: (params: Record<string, unknown>) => get<Paginated<StocktakeOrder>>('/wms/stocktake/page', params),
  detail: (id: number) => get<StocktakeOrder>(`/wms/stocktake/${id}`),
  create: (data: { warehouseId: number; type: number; remark?: string }) => post<number>('/wms/stocktake', data),
  submit: (id: number, counts: { itemId: number; actualQty: number | null }[]) =>
    put<void>(`/wms/stocktake/${id}/submit`, { counts }),
  audit: (id: number, approved: boolean) => put<void>(`/wms/stocktake/${id}/audit`, undefined, { approved }),
  cancel: (id: number) => put<void>(`/wms/stocktake/${id}/cancel`)
}

export const returnApi = {
  page: (params: Record<string, unknown>) => get<Paginated<ReturnOrder>>('/wms/return/page', params),
  detail: (id: number) => get<ReturnOrder>(`/wms/return/${id}`),
  create: (data: { warehouseId: number; customerId?: number; remark?: string; items: { productId: number; quantity: number }[] }) =>
    post<number>('/wms/return', data),
  receive: (id: number) => put<void>(`/wms/return/${id}/receive`),
  qc: (id: number, grades: { itemId: number; qcGrade: number; qcRemark?: string }[]) =>
    put<void>(`/wms/return/${id}/qc`, { grades }),
  cancel: (id: number) => put<void>(`/wms/return/${id}/cancel`)
}

export const taskApi = {
  page: (params: Record<string, unknown>) => get<Paginated<WmsTask>>('/wms/task/page', params),
  assignees: () => get<{ id: number; name: string }[]>('/wms/task/assignees'),
  assign: (id: number, userId: number) => put<void>(`/wms/task/${id}/assign`, undefined, { userId }),
  start: (id: number) => put<void>(`/wms/task/${id}/start`),
  complete: (id: number) => put<void>(`/wms/task/${id}/complete`),
  cancel: (id: number) => put<void>(`/wms/task/${id}/cancel`)
}

export const batchApi = {
  list: (params: Record<string, unknown>) => get<BatchInfo[]>('/wms/batch/list', params)
}

/* ==================== 物流运输 ==================== */

export const logisticsApi = {
  orderPage: (params: Record<string, unknown>) => get<Paginated<LogisticsOrder>>('/logistics/order/page', params),
  addOrder: (data: { receiverName: string; receiverPhone: string; receiverAddress: string; remark?: string; outboundOrderId?: number }) =>
    post<void>('/logistics/order', data),
  dispatch: (id: number, vehicleId: number) => put<void>(`/logistics/order/${id}/dispatch`, { vehicleId }),
  deliver: (id: number) => put<void>(`/logistics/order/${id}/deliver`),
  cancel: (id: number) => put<void>(`/logistics/order/${id}/cancel`),
  addTrack: (id: number, data: { location: string; content: string }) => post<void>(`/logistics/order/${id}/track`, data),
  tracks: (id: number) => get<LogTrack[]>(`/logistics/order/${id}/tracks`)
}

export const vehicleApi = {
  page: (params: Record<string, unknown>) => get<Paginated<Vehicle>>('/logistics/vehicle/page', params),
  list: () => get<Vehicle[]>('/logistics/vehicle/list'),
  add: (data: Partial<Vehicle>) => post<void>('/logistics/vehicle', data),
  update: (data: Vehicle) => put<void>('/logistics/vehicle', data),
  remove: (id: number) => del<void>(`/logistics/vehicle/${id}`)
}

/* ==================== 系统管理 ==================== */

export const userApi = {
  page: (params: Record<string, unknown>) => get<Paginated<SysUser>>('/system/user/page', params),
  add: (data: UserForm) => post<void>('/system/user', data),
  update: (data: UserForm) => put<void>('/system/user', data),
  remove: (id: number) => del<void>(`/system/user/${id}`),
  resetPassword: (id: number, password: string) => put<void>(`/system/user/${id}/password`, { password })
}

export const roleApi = {
  page: (params: Record<string, unknown>) => get<Paginated<SysRole>>('/system/role/page', params),
  list: () => get<SysRole[]>('/system/role/list'),
  add: (data: RoleForm) => post<void>('/system/role', data),
  update: (data: RoleForm) => put<void>('/system/role', data),
  remove: (id: number) => del<void>(`/system/role/${id}`)
}

export const logApi = {
  page: (params: Record<string, unknown>) => get<Paginated<SysLog>>('/system/log/page', params)
}

/* ==================== AI ==================== */

export const aiApi = {
  chat: (data: { message: string; history: ChatMessage[] }) => postT<AiResult>('/ai/chat', data, 300000),
  stockAdvice: () => get<AiResult>('/ai/stock-advice'),
  scheduleAdvice: () => get<AiResult>('/ai/schedule-advice'),
  trackExplain: (orderId: number) => get<AiResult>(`/ai/track-explain/${orderId}`),
  uploadFile: (file: File) => {
    const fd = new FormData()
    fd.append('file', file)
    return upload<FilePreview>('/ai/file/upload', fd)
  }
}
