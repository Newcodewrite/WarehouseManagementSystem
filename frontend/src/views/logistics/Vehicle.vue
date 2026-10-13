<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { vehicleApi, type Vehicle } from '@/api/modules'
import { useUserStore } from '@/stores/user'
import { formatTime } from '@/utils/format'

const userStore = useUserStore()
const canWrite = userStore.hasPerm('logistics:vehicle')

const statusMap: Record<number, { label: string; type: 'success' | 'primary' | 'danger' }> = {
  0: { label: '空闲', type: 'success' },
  1: { label: '运输中', type: 'primary' },
  2: { label: '维修', type: 'danger' }
}
const statusOptions = [
  { value: 0, label: '空闲' },
  { value: 1, label: '运输中' },
  { value: 2, label: '维修' }
]

/* ---------- 列表 ---------- */
const loading = ref(false)
const list = ref<Vehicle[]>([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, keyword: '', status: '' as number | '' })

async function loadData() {
  loading.value = true
  try {
    const params: Record<string, unknown> = { current: query.current, size: query.size }
    if (query.keyword) params.keyword = query.keyword
    if (query.status !== '') params.status = query.status
    const res = await vehicleApi.page(params)
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
const form = reactive<Partial<Vehicle>>({
  id: undefined, code: '', vehicleType: '', driverName: '', driverPhone: '', capacityKg: 0, status: 0, remark: ''
})

const rules: FormRules = {
  code: [{ required: true, message: '请输入车牌号', trigger: 'blur' }]
}

function openEdit(row?: Vehicle) {
  Object.assign(form, {
    id: row?.id,
    code: row?.code ?? '',
    vehicleType: row?.vehicleType ?? '',
    driverName: row?.driverName ?? '',
    driverPhone: row?.driverPhone ?? '',
    capacityKg: row?.capacityKg ?? 0,
    status: row?.status ?? 0,
    remark: row?.remark ?? ''
  })
  dialogVisible.value = true
}

async function handleSave() {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (form.id) {
      await vehicleApi.update(form as Vehicle)
      ElMessage.success('修改成功')
    } else {
      await vehicleApi.add(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: Vehicle) {
  try {
    await ElMessageBox.confirm(`确定删除车辆「${row.code}」吗？`, '提示', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await vehicleApi.remove(row.id)
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
        placeholder="车牌号 / 司机关键字"
        clearable
        style="width: 220px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="handleSearch">
        <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
      <el-button v-if="canWrite" type="success" :icon="Plus" @click="openEdit()">新增车辆</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="code" label="车牌号" min-width="120" />
      <el-table-column prop="vehicleType" label="车型" width="110" />
      <el-table-column prop="driverName" label="司机" width="100" />
      <el-table-column prop="driverPhone" label="司机电话" width="140" />
      <el-table-column prop="capacityKg" label="载重(kg)" width="100" align="right" />
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="statusMap[row.status]?.type || 'info'">{{ statusMap[row.status]?.label || '未知' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
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

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑车辆' : '新增车辆'" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="车牌号" prop="code">
          <el-input v-model="form.code" placeholder="如：鄂A12345" />
        </el-form-item>
        <el-form-item label="车型">
          <el-input v-model="form.vehicleType" placeholder="如：厢式货车 / 冷藏车" />
        </el-form-item>
        <el-form-item label="司机">
          <el-input v-model="form.driverName" placeholder="请输入司机姓名" />
        </el-form-item>
        <el-form-item label="司机电话">
          <el-input v-model="form.driverPhone" placeholder="请输入司机电话" />
        </el-form-item>
        <el-form-item label="载重(kg)">
          <el-input-number v-model="form.capacityKg" :min="0" :precision="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
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
