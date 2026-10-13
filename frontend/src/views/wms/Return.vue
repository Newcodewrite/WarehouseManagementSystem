<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Delete } from '@element-plus/icons-vue'
import {
  returnApi, warehouseApi, customerApi, productApi,
  type ReturnOrder, type Warehouse, type Partner, type Product
} from '@/api/modules'
import { useUserStore } from '@/stores/user'
import { formatTime } from '@/utils/format'

const userStore = useUserStore()
const canWrite = userStore.hasPerm('wms:return')

const warehouses = ref<Warehouse[]>([])
const customers = ref<Partner[]>([])
const products = ref<Product[]>([])
const loading = ref(false)
const list = ref<ReturnOrder[]>([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, orderNo: '', status: '' as number | '' })

const statusTag = ['info', 'warning', 'success', 'danger']
function tagType(s: number) {
  return statusTag[s] ?? 'info'
}
const gradeOptions = [
  { value: 1, label: '可二次销售' },
  { value: 2, label: '需翻新' },
  { value: 3, label: '报废' }
]
function gradeTag(g?: number | null) {
  return gradeOptions.find((o) => o.value === g)?.label ?? '未质检'
}

async function loadData() {
  loading.value = true
  try {
    const params: Record<string, unknown> = { current: query.current, size: query.size }
    if (query.orderNo) params.orderNo = query.orderNo
    if (query.status !== '') params.status = query.status
    const res = await returnApi.page(params)
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

/* ---------- 新建退货单 ---------- */
const createVisible = ref(false)
const createForm = reactive<{ warehouseId: number | undefined; customerId: number | undefined; remark: string; items: { productId: number | undefined; quantity: number }[] }>({
  warehouseId: undefined, customerId: undefined, remark: '', items: [{ productId: undefined, quantity: 1 }]
})
function openCreate() {
  createForm.warehouseId = undefined
  createForm.customerId = undefined
  createForm.remark = ''
  createForm.items = [{ productId: undefined, quantity: 1 }]
  createVisible.value = true
}
function addLine() {
  createForm.items.push({ productId: undefined, quantity: 1 })
}
function removeLine(idx: number) {
  createForm.items.splice(idx, 1)
}
async function confirmCreate() {
  if (!createForm.warehouseId) return ElMessage.warning('请选择收货仓库')
  const items = createForm.items
    .filter((i): i is { productId: number; quantity: number } => !!i.productId && i.quantity > 0)
  if (!items.length) return ElMessage.warning('请至少添加一条退货明细')
  await returnApi.create({ warehouseId: createForm.warehouseId, customerId: createForm.customerId, remark: createForm.remark, items })
  ElMessage.success('退货单已创建')
  createVisible.value = false
  loadData()
}

/* ---------- 详情 / 收货 / 质检 ---------- */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<ReturnOrder | null>(null)
const grades = reactive<Record<number, { grade: number; remark: string }>>({})

async function openDetail(row: ReturnOrder) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    const d = await returnApi.detail(row.id)
    detail.value = d
    Object.keys(grades).forEach((k) => delete grades[Number(k)])
    ;(d.items || []).forEach((it) => {
      grades[it.id] = { grade: it.qcGrade ?? 1, remark: it.qcRemark ?? '' }
    })
  } finally {
    detailLoading.value = false
  }
}

