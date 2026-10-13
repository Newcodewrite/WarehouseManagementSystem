<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { locationApi, warehouseApi, type Location, type Warehouse } from '@/api/modules'
import { useUserStore } from '@/stores/user'
import { formatTime } from '@/utils/format'

const userStore = useUserStore()
const canWrite = userStore.hasPerm('base:location')

const warehouses = ref<Warehouse[]>([])
const loading = ref(false)
const list = ref<Location[]>([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, warehouseId: '' as number | '', zone: '', keyword: '' })

const zoneOptions = [
  { value: 'RECEIVE', label: '收货区' },
  { value: 'STORE', label: '存储区' },
  { value: 'PICK', label: '拣选区' },
  { value: 'DEFECT', label: '残次区' },
  { value: 'SHIP', label: '发货区' }
]
function zoneLabel(z: string) {
  return zoneOptions.find((o) => o.value === z)?.label ?? z
}

async function loadData() {
  loading.value = true
  try {
    const params: Record<string, unknown> = { current: query.current, size: query.size }
    if (query.warehouseId !== '') params.warehouseId = query.warehouseId
    if (query.zone) params.zone = query.zone
    if (query.keyword) params.keyword = query.keyword
    const res = await locationApi.page(params)
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

/* ---------- 新增 / 编辑 ---------- */
const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<Partial<Location>>({
  id: undefined, warehouseId: undefined as number | undefined, code: '', zone: 'STORE', capacity: 1000, status: 1, remark: ''
})
const rules: FormRules = {
  warehouseId: [{ required: true, message: '请选择仓库', trigger: 'change' }],
  code: [{ required: true, message: '请输入库位编码', trigger: 'blur' }],
  zone: [{ required: true, message: '请选择库区', trigger: 'change' }]
}

function openEdit(row?: Location) {
  Object.assign(form, {
    id: row?.id,
    warehouseId: row?.warehouseId,
    code: row?.code ?? '',
    zone: row?.zone ?? 'STORE',
    capacity: row?.capacity ?? 1000,
    status: row?.status ?? 1,
    remark: row?.remark ?? ''
  })
  dialogVisible.value = true
}

async function handleSave() {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (form.id) {
      await locationApi.update(form as Location)
      ElMessage.success('修改成功')
    } else {
      await locationApi.add(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: Location) {
  try {
    await ElMessageBox.confirm(`确定删除库位「${row.code}」吗？`, '提示', {
      type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await locationApi.remove(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(async () => {
  warehouses.value = await warehouseApi.list()
  loadData()
})
</script>

<template>
  <div class="page-container">
    <div class="toolbar">
      <el-select v-model="query.warehouseId" placeholder="选择仓库" clearable style="width: 180px" @change="handleSearch">
        <el-option v-for="w in warehouses" :key="w.id" :label="w.name" :value="w.id" />
      </el-select>
      <el-select v-model="query.zone" placeholder="库区" clearable style="width: 140px" @change="handleSearch">
        <el-option v-for="z in zoneOptions" :key="z.value" :label="z.label" :value="z.value" />
      </el-select>
      <el-input v-model="query.keyword" placeholder="库位编码" clearable style="width: 180px" @keyup.enter="handleSearch" @clear="handleSearch">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
      <el-button v-if="canWrite" type="success" :icon="Plus" @click="openEdit()">新增库位</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="warehouseName" label="所属仓库" min-width="120" />
      <el-table-column prop="code" label="库位编码" min-width="130" />
      <el-table-column label="库区" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.zone === 'PICK' ? 'warning' : row.zone === 'DEFECT' ? 'danger' : 'success'">
            {{ zoneLabel(row.zone) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="capacity" label="容量" width="90" align="right" />
      <el-table-column prop="used" label="已占用" width="90" align="right" />
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
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
      v-model:current-page="query.current" v-model:page-size="query.size" class="pager" background
      layout="total, prev, pager, next, sizes" :total="total" :page-sizes="[10, 20, 50]"
      @current-change="loadData" @size-change="handleSearch"
    />

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑库位' : '新增库位'" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="所属仓库" prop="warehouseId">
          <el-select v-model="form.warehouseId" placeholder="请选择仓库" style="width: 100%" :disabled="!!form.id">
            <el-option v-for="w in warehouses" :key="w.id" :label="w.name" :value="w.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="库位编码" prop="code">
          <el-input v-model="form.code" placeholder="如 A-01-03 或 P-02-01" />
        </el-form-item>
        <el-form-item label="库区" prop="zone">
          <el-select v-model="form.zone" style="width: 100%">
            <el-option v-for="z in zoneOptions" :key="z.value" :label="z.label" :value="z.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="容量">
          <el-input-number v-model="form.capacity" :min="1" :precision="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
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
