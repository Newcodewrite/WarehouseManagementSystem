import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { TOKEN_KEY } from '@/api'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    perm?: string
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/layout/Layout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/Dashboard.vue'), meta: { title: '驾驶舱' } },
      { path: 'base/product', name: 'Product', component: () => import('@/views/base/Product.vue'), meta: { title: '商品管理', perm: 'base:product' } },
      { path: 'base/warehouse', name: 'Warehouse', component: () => import('@/views/base/Warehouse.vue'), meta: { title: '仓库管理', perm: 'base:warehouse' } },
      { path: 'base/supplier', name: 'Supplier', component: () => import('@/views/base/Supplier.vue'), meta: { title: '供应商管理', perm: 'base:supplier' } },
      { path: 'base/customer', name: 'Customer', component: () => import('@/views/base/Customer.vue'), meta: { title: '客户管理', perm: 'base:customer' } },
      { path: 'base/location', name: 'Location', component: () => import('@/views/base/Location.vue'), meta: { title: '库位管理', perm: 'base:location' } },
      { path: 'wms/inbound', name: 'Inbound', component: () => import('@/views/wms/Inbound.vue'), meta: { title: '入库管理', perm: 'wms:inbound' } },
      { path: 'wms/outbound', name: 'Outbound', component: () => import('@/views/wms/Outbound.vue'), meta: { title: '出库管理', perm: 'wms:outbound' } },
      { path: 'wms/inventory', name: 'Inventory', component: () => import('@/views/wms/Inventory.vue'), meta: { title: '库存查询', perm: 'wms:inventory' } },
      { path: 'wms/alert', name: 'Alert', component: () => import('@/views/wms/Alert.vue'), meta: { title: '库存预警' } },
      { path: 'wms/stocktake', name: 'Stocktake', component: () => import('@/views/wms/Stocktake.vue'), meta: { title: '盘点管理', perm: 'wms:stocktake' } },
      { path: 'wms/return', name: 'Return', component: () => import('@/views/wms/Return.vue'), meta: { title: '退货管理', perm: 'wms:return' } },
      { path: 'wms/task', name: 'Task', component: () => import('@/views/wms/Task.vue'), meta: { title: '作业任务', perm: 'wms:task' } },
      { path: 'logistics/order', name: 'LogisticsOrder', component: () => import('@/views/logistics/LogisticsOrder.vue'), meta: { title: '物流订单', perm: 'logistics:order' } },
      { path: 'logistics/vehicle', name: 'Vehicle', component: () => import('@/views/logistics/Vehicle.vue'), meta: { title: '车辆管理', perm: 'logistics:vehicle' } },
      { path: 'ai/assistant', name: 'AiChat', component: () => import('@/views/AiChat.vue'), meta: { title: 'AI 助手' } },
      { path: 'system/user', name: 'SysUser', component: () => import('@/views/system/User.vue'), meta: { title: '用户管理', perm: 'system:user' } },
      { path: 'system/role', name: 'SysRole', component: () => import('@/views/system/Role.vue'), meta: { title: '角色管理', perm: 'system:role' } },
      { path: 'system/log', name: 'SysLog', component: () => import('@/views/system/Log.vue'), meta: { title: '操作日志', perm: 'system:log' } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (!token && to.path !== '/login') {
    return { path: '/login' }
  }
  if (token && to.path === '/login') {
    return { path: '/dashboard' }
  }
  document.title = to.meta.title ? `${to.meta.title} - 智能物流仓储管理系统` : '智能物流仓储管理系统'
  return true
})

export default router
