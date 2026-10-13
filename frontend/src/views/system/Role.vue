<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { roleApi, type RoleForm, type SysRole } from '@/api/modules'
import { useUserStore } from '@/stores/user'
import { formatTime } from '@/utils/format'

const userStore = useUserStore()
const canWrite = userStore.hasPerm('system:role')

/** 权限候选（12 个权限码） */
const PERM_OPTIONS: { code: string; label: string }[] = [
  { code: 'base:product', label: '商品管理' },
  { code: 'base:warehouse', label: '仓库管理' },
  { code: 'base:supplier', label: '供应商管理' },
  { code: 'base:customer', label: '客户管理' },
  { code: 'wms:inbound', label: '入库管理' },
  { code: 'wms:outbound', label: '出库管理' },
  { code: 'wms:inventory', label: '库存管理' },
  { code: 'logistics:order', label: '物流订单' },
  { code: 'logistics:vehicle', label: '车辆管理' },
  { code: 'system:user', label: '用户管理' },
  { code: 'system:role', label: '角色管理' },
  { code: 'system:log', label: '操作日志' }
]

const permLabelMap: Record<string, string> = Object.fromEntries(PERM_OPTIONS.map((o) => [o.code, o.label]))

/* ---------- 列表 ---------- */
const loading = ref(false)
const list = ref<SysRole[]>([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, keyword: '' })

async function loadData() {
  loading.value = true
  try {
    const params: Record<string, unknown> = { current: query.current, size: query.size }
    if (query.keyword) params.keyword = query.keyword
    const res = await roleApi.page(params)
    list.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.current = 1
  loadData()
}

function permLabels(perms?: string): string[] {
  return (perms || '').split(',').filter(Boolean)
}

/* ---------- 新增 / 编辑 ---------- */
const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<{ id?: number } & RoleForm & { permList: string[] }>({
  id: undefined,
  code: '',
  name: '',
  perms: '',
  remark: '',
  permList: []
})

const rules: FormRules = {
  code: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }]
}

function openEdit(row?: SysRole) {
  Object.assign(form, {
    id: row?.id,
    code: row?.code ?? '',
    name: row?.name ?? '',
    perms: row?.perms ?? '',
    remark: row?.remark ?? '',
    permList: permLabels(row?.perms)
  })
  dialogVisible.value = true
}

async function handleSave() {
  await formRef.value?.validate()
  saving.value = true
  try {
    const payload: RoleForm = {
      code: form.code,
      name: form.name,
      perms: form.permList.join(','),
      remark: form.remark
    }
    if (form.id) {
      await roleApi.update(payload)
      ElMessage.success('修改成功')
    } else {
      await roleApi.add(payload)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: SysRole) {
  try {
    await ElMessageBox.confirm(`确定删除角色「${row.name}」吗？`, '提示', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await roleApi.remove(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="角色编码 / 名称关键字"
        clearable
        style="width: 220px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
      <el-button v-if="canWrite" type="success" :icon="Plus" @click="openEdit()">新增角色</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="code" label="角色编码" min-width="130" />
      <el-table-column prop="name" label="角色名称" min-width="130" />
      <el-table-column label="权限" min-width="320">
        <template #default="{ row }">
          <template v-if="permLabels(row.perms).length">
            <el-tag v-for="p in permLabels(row.perms)" :key="p" size="small" style="margin: 2px 6px 2px 0">
              {{ permLabelMap[p] || p }}
            </el-tag>
          </template>
          <span v-else style="color: #909399">仅查询</span>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ row.remark || '-' }}</template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column v-if="canWrite" label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="query.current"
      v-model:page-size="query.size"
      class="pager"
      background
      layout="total, prev, pager, next, sizes"
      :total="total"
      :page-sizes="[10, 20, 50]"
      @current-change="loadData"
      @size-change="handleSearch"
    />

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑角色' : '新增角色'" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="角色编码" prop="code">
          <el-input v-model="form.code" placeholder="如：admin / op" />
        </el-form-item>
        <el-form-item label="角色名称" prop="name">
          <el-input v-model="form.name" placeholder="如：管理员 / 操作员" />
        </el-form-item>
        <el-form-item label="权限">
          <el-checkbox-group v-model="form.permList">
            <el-checkbox v-for="o in PERM_OPTIONS" :key="o.code" :value="o.code">{{ o.label }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="备注信息（选填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>
