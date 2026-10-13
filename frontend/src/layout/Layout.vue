<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UserFilled } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { authApi, userApi } from '@/api/modules'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const collapsed = ref(false)

interface MenuLeaf {
  path: string
  title: string
  icon: string
  perm?: string
}
interface MenuGroup {
  title: string
  icon: string
  children: MenuLeaf[]
}
type MenuNode = MenuLeaf | MenuGroup

const menus: MenuNode[] = [
  { path: '/dashboard', title: '驾驶舱', icon: 'Odometer' },
  {
    title: '基础数据',
    icon: 'Files',
    children: [
      { path: '/base/product', title: '商品管理', icon: 'Goods', perm: 'base:product' },
      { path: '/base/warehouse', title: '仓库管理', icon: 'House', perm: 'base:warehouse' },
      { path: '/base/supplier', title: '供应商管理', icon: 'OfficeBuilding', perm: 'base:supplier' },
      { path: '/base/customer', title: '客户管理', icon: 'User', perm: 'base:customer' },
      { path: '/base/location', title: '库位管理', icon: 'Location', perm: 'base:location' }
    ]
  },
  {
    title: '仓储作业',
    icon: 'Box',
    children: [
      { path: '/wms/inbound', title: '入库管理', icon: 'Download', perm: 'wms:inbound' },
      { path: '/wms/outbound', title: '出库管理', icon: 'Upload', perm: 'wms:outbound' },
      { path: '/wms/inventory', title: '库存查询', icon: 'DataLine', perm: 'wms:inventory' },
      { path: '/wms/alert', title: '库存预警', icon: 'Bell', perm: 'wms:inventory' },
      { path: '/wms/stocktake', title: '盘点管理', icon: 'Tickets', perm: 'wms:stocktake' },
      { path: '/wms/return', title: '退货管理', icon: 'RefreshLeft', perm: 'wms:return' },
      { path: '/wms/task', title: '作业任务', icon: 'Operation', perm: 'wms:task' }
    ]
  },
  {
    title: '物流运输',
    icon: 'Location',
    children: [
      { path: '/logistics/order', title: '物流订单', icon: 'List', perm: 'logistics:order' },
      { path: '/logistics/vehicle', title: '车辆管理', icon: 'Van', perm: 'logistics:vehicle' }
    ]
  },
  { path: '/ai/assistant', title: 'AI 助手', icon: 'ChatDotRound' },
  {
    title: '系统管理',
    icon: 'Setting',
    children: [
      { path: '/system/user', title: '用户管理', icon: 'User', perm: 'system:user' },
      { path: '/system/role', title: '角色管理', icon: 'Avatar', perm: 'system:role' },
      { path: '/system/log', title: '操作日志', icon: 'Document', perm: 'system:log' }
    ]
  }
]

function leafVisible(leaf: MenuLeaf): boolean {
  return !leaf.perm || userStore.hasPerm(leaf.perm)
}

const visibleMenus = computed<MenuNode[]>(() =>
  menus
    .map((m) => {
      if ('children' in m) {
        const children = m.children.filter(leafVisible)
        return children.length ? { ...m, children } : null
      }
      return leafVisible(m) ? m : null
    })
    .filter((m): m is MenuNode => m !== null)
)

const breadcrumbs = computed(() =>
  route.matched
    .filter((r) => r.meta?.title)
    .map((r) => ({ path: r.path, title: r.meta.title as string }))
)

/* ---------- 修改密码 ---------- */
const pwdDialogVisible = ref(false)
const newPassword = ref('')
const pwdSaving = ref(false)

async function handleChangePassword() {
  if (!newPassword.value) {
    ElMessage.warning('请输入新密码')
    return
  }
  if (!userStore.user) return
  pwdSaving.value = true
  try {
    await userApi.resetPassword(userStore.user.id, newPassword.value)
    ElMessage.success('密码修改成功')
    pwdDialogVisible.value = false
  } finally {
    pwdSaving.value = false
  }
}

