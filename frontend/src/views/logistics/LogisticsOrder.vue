<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { aiApi, logisticsApi, vehicleApi, type LogisticsOrder, type LogTrack, type Vehicle } from '@/api/modules'
import { useUserStore } from '@/stores/user'
import { formatTime } from '@/utils/format'

const userStore = useUserStore()
const canWrite = userStore.hasPerm('logistics:order')

const statusMap: Record<number, { label: string; type: 'warning' | 'primary' | 'success' | 'info' }> = {
  0: { label: '待调度', type: 'warning' },
  1: { label: '运输中', type: 'primary' },
  2: { label: '已签收', type: 'success' },
  3: { label: '已取消', type: 'info' }
}
const statusOptions = [
  { value: 0, label: '待调度' },
  { value: 1, label: '运输中' },
  { value: 2, label: '已签收' },
  { value: 3, label: '已取消' }
]

/* ---------- 列表 ---------- */
const loading = ref(false)
const list = ref<LogisticsOrder[]>([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, orderNo: '', status: '' as number | '' })

async function loadData() {
  loading.value = true
  try {
    const params: Record<string, unknown> = { current: query.current, size: query.size }
    if (query.orderNo) params.orderNo = query.orderNo
    if (query.status !== '') params.status = query.status
    const res = await logisticsApi.orderPage(params)
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

/* ---------- 新建物流订单 ---------- */
const createVisible = ref(false)
const creating = ref(false)
const formRef = ref<FormInstance>()
const createForm = reactive({ receiverName: '', receiverPhone: '', receiverAddress: '', remark: '' })

const createRules: FormRules = {
  receiverName: [{ required: true, message: '请输入收货人姓名', trigger: 'blur' }],
  receiverPhone: [{ required: true, message: '请输入收货人电话', trigger: 'blur' }],
  receiverAddress: [{ required: true, message: '请输入收货地址', trigger: 'blur' }]
}

function openCreate() {
  createForm.receiverName = ''
  createForm.receiverPhone = ''
  createForm.receiverAddress = ''
  createForm.remark = ''
  createVisible.value = true
}

async function handleCreate() {
  await formRef.value?.validate()
  creating.value = true
  try {
    await logisticsApi.addOrder({ ...createForm })
    ElMessage.success('物流订单创建成功')
    createVisible.value = false
    handleSearch()
  } finally {
    creating.value = false
  }
}

/* ---------- 调度 ---------- */
const dispatchVisible = ref(false)
const dispatchSaving = ref(false)
const dispatchOrderId = ref(0)
const dispatchVehicleId = ref<number | undefined>(undefined)
const idleVehicles = ref<Vehicle[]>([])

async function openDispatch(row: LogisticsOrder) {
  dispatchOrderId.value = row.id
  dispatchVehicleId.value = undefined
  try {
    const vehicles = await vehicleApi.list()
    idleVehicles.value = (vehicles || []).filter((v) => v.status === 0)
  } catch {
    idleVehicles.value = []
  }
  dispatchVisible.value = true
}

async function handleDispatch() {
  if (!dispatchVehicleId.value) {
    ElMessage.warning('请选择调度车辆')
    return
  }
  dispatchSaving.value = true
  try {
    await logisticsApi.dispatch(dispatchOrderId.value, dispatchVehicleId.value)
    ElMessage.success('调度成功，订单已进入运输中')
    dispatchVisible.value = false
    loadData()
  } finally {
    dispatchSaving.value = false
  }
}

/* ---------- 签收 / 取消 ---------- */
async function handleDeliver(row: LogisticsOrder) {
  try {
    await ElMessageBox.confirm(`确定订单「${row.orderNo}」已签收吗？`, '签收确认', {
      type: 'warning',
      confirmButtonText: '确定签收',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await logisticsApi.deliver(row.id)
  ElMessage.success('签收成功')
  loadData()
}

async function handleCancel(row: LogisticsOrder) {
  try {
    await ElMessageBox.confirm(`确定取消订单「${row.orderNo}」吗？`, '取消确认', {
      type: 'warning',
      confirmButtonText: '确定取消',
      cancelButtonText: '返回'
    })
  } catch {
    return
  }
  await logisticsApi.cancel(row.id)
  ElMessage.success('订单已取消')
  loadData()
}

/* ---------- 轨迹 ---------- */
const trackVisible = ref(false)
const trackLoading = ref(false)
const trackOrderId = ref(0)
const trackOrderStatus = ref(0)
const tracks = ref<LogTrack[]>([])
const trackForm = reactive({ location: '', content: '' })

async function openTrack(row: LogisticsOrder) {
  trackOrderId.value = row.id
  trackOrderStatus.value = row.status
  trackForm.location = ''
  trackForm.content = ''
  trackVisible.value = true
  await loadTracks()
}

async function loadTracks() {
  trackLoading.value = true
  try {
    const res = await logisticsApi.tracks(trackOrderId.value)
    tracks.value = (res || []).slice().reverse()
  } finally {
    trackLoading.value = false
  }
}

async function handleAddTrack() {
  if (!trackForm.location.trim() || !trackForm.content.trim()) {
    ElMessage.warning('请填写轨迹位置和内容')
    return
  }
  await logisticsApi.addTrack(trackOrderId.value, {
    location: trackForm.location.trim(),
    content: trackForm.content.trim()
  })
  ElMessage.success('轨迹添加成功')
  trackForm.location = ''
  trackForm.content = ''
  await loadTracks()
  loadData()
}

/* ---------- AI 解读 ---------- */
const aiVisible = ref(false)
const aiLoading = ref(false)
const aiReply = ref('')
const aiMode = ref<'ai' | 'rule'>('ai')

async function openAi(row: LogisticsOrder) {
  aiVisible.value = true
  aiLoading.value = true
  aiReply.value = ''
  try {
    const res = await aiApi.trackExplain(row.id)
    aiReply.value = res.reply
    aiMode.value = res.mode
  } finally {
    aiLoading.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <div class="toolbar">
      <el-input
        v-model="query.orderNo"
        placeholder="物流单号"
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
      <el-button v-if="canWrite" type="success" :icon="Plus" @click="openCreate">新建物流订单</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="orderNo" label="物流单号" min-width="160" />
      <el-table-column prop="outboundOrderNo" label="关联出库单" min-width="150">
        <template #default="{ row }">{{ row.outboundOrderNo || '-' }}</template>
      </el-table-column>
      <el-table-column prop="receiverName" label="收货人" width="100" />
      <el-table-column prop="receiverPhone" label="联系电话" width="130" />
      <el-table-column prop="receiverAddress" label="收货地址" min-width="180" show-overflow-tooltip />
      <el-table-column prop="vehicleCode" label="车牌号" width="110">
        <template #default="{ row }">{{ row.vehicleCode || '-' }}</template>
      </el-table-column>
      <el-table-column prop="driverName" label="司机" width="90">
        <template #default="{ row }">{{ row.driverName || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="statusMap[row.status]?.type || 'info'">{{ statusMap[row.status]?.label || '未知' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="100" show-overflow-tooltip />
      <el-table-column label="操作" width="270" fixed="right">
        <template #default="{ row }">
          <el-button v-if="canWrite && row.status === 0" link type="success" @click="openDispatch(row)">调度</el-button>
          <el-button v-if="canWrite && row.status === 1" link type="success" @click="handleDeliver(row)">签收</el-button>
          <el-button v-if="canWrite && row.status === 1" link type="warning" @click="handleCancel(row)">取消</el-button>
          <el-button link type="primary" @click="openTrack(row)">轨迹</el-button>
          <el-button link type="primary" @click="openAi(row)">AI 解读</el-button>
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

    <!-- 新建物流订单 -->
    <el-dialog v-model="createVisible" title="新建物流订单" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="createForm" :rules="createRules" label-width="90px">
        <el-form-item label="收货人" prop="receiverName">
          <el-input v-model="createForm.receiverName" placeholder="请输入收货人姓名" />
        </el-form-item>
        <el-form-item label="联系电话" prop="receiverPhone">
          <el-input v-model="createForm.receiverPhone" placeholder="请输入收货人电话" />
        </el-form-item>
        <el-form-item label="收货地址" prop="receiverAddress">
          <el-input v-model="createForm.receiverAddress" type="textarea" :rows="2" placeholder="请输入收货地址" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" placeholder="备注信息（选填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">确定</el-button>
      </template>
    </el-dialog>

    <!-- 调度弹窗 -->
    <el-dialog v-model="dispatchVisible" title="订单调度" width="460px" destroy-on-close>
      <el-alert
        v-if="!idleVehicles.length"
        title="当前没有空闲车辆，请先在车辆管理中添加或释放车辆"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-select v-else v-model="dispatchVehicleId" filterable placeholder="请选择空闲车辆" style="width: 100%">
        <el-option
          v-for="v in idleVehicles"
          :key="v.id"
          :label="`${v.code}（司机：${v.driverName || '-'}）`"
          :value="v.id"
        />
      </el-select>
      <template #footer>
        <el-button @click="dispatchVisible = false">取消</el-button>
        <el-button type="primary" :loading="dispatchSaving" :disabled="!idleVehicles.length" @click="handleDispatch">
          确定调度
        </el-button>
      </template>
    </el-dialog>

    <!-- 轨迹抽屉 -->
    <el-drawer v-model="trackVisible" title="物流轨迹" size="480px">
      <div v-loading="trackLoading">
        <el-empty v-if="!tracks.length && !trackLoading" description="暂无轨迹信息" />
        <el-timeline v-else class="track-timeline">
          <el-timeline-item
            v-for="(t, i) in tracks"
            :key="i"
            :timestamp="formatTime(t.trackTime)"
            placement="top"
            :type="i === 0 ? 'primary' : undefined"
          >
            <div class="track-content">{{ t.content }}</div>
            <div class="track-loc">
              <el-icon><Location /></el-icon>
              <span>{{ t.location }}</span>
            </div>
          </el-timeline-item>
        </el-timeline>

        <template v-if="canWrite && trackOrderStatus === 1">
          <div class="section-title">追加轨迹</div>
          <el-form label-width="80px" @submit.prevent>
            <el-form-item label="当前位置">
              <el-input v-model="trackForm.location" placeholder="如：武汉市沌口物流园" />
            </el-form-item>
            <el-form-item label="轨迹内容">
              <el-input v-model="trackForm.content" type="textarea" :rows="2" placeholder="如：货物已装车发运" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleAddTrack">添加轨迹</el-button>
            </el-form-item>
          </el-form>
        </template>
      </div>
    </el-drawer>

    <!-- AI 解读弹窗 -->
    <el-dialog v-model="aiVisible" title="AI 物流轨迹解读" width="620px" destroy-on-close>
      <div v-loading="aiLoading" class="ai-body" style="min-height: 120px">
        <template v-if="aiReply">
          <el-alert
            v-if="aiMode === 'rule'"
            title="当前为离线规则模式回复"
            type="info"
            :closable="false"
            show-icon
            style="margin-bottom: 12px"
          />
          <div class="pre-wrap">{{ aiReply }}</div>
        </template>
      </div>
      <template #footer>
        <el-button type="primary" @click="aiVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.track-timeline {
  padding-left: 4px;
}

.track-content {
  font-size: 14px;
  color: #303133;
}

.track-loc {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}
</style>