async function doReceive(row: ReturnOrder) {
  try {
    await ElMessageBox.confirm('确认收到退货包裹？确认后进入待质检状态。', '退货收货', { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' })
  } catch {
    return
  }
  await returnApi.receive(row.id)
  ElMessage.success('已收货，生成退货质检任务')
  loadData()
}

async function submitQc() {
  if (!detail.value) return
  const arr = (detail.value.items || []).map((it) => ({
    itemId: it.id,
    qcGrade: grades[it.id]?.grade ?? 1,
    qcRemark: grades[it.id]?.remark
  }))
  await returnApi.qc(detail.value.id, arr)
  ElMessage.success('质检完成，可售品已回库、报废品已生成移库任务')
  detailVisible.value = false
  loadData()
}

async function cancelOrder(row: ReturnOrder) {
  try {
    await ElMessageBox.confirm('确定取消该退货单吗？', '提示', { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' })
  } catch {
    return
  }
  await returnApi.cancel(row.id)
  ElMessage.success('已取消')
  loadData()
}

onMounted(async () => {
  warehouses.value = await warehouseApi.list()
  customers.value = (await customerApi.page({ current: 1, size: 200 })).records || []
  products.value = await productApi.list()
  loadData()
})
</script>

<template>
  <div class="page-container">
    <div class="toolbar">
      <el-input v-model="query.orderNo" placeholder="退货单号" clearable style="width: 200px" @keyup.enter="handleSearch" @clear="handleSearch">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px" @change="handleSearch">
        <el-option label="待收货" :value="0" />
        <el-option label="待质检" :value="1" />
        <el-option label="已完成" :value="2" />
        <el-option label="已取消" :value="3" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
      <el-button v-if="canWrite" type="success" :icon="Plus" @click="openCreate">新建退货</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="orderNo" label="退货单号" min-width="150" />
      <el-table-column prop="warehouseName" label="收货仓库" min-width="110" />
      <el-table-column prop="customerName" label="客户" min-width="120" />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }"><el-tag :type="tagType(row.status)">{{ row.statusName }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="totalQty" label="退货数" width="80" align="right" />
      <el-table-column label="可售 / 报废" width="110" align="center">
        <template #default="{ row }">
          <span style="color: #67c23a">{{ row.resaleQty }}</span> /
          <span style="color: #f56c6c">{{ row.scrapQty }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="createBy" label="创建人" width="90" />
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column v-if="canWrite" label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">明细/质检</el-button>
          <el-button v-if="row.status === 0" link type="success" @click="doReceive(row)">收货</el-button>
          <el-button v-if="row.status === 0 || row.status === 1" link type="danger" @click="cancelOrder(row)">取消</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="query.current" v-model:page-size="query.size" class="pager" background
      layout="total, prev, pager, next, sizes" :total="total" :page-sizes="[10, 20, 50]"
      @current-change="loadData" @size-change="handleSearch"
    />

    <!-- 新建退货单 -->
    <el-dialog v-model="createVisible" title="新建退货单" width="640px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="收货仓库" required>
          <el-select v-model="createForm.warehouseId" placeholder="请选择仓库" style="width: 100%">
            <el-option v-for="w in warehouses" :key="w.id" :label="w.name" :value="w.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="客户">
          <el-select v-model="createForm.customerId" placeholder="选择客户（选填）" clearable filterable style="width: 100%">
            <el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="退货明细">
          <div style="width: 100%">
            <div v-for="(line, idx) in createForm.items" :key="idx" style="display: flex; gap: 8px; margin-bottom: 8px">
              <el-select v-model="line.productId" placeholder="选择商品" filterable style="flex: 1">
                <el-option v-for="p in products" :key="p.id" :label="`${p.code} ${p.name}`" :value="p.id" />
              </el-select>
              <el-input-number v-model="line.quantity" :min="1" :precision="0" style="width: 130px" />
              <el-button link type="danger" :icon="Delete" @click="removeLine(idx)" />
            </div>
            <el-button link type="primary" :icon="Plus" @click="addLine">添加一行</el-button>
          </div>
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

    <!-- 明细 / 质检 -->
    <el-dialog v-model="detailVisible" :title="`退货明细 ${detail ? '· ' + detail.orderNo : ''}`" width="780px" destroy-on-close>
      <div v-loading="detailLoading">
        <el-alert
          v-if="detail"
          :title="`仓库：${detail.warehouseName} ｜ 客户：${detail.customerName} ｜ 状态：${detail.statusName}`"
          type="info" :closable="false" style="margin-bottom: 12px"
        />
        <el-table :data="detail?.items || []" border stripe max-height="400">
          <el-table-column prop="productCode" label="编码" width="90" />
          <el-table-column prop="productName" label="商品" min-width="150" />
          <el-table-column prop="quantity" label="退货数" width="80" align="right" />
          <el-table-column label="质检分级" width="150">
            <template #default="{ row }">
              <el-select
                v-if="canWrite && detail?.status === 1"
                v-model="grades[row.id].grade" size="small"
              >
                <el-option v-for="g in gradeOptions" :key="g.value" :label="g.label" :value="g.value" />
              </el-select>
              <span v-else>{{ gradeTag(row.qcGrade) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="质检备注" min-width="140">
            <template #default="{ row }">
              <el-input
                v-if="canWrite && detail?.status === 1" v-model="grades[row.id].remark" size="small" placeholder="质检说明"
              />
              <span v-else>{{ row.qcRemark || '—' }}</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button v-if="canWrite && detail?.status === 1" type="primary" @click="submitQc">完成质检并处理</el-button>
      </template>
    </el-dialog>
  </div>
</template>
