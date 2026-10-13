<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { MagicStick, Paperclip, Van } from '@element-plus/icons-vue'
import { aiApi, type ChatMessage } from '@/api/modules'

interface ChatItem extends ChatMessage {
  mode?: 'ai' | 'rule'
}

const messages = ref<ChatItem[]>([
  {
    role: 'assistant',
    content:
      '您好！我是智能物流仓储 AI 管家小仓。我不仅能解答问题，还可以直接帮您操作系统：\n' +
      '· 商品：查询、新增、修改、删除\n' +
      '· 库存：查询库存、查看库存预警\n' +
      '· 入库/出库：创建单据、审核（审核后自动增减库存）\n' +
      '· 物流：查询物流订单状态\n' +
      '· 文件导入：点击输入框左侧 📎 上传 Excel/CSV，批量导入商品档案或库存\n' +
      '您可以直接用自然语言吩咐，例如：「帮我查一下蓝牙耳机的库存」「新增一个商品：折叠雨伞，日用品，单价39元」「给上海仓库做一张100个保温杯的入库单」。'
  }
])
const input = ref('')
const sending = ref(false)
const uploading = ref(false)
const fileInput = ref<HTMLInputElement>()
const listRef = ref<HTMLDivElement>()

/** 点击回形针：打开系统文件选择框 */
function openFilePicker() {
  if (sending.value || uploading.value) return
  fileInput.value?.click()
}

/** 选中文件后：上传解析 → 自动发起 AI 导入对话 */
async function onFileChange(e: Event) {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  target.value = '' // 重置，允许再次选择同一文件
  if (!file || uploading.value || sending.value) return
  uploading.value = true
  const loadingMsg = ElMessage.info({ message: `正在解析文件「${file.name}」…`, duration: 0 })
  try {
    const info = await aiApi.uploadFile(file)
    loadingMsg.close()
    ElMessage.success(`文件解析成功：${info.rowCount} 行`)
    const typeName =
      info.detectedType === 'STOCK'
        ? '库存入库导入'
        : info.detectedType === 'PRODUCT'
          ? '商品档案导入'
          : '导入类型待确认'
    const content = `📁 已上传文件「${info.fileName}」（共 ${info.rowCount} 行，列：${info.columns.join('、')}，识别为${typeName}）\n请帮我导入这个文件（fileToken=${info.fileToken}）`
    const history: ChatMessage[] = messages.value.slice(-10).map((m) => ({ role: m.role, content: m.content }))
    messages.value.push({ role: 'user', content })
    sending.value = true
    try {
      const res = await aiApi.chat({ message: content, history })
      messages.value.push({ role: 'assistant', content: res.reply, mode: res.mode })
    } finally {
      sending.value = false
    }
  } catch {
    loadingMsg.close()
    // 请求层响应拦截器已弹出具体错误提示
  } finally {
    uploading.value = false
  }
}

/** 示例指令（点击直接发送） */
const examples = [
  '查询一下蓝牙耳机的库存',
  '现在有哪些库存预警？',
  '帮我新增一个商品：折叠雨伞，日用品，单位把，单价39元',
  '查一下待审核的入库单',
  '最近有哪些物流订单在运输中？'
]

watch(
  () => messages.value.length,
  () => {
    nextTick(() => {
      if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight
    })
  }
)

/** 发送消息（携带最近 10 条历史） */
async function send(text?: string) {
  const content = (text ?? input.value).trim()
  if (!content || sending.value) return
  const history: ChatMessage[] = messages.value.slice(-10).map((m) => ({ role: m.role, content: m.content }))
  messages.value.push({ role: 'user', content })
  input.value = ''
  sending.value = true
  try {
    const res = await aiApi.chat({ message: content, history })
    messages.value.push({ role: 'assistant', content: res.reply, mode: res.mode })
  } finally {
    sending.value = false
  }
}

/** 快捷建议 */
async function quick(kind: 'stock' | 'schedule') {
  if (sending.value) return
  sending.value = true
  try {
    const res = kind === 'stock' ? await aiApi.stockAdvice() : await aiApi.scheduleAdvice()
    messages.value.push({ role: 'assistant', content: res.reply, mode: res.mode })
  } finally {
    sending.value = false
  }
}

