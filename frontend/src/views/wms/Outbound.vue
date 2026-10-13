<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { customerApi, outboundApi, productApi, warehouseApi, type Partner, type Product, type Warehouse, type WmsOrder } from '@/api/modules'
import { useUserStore } from '@/stores/user'
import { formatMoney, formatTime } from '@/utils/format'

const userStore = useUserStore()
const canWrite = userStore.hasPerm('wms:outbound')

const statusMap: Record<number, { label: string; type: 'warning' | 'success' | 'danger' }> = {
  0: { label: '待审核', type: 'warning' },
  1: { label: '已出库', type: 'success' },
  2: { label: '已驳回', type: 'danger' }
}
const statusOptions = [
  { value: 0, label: '待审核' },
  { value: 1, label: '已出库' },
  { value: 2, label: '已驳回' }
]

/* ---------- 列表 ---------- */
const loading = ref(false)
const list = ref<WmsOrder[]>([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, orderNo: '', status: '' as number | '' })

async function loadData() {
  loading.value = true
  try {
    const params: Record<string, unknown> = { current: query.current, size: query.size }
    if (query.orderNo) params.orderNo = query.orderNo
    if (query.status !== '') params.status = query.status
    const res = await outboundApi.page(params)
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

/* ---------- 新建出库单 ---------- */
interface CreateItem {
  productId: number | undefined
  quantity: number
  price: number
}

const createVisible = ref(false)
const creating = ref(false)
const formRef = ref<FormInstance>()
const warehouseOptions = ref<Warehouse[]>([])
const customerOptions = ref<Partner[]>([])
const productOptions = ref<Product[]>([])
const createForm = reactive({
  warehouseId: undefined as number | undefined,
  customerId: undefined as number | undefined,
  remark: '',
  items: [] as CreateItem[]
})

const createRules: FormRules = {
  warehouseId: [{ required: true, message: '请选择出库仓库', trigger: 'change' }],
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }]
}

const totalAmount = computed(() =>
  createForm.items.reduce((sum, it) => sum + (it.quantity || 0) * (it.price || 0), 0)
)

async function openCreate() {
  createForm.warehouseId = undefined
  createForm.customerId = undefined
  createForm.remark = ''
  createForm.items = [{ productId: undefined, quantity: 1, price: 0 }]
  if (!warehouseOptions.value.length || !customerOptions.value.length || !productOptions.value.length) {
    const [w, c, p] = await Promise.all([
      warehouseApi.list(),
      customerApi.page({ current: 1, size: 100 }),
      productApi.list()
    ])
    warehouseOptions.value = w || []
    customerOptions.value = c?.records || []
    productOptions.value = p || []
  }
  createVisible.value = true
}

function addItem() {
  createForm.items.push({ productId: undefined, quantity: 1, price: 0 })
}

async function handleCreate() {
  await formRef.value?.validate()
  if (!createForm.items.length) {
    ElMessage.warning('请至少添加一条出库明细')
    return
  }
  for (let i = 0; i < createForm.items.length; i++) {
    const it = createForm.items[i]
    if (!it.productId) {
      ElMessage.warning(`第 ${i + 1} 行明细请选择商品`)
      return
    }
    if (!it.quantity || it.quantity <= 0) {
      ElMessage.warning(`第 ${i + 1} 行明细数量必须大于 0`)
      return
    }
  }
  creating.value = true
  try {
    await outboundApi.add({
      warehouseId: createForm.warehouseId!,
      customerId: createForm.customerId!,
      remark: createForm.remark,
      items: createForm.items.map((it) => ({ productId: it.productId!, quantity: it.quantity, price: it.price }))
    })
    ElMessage.success('出库单创建成功')
    createVisible.value = false
    handleSearch()
  } finally {
    creating.value = false
  }
}

/* ---------- 详情 ---------- */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<WmsOrder | null>(null)

async function openDetail(row: WmsOrder) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await outboundApi.detail(row.id)
  } finally {
    detailLoading.value = false
  }
}

