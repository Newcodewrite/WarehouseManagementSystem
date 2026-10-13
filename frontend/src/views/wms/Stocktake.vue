<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { stocktakeApi, warehouseApi, type StocktakeOrder, type StocktakeItem, type Warehouse } from '@/api/modules'
import { useUserStore } from '@/stores/user'
import { formatTime } from '@/utils/format'

const userStore = useUserStore()
const canWrite = userStore.hasPerm('wms:stocktake')

const warehouses = ref<Warehouse[]>([])
const loading = ref(false)
const list = ref<StocktakeOrder[]>([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, orderNo: '', status: '' as number | '' })

const statusTag = ['info', 'warning', 'primary', 'success', 'danger']
function tagType(s: number) {
  return statusTag[s] ?? 'info'
}

async function loadData() {
  loading.value = true
  try {
    const params: Record<string, unknown> = { current: query.current, size: query.size }
    if (query.orderNo) params.orderNo = query.orderNo
    if (query.status !== '') params.status = query.status
    const res = await stocktakeApi.page(params)
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

/* ---------- 新建盘点单 ---------- */
const createVisible = ref(false)
const createForm = reactive({ warehouseId: undefined as number | undefined, type: 1, remark: '' })
function openCreate() {
  createForm.warehouseId = undefined
  createForm.type = 1
  createForm.remark = ''
  createVisible.value = true
}
async function confirmCreate() {
  if (!createForm.warehouseId) {
    ElMessage.warning('请选择盘点仓库')
    return
  }
  await stocktakeApi.create({ warehouseId: createForm.warehouseId, type: createForm.type, remark: createForm.remark })
  ElMessage.success('盘点单已创建，并生成盘点任务')
  createVisible.value = false
  loadData()
}

/* ---------- 盘点明细（录实盘） ---------- */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<StocktakeOrder | null>(null)
const counts = reactive<Record<number, number | null>>({})

async function openDetail(row: StocktakeOrder) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    const d = await stocktakeApi.detail(row.id)
    detail.value = d
    Object.keys(counts).forEach((k) => delete counts[Number(k)])
    ;(d.items || []).forEach((it) => {
      counts[it.id] = it.actualQty ?? null
    })
  } finally {
    detailLoading.value = false
  }
}

async function submitCounts() {
  if (!detail.value) return
  const countsArr = (detail.value.items || []).map((it) => ({ itemId: it.id, actualQty: counts[it.id] ?? null }))
  const missing = countsArr.filter((c) => c.actualQty === null || c.actualQty === undefined)
  if (missing.length) {
    ElMessage.warning(`还有 ${missing.length} 项未录入实盘数量`)
    return
  }
  await stocktakeApi.submit(detail.value.id, countsArr)
  ElMessage.success('实盘已提交，等待审核')
  detailVisible.value = false
  loadData()
}

