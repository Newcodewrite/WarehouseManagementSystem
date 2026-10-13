<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { MagicStick } from '@element-plus/icons-vue'
import { aiApi, inventoryApi, type InventoryAlert } from '@/api/modules'

/* ---------- 预警列表 ---------- */
const loading = ref(false)
const list = ref<InventoryAlert[]>([])
const total = ref(0)
const query = reactive({ current: 1, size: 10 })

async function loadData() {
  loading.value = true
  try {
    const res = await inventoryApi.alertPage({ current: query.current, size: query.size })
    list.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

/* ---------- AI 补货建议 ---------- */
const aiVisible = ref(false)
const aiLoading = ref(false)
const aiReply = ref('')
const aiMode = ref<'ai' | 'rule'>('ai')

async function openAiAdvice() {
  aiVisible.value = true
  aiLoading.value = true
  aiReply.value = ''
  try {
    const res = await aiApi.stockAdvice()
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
      <span class="page-tip">以下商品库存已低于预警值，建议及时补货</span>
      <el-button type="primary" :icon="MagicStick" :loading="aiLoading" @click="openAiAdvice">AI 补货建议</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="warehouseName" label="仓库" min-width="130" />
      <el-table-column prop="productCode" label="商品编码" min-width="120" />
      <el-table-column prop="productName" label="商品名称" min-width="150" />
      <el-table-column prop="category" label="分类" width="100" />
      <el-table-column prop="unit" label="单位" width="70" align="center" />
      <el-table-column label="当前库存" width="100" align="right">
        <template #default="{ row }">
          <span class="qty-danger">{{ row.quantity }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="warnStock" label="预警库存" width="100" align="right" />
      <el-table-column label="建议补货量" width="110" align="right">
        <template #default="{ row }">
          <span class="qty-suggest">{{ row.suggestQty }}</span>
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
      @size-change="loadData"
    />

    <!-- AI 补货建议弹窗 -->
    <el-dialog v-model="aiVisible" title="AI 补货建议" width="620px" destroy-on-close>
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
.page-tip {
  color: #909399;
  font-size: 13px;
  margin-right: auto;
}

.qty-danger {
  color: #f56c6c;
  font-weight: 600;
}

.qty-suggest {
  color: #67c23a;
  font-weight: 600;
}
</style>