/* ---------- 退出登录 ---------- */
async function handleCommand(cmd: string) {
  if (cmd === 'password') {
    newPassword.value = ''
    pwdDialogVisible.value = true
    return
  }
  if (cmd === 'logout') {
    try {
      await ElMessageBox.confirm('确定退出登录吗？', '提示', {
        type: 'warning',
        confirmButtonText: '确定',
        cancelButtonText: '取消'
      })
    } catch {
      return
    }
    authApi.logout().catch(() => {})
    userStore.clear()
    ElMessage.success('已退出登录')
    router.push('/login')
  }
}
</script>

<template>
  <el-container class="layout">
    <!-- 左侧菜单 -->
    <el-aside :width="collapsed ? '64px' : '210px'" class="aside">
      <div v-if="!collapsed" class="logo">
        <el-icon :size="22"><Box /></el-icon>
        <span>智能物流仓储</span>
      </div>
      <div v-else class="logo logo-mini">
        <el-icon :size="22"><Box /></el-icon>
      </div>
      <el-menu
        class="menu"
        :default-active="route.path"
        :collapse="collapsed"
        :collapse-transition="false"
        router
        background-color="#001529"
        text-color="rgba(255,255,255,0.68)"
        active-text-color="#ffffff"
      >
        <template v-for="menu in visibleMenus" :key="'children' in menu ? menu.title : menu.path">
          <el-sub-menu v-if="'children' in menu" :index="menu.title">
            <template #title>
              <el-icon><component :is="menu.icon" /></el-icon>
              <span>{{ menu.title }}</span>
            </template>
            <el-menu-item v-for="child in menu.children" :key="child.path" :index="child.path">
              <el-icon><component :is="child.icon" /></el-icon>
              <span>{{ child.title }}</span>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="menu.path">
            <el-icon><component :is="menu.icon" /></el-icon>
            <span>{{ menu.title }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <el-container class="right">
      <!-- 顶部栏 -->
      <el-header class="header" height="56px">
        <div class="header-left">
          <el-icon class="collapse-btn" :size="20" @click="collapsed = !collapsed">
            <Expand v-if="collapsed" />
            <Fold v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path" :to="item.path">
              {{ item.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <el-dropdown trigger="click" @command="handleCommand">
          <span class="user-box">
            <el-avatar :size="30" class="user-avatar">
              <el-icon><UserFilled /></el-icon>
            </el-avatar>
            <span class="user-name">{{ userStore.user?.nickname || userStore.user?.username || '用户' }}</span>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="password">修改密码</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>

      <!-- 内容区 -->
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>

    <!-- 修改密码弹窗 -->
    <el-dialog v-model="pwdDialogVisible" title="修改密码" width="420px" destroy-on-close>
      <el-form label-width="80px" @submit.prevent>
        <el-form-item label="新密码">
          <el-input v-model="newPassword" type="password" show-password placeholder="请输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="pwdSaving" @click="handleChangePassword">确定</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100vh;
}

.aside {
  background-color: #001529;
  overflow-x: hidden;
  transition: width 0.25s;
}

.logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  white-space: nowrap;
}

.menu {
  border-right: none;
}

.menu :deep(.el-menu-item.is-active) {
  background-color: #409eff;
}

.menu :deep(.el-sub-menu__title:hover),
.menu :deep(.el-menu-item:hover) {
  background-color: rgba(255, 255, 255, 0.08);
}

.right {
  min-width: 0;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  padding: 0 16px;
  position: relative;
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.collapse-btn {
  cursor: pointer;
  color: #606266;
}

.user-box {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
}

.user-avatar {
  background-color: #409eff;
}

.user-name {
  font-size: 14px;
  color: #303133;
}

.main {
  padding: 0;
  background: #f5f7fa;
  overflow: auto;
}
</style>