onMounted(() => {
  if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight
})
</script>

<template>
  <div class="chat-page">
    <!-- 顶部：标题 + 快捷按钮 -->
    <div class="chat-header">
      <div class="chat-title">
        <el-icon :size="20" color="#409eff"><ChatDotRound /></el-icon>
        <span>AI 管家 · 可直接管理商品与出入库</span>
      </div>
      <div class="chat-quick">
        <el-button size="small" round :icon="MagicStick" :disabled="sending" @click="quick('stock')">
          库存补货建议
        </el-button>
        <el-button size="small" round :icon="Van" :disabled="sending" @click="quick('schedule')">
          调度建议
        </el-button>
      </div>
    </div>

    <!-- 示例指令 -->
    <div class="chat-examples">
      <span class="examples-label">试试指令：</span>
      <el-tag
        v-for="(ex, i) in examples"
        :key="i"
        class="example-tag"
        effect="plain"
        :disable-transitions="true"
        @click="send(ex)"
      >
        {{ ex }}
      </el-tag>
    </div>

    <!-- 消息列表 -->
    <div ref="listRef" class="chat-body">
      <div v-for="(m, i) in messages" :key="i" class="msg-row" :class="m.role">
        <div class="bubble" :class="m.role">
          <div class="msg-text pre-wrap">{{ m.content }}</div>
          <div v-if="m.mode === 'rule'" class="msg-mode">离线模式回复</div>
        </div>
      </div>
      <div v-if="sending" class="msg-row assistant">
        <div class="bubble assistant thinking">思考中…</div>
      </div>
    </div>

    <!-- 底部输入 -->
    <div class="chat-footer">
      <!-- 原生文件选择：支持 .csv / .xlsx / .xls -->
      <input
        ref="fileInput"
        type="file"
        accept=".csv,.xlsx,.xls"
        style="display: none"
        @change="onFileChange"
      />
      <el-button
        class="upload-btn"
        :loading="uploading"
        :disabled="sending || uploading"
        title="上传 Excel/CSV，AI 批量导入商品或库存"
        @click="openFilePicker"
      >
        <el-icon :size="18"><Paperclip /></el-icon>
      </el-button>
      <el-input
        v-model="input"
        type="textarea"
        :rows="2"
        resize="none"
        placeholder="用自然语言吩咐，例如：查库存、新增商品、创建入库单… Enter 发送"
        :disabled="sending"
        @keydown.enter.exact.prevent="send()"
      />
      <el-button type="primary" class="send-btn" :loading="sending" :disabled="sending" @click="send()">
        {{ sending ? '思考中…' : '发送' }}
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.chat-page {
  height: calc(100vh - 88px);
  padding: 16px;
  display: flex;
  flex-direction: column;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-radius: 8px;
  padding: 10px 16px;
  margin-bottom: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.chat-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
}

.chat-examples {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  background: #fff;
  border-radius: 8px;
  padding: 8px 16px;
  margin-bottom: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.examples-label {
  font-size: 13px;
  color: #909399;
}

.example-tag {
  cursor: pointer;
  font-size: 12px;
}

.example-tag:hover {
  color: #409eff;
  border-color: #409eff;
}

.chat-body {
  flex: 1;
  overflow-y: auto;
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.msg-row {
  display: flex;
  margin-bottom: 14px;
}

.msg-row.user {
  justify-content: flex-end;
}

.msg-row.assistant {
  justify-content: flex-start;
}

.bubble {
  max-width: 72%;
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 14px;
  line-height: 1.6;
}

.bubble.user {
  background: #409eff;
  color: #fff;
  border-top-right-radius: 2px;
}

.bubble.assistant {
  background: #f4f4f5;
  color: #303133;
  border-top-left-radius: 2px;
}

.bubble.thinking {
  color: #909399;
  font-style: italic;
}

.msg-mode {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}

.chat-footer {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  margin-top: 12px;
}

.upload-btn {
  height: 54px;
  padding: 8px 12px;
}

.send-btn {
  height: 54px;
  min-width: 96px;
}
</style>
