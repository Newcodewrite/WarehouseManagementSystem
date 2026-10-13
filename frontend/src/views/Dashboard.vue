<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { dashboardApi, type DashboardStats, type TrendData, type KpiData } from '@/api/modules'
import { formatMoney, formatNumber } from '@/utils/format'

const stats = ref<DashboardStats | null>(null)
const kpi = ref<KpiData | null>(null)
const loading = ref(false)

const statCards = computed(() => {
  const s = stats.value
  return [
    { label: '库存总量', value: formatNumber(s?.inventoryTotal), icon: 'Box', color: '#409eff', sub: `库存货值 ${formatMoney(s?.inventoryValue)}` },
    { label: '库存预警', value: formatNumber(s?.alertCount), icon: 'WarningFilled', color: '#f56c6c', sub: '低于预警库存的记录数' },
    { label: '在途订单', value: formatNumber(s?.orderTransit), icon: 'Promotion', color: '#e6a23c', sub: '物流运输中的订单' },
    { label: '待入库', value: formatNumber(s?.inboundPending), icon: 'Download', color: '#67c23a', sub: '待审核入库单' },
    { label: '待出库', value: formatNumber(s?.outboundPending), icon: 'Upload', color: '#909399', sub: '待审核出库单' },
    { label: '空闲车辆', value: formatNumber(s?.vehicleIdle), icon: 'Van', color: '#00b2a9', sub: '当前空闲可调度' },
    { label: '商品数', value: formatNumber(s?.productCount), icon: 'Goods', color: '#7c3aed', sub: '在册商品总数' },
    { label: '仓库数', value: formatNumber(s?.warehouseCount), icon: 'House', color: '#f59e0b', sub: '在用仓库总数' }
  ]
})

const kpiCards = computed(() => {
  const k = kpi.value
  return [
    { label: '库存周转率(30天)', value: k?.turnoverRate30 ?? '—', icon: 'Refresh', color: '#409eff', sub: '近30天出库量 / 当前库存' },
    { label: '近30天出库量', value: formatNumber(k?.outQty30), icon: 'Promotion', color: '#67c23a', sub: `累计出库 ${formatNumber(k?.outQtyAll)}` },
    { label: '入库及时率', value: k ? `${k.inboundOnTimeRate}%` : '—', icon: 'Timer', color: '#e6a23c', sub: `24小时内审核，共 ${k?.inboundTotal ?? 0} 单` },
    { label: '盘点准确率', value: k ? `${k.stocktakeAccuracy}%` : '—', icon: 'CircleCheck', color: '#00b2a9', sub: `已完成 ${k?.stocktakeCount ?? 0} 次盘点` }
  ]
})

const chartRef = ref<HTMLDivElement>()
const ageChartRef = ref<HTMLDivElement>()
const dormantChartRef = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null
let ageChart: echarts.ECharts | null = null
let dormantChart: echarts.ECharts | null = null

function renderChart(t: TrendData) {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['入库数量', '出库数量'], top: 0 },
    grid: { left: 48, right: 24, top: 44, bottom: 32 },
    xAxis: { type: 'category', boundaryGap: false, data: t.labels || [] },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        name: '入库数量',
        type: 'line',
        smooth: true,
        data: t.inData || [],
        itemStyle: { color: '#409eff' },
        areaStyle: { opacity: 0.12 }
      },
      {
        name: '出库数量',
        type: 'line',
        smooth: true,
        data: t.outData || [],
        itemStyle: { color: '#67c23a' },
        areaStyle: { opacity: 0.12 }
      }
    ]
  })
}

function handleResize() {
  chart?.resize()
  ageChart?.resize()
  dormantChart?.resize()
}

function renderAgeChart(k: KpiData) {
  if (!ageChartRef.value) return
  if (!ageChart) ageChart = echarts.init(ageChartRef.value)
  ageChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}：{c} 件 ({d}%)' },
    legend: { bottom: 0 },
    color: ['#67c23a', '#409eff', '#e6a23c', '#f56c6c'],
    series: [{
      type: 'pie',
      radius: ['42%', '68%'],
      center: ['50%', '44%'],
      data: (k.ageDistribution || []).map((d) => ({ name: d.name, value: d.value }))
    }]
  })
}

function renderDormantChart(k: KpiData) {
  if (!dormantChartRef.value) return
  if (!dormantChart) dormantChart = echarts.init(dormantChartRef.value)
  const list = (k.dormantTop10 || []).slice(0, 10).reverse()
  dormantChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 110, right: 40, top: 16, bottom: 32 },
    xAxis: { type: 'value', minInterval: 1 },
    yAxis: { type: 'category', data: list.map((d) => `${d.productCode} ${d.productName}`) },
    series: [{
      type: 'bar',
      data: list.map((d) => d.quantity),
      itemStyle: { color: '#e6a23c', borderRadius: [0, 4, 4, 0] },
      label: { show: true, position: 'right' }
    }]
  })
}

onMounted(async () => {
  window.addEventListener('resize', handleResize)
  loading.value = true
  try {
    const [s, t, k] = await Promise.all([dashboardApi.stats(), dashboardApi.trend(7), dashboardApi.kpi()])
    stats.value = s
    kpi.value = k
    renderChart(t)
    renderAgeChart(k)
    renderDormantChart(k)
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  ageChart?.dispose()
  dormantChart?.dispose()
  chart = null
  ageChart = null
  dormantChart = null
})
</script>

<template>
  <div class="page-container" v-loading="loading">
    <el-row :gutter="14">
      <el-col v-for="c in statCards" :key="c.label" :xs="12" :sm="8" :md="6" :lg="3" class="stat-col">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-top">
            <span class="stat-label">{{ c.label }}</span>
            <el-icon :size="22" :color="c.color"><component :is="c.icon" /></el-icon>
          </div>
          <div class="stat-value">{{ c.value }}</div>
          <div class="stat-sub">{{ c.sub }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 智能运营 KPI -->
    <el-row :gutter="14">
      <el-col v-for="c in kpiCards" :key="c.label" :xs="12" :sm="8" :md="6" class="stat-col">
        <el-card shadow="hover" class="kpi-card">
          <div class="stat-top">
            <span class="stat-label">{{ c.label }}</span>
            <el-icon :size="20" :color="c.color"><component :is="c.icon" /></el-icon>
          </div>
          <div class="stat-value kpi-value">{{ c.value }}</div>
          <div class="stat-sub">{{ c.sub }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <template #header>
        <span class="chart-title">近 7 天出入库趋势</span>
      </template>
      <div ref="chartRef" class="chart"></div>
    </el-card>

    <el-row :gutter="14" class="chart-row">
      <el-col :xs="24" :md="10">
        <el-card shadow="never">
          <template #header><span class="chart-title">库存库龄分布</span></template>
          <div ref="ageChartRef" class="chart-sm"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="14">
        <el-card shadow="never">
          <template #header><span class="chart-title">呆滞库存 TOP10（近180天无出库）</span></template>
          <div ref="dormantChartRef" class="chart-sm"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.stat-col {
  margin-bottom: 14px;
}

.stat-card :deep(.el-card__body) {
  padding: 14px;
}

.stat-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.stat-label {
  font-size: 13px;
  color: #606266;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  margin-top: 6px;
  color: #303133;
}

.stat-sub {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.chart-title {
  font-weight: 600;
}

.chart {
  width: 100%;
  height: 360px;
}

.kpi-card :deep(.el-card__body) {
  padding: 14px;
}

.kpi-value {
  color: #409eff;
}

.chart-row {
  margin-top: 14px;
}

.chart-sm {
  width: 100%;
  height: 320px;
}
</style>