/* ---------- 审核 / 删除 ---------- */
async function handleAudit(row: WmsOrder, approved: boolean) {
  try {
    await ElMessageBox.confirm(`确定${approved ? '通过' : '驳回'}出库单「${row.orderNo}」吗？`, '审核确认', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await outboundApi.audit(row.id, approved)
  ElMessage.success(approved ? '审核通过，商品已出库' : '已驳回该出库单')
  loadData()
}

async function handleDelete(row: WmsOrder) {
  try {
    await ElMessageBox.confirm(`确定删除出库单「${row.orderNo}」吗？`, '提示', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await outboundApi.remove(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <div class="toolbar">
      <el-input
        v-model="query.orderNo"
        placeholder="出库单号"
        clearable
        style="width: 200px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="handleSearch">
        <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
      <el-button v-if="canWrite" type="success" :icon="Plus" @click="openCreate">新建出库单</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="orderNo" label="出库单号" min-width="160" />
      <el-table-column prop="warehouseName" label="仓库" min-width="120" />
      <el-table-column prop="customerName" label="客户" min-width="130" />
      <el-table-column prop="itemCount" label="商品种数" width="90" align="center" />
      <el-table-column label="合计金额" width="120" align="right">
        <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="statusMap[row.status]?.type || 'info'">{{ statusMap[row.status]?.label || '未知' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createBy" label="创建人" width="90" />
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column prop="auditBy" label="审核人" width="90" />
      <el-table-column label="审核时间" width="170">
        <template #default="{ row }">{{ formatTime(row.auditTime) }}</template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="110" show-overflow-tooltip />
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <template v-if="canWrite && row.status === 0">
            <el-button link type="success" @click="handleAudit(row, true)">通过</el-button>
            <el-button link type="warning" @click="handleAudit(row, false)">驳回</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
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

    <!-- 新建出库单 -->
    <el-dialog v-model="createVisible" title="新建出库单" width="780px" destroy-on-close>
      <el-form ref="formRef" :model="createForm" :rules="createRules" label-width="90px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="出库仓库" prop="warehouseId">
              <el-select v-model="createForm.warehouseId" filterable placeholder="请选择仓库" style="width: 100%">
                <el-option v-for="w in warehouseOptions" :key="w.id" :label="`${w.code} - ${w.name}`" :value="w.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="客户" prop="customerId">
              <el-select v-model="createForm.customerId" filterable placeholder="请选择客户" style="width: 100%">
                <el-option v-for="c in customerOptions" :key="c.id" :label="`${c.code} - ${c.name}`" :value="c.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" placeholder="备注信息（选填）" />
        </el-form-item>
      </el-form>

      <div class="items-title">出库明细</div>
      <el-table :data="createForm.items" border size="small">
        <el-table-column label="商品" min-width="220">
          <template #default="{ row }">
            <el-select v-model="row.productId" filterable placeholder="请选择商品" style="width: 100%">
              <el-option
                v-for="p in productOptions"
                :key="p.id"
                :label="`${p.code} - ${p.name}`"
                :value="p.id"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="150">
          <template #default="{ row }">
            <el-input-number v-model="row.quantity" :min="1" :precision="0" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="单价" width="160">
          <template #default="{ row }">
            <el-input-number v-model="row.price" :min="0" :precision="2" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="小计" width="110" align="right">
          <template #default="{ row }">{{ formatMoney((row.quantity || 0) * (row.price || 0)) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ $index }">
            <el-button
              link
              type="danger"
              :disabled="createForm.items.length === 1"
              @click="createForm.items.splice($index, 1)"
            >删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="items-footer">
        <el-button type="primary" plain :icon="Plus" size="small" @click="addItem">添加明细</el-button>
        <span class="total">合计金额：<b>{{ formatMoney(totalAmount) }}</b></span>
      </div>

      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">提交出库单</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="出库单详情" size="640px">
      <div v-loading="detailLoading">
        <el-descriptions v-if="detail" :column="2" border>
          <el-descriptions-item label="出库单号" :span="2">{{ detail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="仓库">{{ detail.warehouseName }}</el-descriptions-item>
          <el-descriptions-item label="客户">{{ detail.customerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusMap[detail.status]?.type || 'info'">{{ statusMap[detail.status]?.label || '未知' }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="合计金额">{{ formatMoney(detail.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="创建人">{{ detail.createBy || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatTime(detail.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="审核人">{{ detail.auditBy || '-' }}</el-descriptions-item>
          <el-descriptions-item label="审核时间">{{ formatTime(detail.auditTime) }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="items-title">商品明细</div>
        <el-table :data="detail?.items || []" border size="small">
          <el-table-column prop="productCode" label="商品编码" min-width="110" />
          <el-table-column prop="productName" label="商品名称" min-width="130" />
          <el-table-column prop="unit" label="单位" width="70" align="center" />
          <el-table-column prop="quantity" label="数量" width="80" align="right" />
          <el-table-column label="单价" width="110" align="right">
            <template #default="{ row }">{{ formatMoney(row.price) }}</template>
          </el-table-column>
          <el-table-column label="小计" width="120" align="right">
            <template #default="{ row }">{{ formatMoney((row.quantity || 0) * (row.price || 0)) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.items-title {
  font-weight: 600;
  margin: 6px 0 10px;
}

.items-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
}

.total {
  font-size: 14px;
  color: #606266;
}

.total b {
  color: #f56c6c;
  font-size: 16px;
}
</style>
