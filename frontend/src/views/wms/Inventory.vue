<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import {
  inventoryApi,
  productApi,
  warehouseApi,
  type Inventory,
  type InventoryRecord,
  type Product,
  type Warehouse
} from '@/api/modules'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const canAdjust = userStore.hasPerm('wms:inventory')

const typeMap: Record<string, { label: string; type: 'success' | 'warning' | 'info' }> = {
  IN: { label: '入库', type: 'success' },
  OUT: { label: '出库', type: 'warning' },
  ADJUST: { label: '调整', type: 'info' }
}

/* ---------- 库存列表 ---------- */
const loading = ref(false)
const list = ref<Inventory[]>([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, warehouseId: '' as number | '', keyword: '' })
const warehouseOptions = ref<Warehouse[]>([])

async function loadData() {
  loading.value = true
  try {
    const params: Record<string, unknown> = { current: query.current, size: query.size }
    if (query.warehouseId !== '') params.warehouseId = query.warehouseId
    if (query.keyword) params.keyword = query.keyword
    const res = await inventoryApi.page(params)
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

/* ---------- 库存流水 ---------- */
const recordLoading = ref(false)
const records = ref<InventoryRecord[]>([])
const recordTotal = ref(0)
const recordQuery = reactive({ current: 1, size: 10, warehouseId: '' as number | '', type: '' })

async function loadRecords() {
  recordLoading.value = true
  try {
    const params: Record<string, unknown> = { current: recordQuery.current, size: recordQuery.size }
    if (recordQuery.warehouseId !== '') params.warehouseId = recordQuery.warehouseId
    if (recordQuery.type) params.type = recordQuery.type
    const res = await inventoryApi.recordPage(params)
    records.value = res.records || []
    recordTotal.value = res.total || 0
  } finally {
    recordLoading.value = false
  }
}

function handleRecordSearch() {
  recordQuery.current = 1
  loadRecords()
}

/* ---------- 库存调整 ---------- */
const adjustVisible = ref(false)
const adjustSaving = ref(false)
const adjustFormRef = ref<FormInstance>()
const productOptions = ref<Product[]>([])
const adjustForm = reactive({
  warehouseId: undefined as number | undefined,
  productId: undefined as number | undefined,
  quantity: 0,
  remark: ''
})

const adjustRules: FormRules = {
  warehouseId: [{ required: true, message: '请选择仓库', trigger: 'change' }],
  productId: [{ required: true, message: '请选择商品', trigger: 'change' }]
}

async function openAdjust(row: Inventory) {
  adjustForm.warehouseId = row.warehouseId
  adjustForm.productId = row.productId
  adjustForm.quantity = 0
  adjustForm.remark = ''
  if (!warehouseOptions.value.length || !productOptions.value.length) {
    const [w, p] = await Promise.all([warehouseApi.list(), productApi.list()])
    warehouseOptions.value = w || []
    productOptions.value = p || []
  }
  adjustVisible.value = true
}

async function handleAdjust() {
  await adjustFormRef.value?.validate()
  if (!adjustForm.quantity) {
    ElMessage.warning('调整数量不能为 0（正数增加，负数减少）')
    return
  }
  adjustSaving.value = true
  try {
    await inventoryApi.adjust({
      warehouseId: adjustForm.warehouseId!,
      productId: adjustForm.productId!,
      quantity: adjustForm.quantity,
      remark: adjustForm.remark
    })
    ElMessage.success('库存调整成功')
    adjustVisible.value = false
    loadData()
    loadRecords()
  } finally {
    adjustSaving.value = false
  }
}

onMounted(async () => {
  loadData()
  loadRecords()
  warehouseApi
    .list()
    .then((w) => (warehouseOptions.value = w || []))
    .catch(() => {})
})
</script>

<template>
  <div class="page-container">
    <!-- 库存列表 -->
    <div class="toolbar">
      <el-select
        v-model="query.warehouseId"
        placeholder="全部仓库"
        clearable
        filterable
        style="width: 200px"
        @change="handleSearch"
      >
        <el-option v-for="w in warehouseOptions" :key="w.id" :label="w.name" :value="w.id" />
      </el-select>
      <el-input
        v-model="query.keyword"
        placeholder="商品编码 / 名称关键字"
        clearable
        style="width: 220px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="warehouseName" label="仓库" min-width="130" />
      <el-table-column prop="productCode" label="商品编码" min-width="120" />
      <el-table-column prop="productName" label="商品名称" min-width="150" />
      <el-table-column prop="category" label="分类" width="100" />
      <el-table-column prop="unit" label="单位" width="70" align="center" />
      <el-table-column prop="quantity" label="库存数量" width="100" align="right" />
      <el-table-column prop="warnStock" label="预警库存" width="100" align="right" />
      <el-table-column label="库存状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.alert" type="danger">预警</el-tag>
          <el-tag v-else type="success">正常</el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="canAdjust" label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openAdjust(row)">库存调整</el-button>
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

    <!-- 库存流水 -->
    <div class="section-title">库存流水</div>
    <div class="toolbar">
      <el-select
        v-model="recordQuery.warehouseId"
        placeholder="全部仓库"
        clearable
        filterable
        style="width: 200px"
        @change="handleRecordSearch"
      >
        <el-option v-for="w in warehouseOptions" :key="w.id" :label="w.name" :value="w.id" />
      </el-select>
      <el-select v-model="recordQuery.type" placeholder="全部类型" clearable style="width: 140px" @change="handleRecordSearch">
        <el-option label="入库" value="IN" />
        <el-option label="出库" value="OUT" />
        <el-option label="调整" value="ADJUST" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="handleRecordSearch">查询</el-button>
    </div>

    <el-table v-loading="recordLoading" :data="records" border stripe>
      <el-table-column prop="warehouseName" label="仓库" min-width="130" />
      <el-table-column prop="productName" label="商品名称" min-width="150" />
      <el-table-column label="类型" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="typeMap[row.type]?.type || 'info'">{{ typeMap[row.type]?.label || row.type }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="quantity" label="变动数量" width="100" align="right" />
      <el-table-column prop="afterQuantity" label="变动后库存" width="110" align="right" />
      <el-table-column prop="orderId" label="关联单据ID" width="110" align="center">
        <template #default="{ row }">{{ row.orderId ?? '-' }}</template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
      <el-table-column prop="createBy" label="操作人" width="100" />
      <el-table-column prop="createTime" label="操作时间" width="170">
        <template #default="{ row }">{{ (row.createTime || '').replace('T', ' ').slice(0, 19) }}</template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="recordQuery.current"
      v-model:page-size="recordQuery.size"
      class="pager"
      background
      layout="total, prev, pager, next, sizes"
      :total="recordTotal"
      :page-sizes="[10, 20, 50]"
      @current-change="loadRecords"
      @size-change="handleRecordSearch"
    />

    <!-- 库存调整弹窗 -->
    <el-dialog v-model="adjustVisible" title="库存调整" width="480px" destroy-on-close>
      <el-form ref="adjustFormRef" :model="adjustForm" :rules="adjustRules" label-width="90px">
        <el-form-item label="仓库" prop="warehouseId">
          <el-select v-model="adjustForm.warehouseId" filterable disabled style="width: 100%">
            <el-option v-for="w in warehouseOptions" :key="w.id" :label="w.name" :value="w.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="商品" prop="productId">
          <el-select v-model="adjustForm.productId" filterable disabled style="width: 100%">
            <el-option
              v-for="p in productOptions"
              :key="p.id"
              :label="`${p.code} - ${p.name}`"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="调整数量" required>
          <el-input-number v-model="adjustForm.quantity" :precision="0" style="width: 100%" />
          <div class="adjust-tip">正数增加库存，负数减少库存</div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="adjustForm.remark" type="textarea" :rows="2" placeholder="调整原因（选填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustVisible = false">取消</el-button>
        <el-button type="primary" :loading="adjustSaving" @click="handleAdjust">确定调整</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.adjust-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}
</style>
