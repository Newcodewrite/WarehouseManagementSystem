<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { authApi } from '@/api/modules'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: 'admin', password: '123456' })

const rules: FormRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

function handleLogin() {
  formRef.value?.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const res = await authApi.login({ username: form.username, password: form.password })
      userStore.setLogin(res.token, res.user)
      ElMessage.success('登录成功')
      router.push('/dashboard')
    } finally {
      loading.value = false
    }
  })
}
</script>

<template>
  <div class="login-page">
    <el-card class="login-card" shadow="always">
      <div class="login-title">
        <el-icon :size="30" color="#409eff"><Box /></el-icon>
        <h2>智能物流仓储管理系统</h2>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="请输入账号" :prefix-icon="User" clearable />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" :prefix-icon="Lock" />
        </el-form-item>
        <el-form-item>
          <el-button class="login-btn" type="primary" size="large" :loading="loading" @click="handleLogin">
            登 录
          </el-button>
        </el-form-item>
      </el-form>
      <div class="login-tips">
        <div class="tips-title">演示账号（密码均为 123456）：</div>
        <div>admin —— 管理员（全部权限）</div>
        <div>op —— 仓管 / 物流操作员</div>
        <div>user —— 普通用户（仅查询）</div>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1e3a8a 0%, #2563eb 45%, #38bdf8 100%);
}

.login-card {
  width: 400px;
  border-radius: 10px;
}

.login-title {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-bottom: 22px;
}

.login-title h2 {
  margin: 0;
  font-size: 20px;
  color: #303133;
}

.login-btn {
  width: 100%;
}

.login-tips {
  background: #f5f7fa;
  border-radius: 6px;
  padding: 10px 12px;
  font-size: 12px;
  color: #909399;
  line-height: 1.9;
}

.tips-title {
  font-weight: 600;
  color: #606266;
}
</style>
