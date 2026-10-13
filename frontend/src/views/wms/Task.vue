<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { taskApi, warehouseApi, type WmsTask, type Warehouse } from '@/api/modules'
import { useUserStore } from '@/stores/user'
import { formatTime } from '@/utils/format'

const userStore = useUserStore()
const canWrite = userStore.hasPerm('wms:task')

const warehouses = ref<Warehouse[]>([])
const assignees = ref<{ id: number; name: string }[]>([])
const loading = ref(false)
const list = ref<WmsTask[]>([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, type: '' as number | '', status: '' as number | '', warehouseId: '' as number | '' })

const typeOptions = [
  { value: 1, label: '上架' }, { value: 2, label: '拣选' }, { value: 3, label: '盘点' },
  { value: 4, label: '移库' }, { value: 5, label: '补货' }, { value: 6, label: '退货质检' }
]
const statusOptions = [
  { value: 0, label: '待分配' }, { value: 1, label: '待执行' }, { value: 2, label: '进行中' },
  { value: 3, label: '已完成' }, { value: 4, label: '已取消' }
]
function tagType(status: number) {
  return ['info', 'warning', 'primary', 'success', 'danger'][status] ?? 'info'
}

async function loadData() {
  loading.value = true
  try {
    const params: Record<string, unknown> = { current: query.current, size: query.size }
    if (query.type !== '') params.type = query.type
    if (query.status !== '') params.status = query.status
    if (query.warehouseId !== '') params.warehouseId = query.warehouseId
    const res = await taskApi.page(params)
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

/* ---------- 分配人员 ---------- */
const assignVisible = ref(false)
const assignTarget = ref<WmsTask | null>(null)
const assignUserId = ref<number>()
function openAssign(row: WmsTask) {
  assignTarget.value = row
  assignUserId.value = row.assigneeId
  assignVisible.value = true
}
async function confirmAssign() {
  if (!assignTarget.value || !assignUserId.value) {
    ElMessage.warning('请选择指派人员')
    return
  }
  await taskApi.assign(assignTarget.value.id, assignUserId.value)
  ElMessage.success('任务已分配')
  assignVisible.value = false
  loadData()
}

async function doAction(row: WmsTask, action: 'start' | 'complete' | 'cancel') {
  const map = { start: ['开始', '确定开始执行该任务吗？'], complete: ['完成', '确定该任务已完成吗？'], cancel: ['取消', '确定取消该任务吗？'] } as const
  try {
    await ElMessageBox.confirm(map[action][1], map[action][0], { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' })
  } catch {
    return
  }
  if (action === 'start') await taskApi.start(row.id)
  else if (action === 'complete') await taskApi.complete(row.id)
  else await taskApi.cancel(row.id)
  ElMessage.success('操作成功')
  loadData()
}

onMounted(async () => {
  warehouses.value = await warehouseApi.list()
  assignees.value = await taskApi.assignees()
  loadData()
})
</script>

<template>
  <div class="page-container">
    <div class="toolbar">
      <el-select v-model="query.warehouseId" placeholder="仓库" clearable style="width: 160px" @change="handleSearch">
        <el-option v-for="w in warehouses" :key="w.id" :label="w.name" :value="w.id" />
      </el-select>
      <el-select v-model="query.type" placeholder="任务类型" clearable style="width: 140px" @change="handleSearch">
        <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value" />
      </el-select>
      <el-select v-model="query.status" placeholder="任务状态" clearable style="width: 140px" @change="handleSearch">
        <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="taskNo" label="任务号" min-width="150" />
      <el-table-column label="类型" width="100" align="center">
        <template #default="{ row }"><el-tag>{{ row.typeName }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="warehouseName" label="仓库" min-width="110" />
      <el-table-column prop="content" label="任务内容" min-width="280" show-overflow-tooltip />
      <el-table-column label="指派人" width="100">
        <template #default="{ row }">{{ row.assigneeName || '未分配' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="tagType(row.status)">{{ row.statusName }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="完成时间" width="170">
        <template #default="{ row }">{{ formatTime(row.finishTime) }}</template>
      </el-table-column>
      <el-table-column v-if="canWrite" label="操作" width="250" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :disabled="row.status === 3 || row.status === 4" @click="openAssign(row)">分配</el-button>
          <el-button link type="primary" :disabled="row.status !== 1" @click="doAction(row, 'start')">开始</el-button>
          <el-button link type="success" :disabled="row.status === 0 || row.status === 3 || row.status === 4" @click="doAction(row, 'complete')">完成</el-button>
          <el-button link type="danger" :disabled="row.status === 3 || row.status === 4" @click="doAction(row, 'cancel')">取消</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="query.current" v-model:page-size="query.size" class="pager" background
      layout="total, prev, pager, next, sizes" :total="total" :page-sizes="[10, 20, 50]"
      @current-change="loadData" @size-change="handleSearch"
    />

    <el-dialog v-model="assignVisible" title="分配任务" width="420px" destroy-on-close>
      <div v-if="assignTarget" style="margin-bottom: 12px; color: #606266">
        {{ assignTarget.taskNo }} · {{ assignTarget.typeName }} · {{ assignTarget.content }}
      </div>
      <el-select v-model="assignUserId" placeholder="选择作业人员" filterable style="width: 100%">
        <el-option v-for="a in assignees" :key="a.id" :label="a.name" :value="a.id" />
      </el-select>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmAssign">确定分配</el-button>
      </template>
    </el-dialog>
  </div>
</template>