async function audit(row: StocktakeOrder, approved: boolean) {
  try {
    await ElMessageBox.confirm(
      approved ? '审核通过后，盘盈盘亏将自动调整库存，确定吗？' : '确定驳回该盘点单吗？',
      '盘点审核', { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  await stocktakeApi.audit(row.id, approved)
  ElMessage.success(approved ? '已审核，库存已调整' : '已驳回')
  loadData()
}

async function cancelOrder(row: StocktakeOrder) {
  try {
    await ElMessageBox.confirm('确定取消该盘点单吗？', '提示', { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' })
  } catch {
    return
  }
  await stocktakeApi.cancel(row.id)
  ElMessage.success('已取消')
  loadData()
}

function diffClass(it: StocktakeItem) {
  const a = counts[it.id]
  if (a === null || a === undefined) return ''
  return a - it.bookQty > 0 ? 'gain' : a - it.bookQty < 0 ? 'loss' : ''
}

onMounted(async () => {
  warehouses.value = await warehouseApi.list()
  loadData()
})
</script>

<template>
  <div class="page-container">
    <div class="toolbar">
      <el-input v-model="query.orderNo" placeholder="盘点单号" clearable style="width: 200px" @keyup.enter="handleSearch" @clear="handleSearch">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px" @change="handleSearch">
        <el-option label="待盘点" :value="0" />
        <el-option label="盘点中" :value="1" />
        <el-option label="待审核" :value="2" />
        <el-option label="已完成" :value="3" />
        <el-option label="已取消" :value="4" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
      <el-button v-if="canWrite" type="success" :icon="Plus" @click="openCreate">新建盘点</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="orderNo" label="盘点单号" min-width="150" />
      <el-table-column prop="warehouseName" label="仓库" min-width="110" />
      <el-table-column label="类型" width="90" align="center">
        <template #default="{ row }">{{ row.typeName }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }"><el-tag :type="tagType(row.status)">{{ row.statusName }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="totalCount" label="盘点项数" width="90" align="right" />
      <el-table-column label="盘盈 / 盘亏" width="120" align="center">
        <template #default="{ row }">
          <span style="color: #67c23a">+{{ row.gainQty }}</span>
          /
          <span style="color: #f56c6c">-{{ row.lossQty }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="createBy" label="创建人" width="100" />
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column v-if="canWrite" label="操作" width="250" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">明细/录盘</el-button>
          <el-button v-if="row.status === 2" link type="success" @click="audit(row, true)">审核通过</el-button>
          <el-button v-if="row.status === 2" link type="warning" @click="audit(row, false)">驳回</el-button>
          <el-button v-if="row.status === 0 || row.status === 1" link type="danger" @click="cancelOrder(row)">取消</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="query.current" v-model:page-size="query.size" class="pager" background
      layout="total, prev, pager, next, sizes" :total="total" :page-sizes="[10, 20, 50]"
      @current-change="loadData" @size-change="handleSearch"
    />

    <!-- 新建盘点单 -->
    <el-dialog v-model="createVisible" title="新建盘点单" width="460px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="盘点仓库" required>
          <el-select v-model="createForm.warehouseId" placeholder="请选择仓库" style="width: 100%">
            <el-option v-for="w in warehouses" :key="w.id" :label="w.name" :value="w.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="盘点类型">
          <el-radio-group v-model="createForm.type">
            <el-radio :value="1">全面盘点</el-radio>
            <el-radio :value="2">循环盘点</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" placeholder="备注（选填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 盘点明细 -->
    <el-dialog v-model="detailVisible" :title="`盘点明细 ${detail ? '· ' + detail.orderNo : ''}`" width="820px" destroy-on-close>
      <div v-loading="detailLoading">
        <el-alert
          v-if="detail"
          :title="`仓库：${detail.warehouseName} ｜ 状态：${detail.statusName} ｜ 盘盈 +${detail.gainQty} / 盘亏 -${detail.lossQty}`"
          type="info" :closable="false" style="margin-bottom: 12px"
        />
        <el-table :data="detail?.items || []" border stripe max-height="420">
          <el-table-column prop="productCode" label="编码" width="90" />
          <el-table-column prop="productName" label="商品" min-width="150" />
          <el-table-column prop="unit" label="单位" width="70" align="center" />
          <el-table-column prop="bookQty" label="账面数量" width="100" align="right" />
          <el-table-column label="实盘数量" width="140" align="center">
            <template #default="{ row }">
              <el-input-number
                v-model="counts[row.id]" :min="0" :precision="0" size="small"
                :disabled="!canWrite || (detail?.status ?? 0) >= 2" controls-position="right" style="width: 120px"
              />
            </template>
          </el-table-column>
          <el-table-column label="差异" width="90" align="center">
            <template #default="{ row }">
              <span :class="diffClass(row)">
                {{ counts[row.id] === null || counts[row.id] === undefined ? '-' : (counts[row.id]! - row.bookQty) }}
              </span>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button
          v-if="canWrite && detail && (detail.status === 0 || detail.status === 1)"
          type="primary" @click="submitCounts"
        >提交实盘</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.gain { color: #67c23a; font-weight: 600; }
.loss { color: #f56c6c; font-weight: 600; }
</style>
