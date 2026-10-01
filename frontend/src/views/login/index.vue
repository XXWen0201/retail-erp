<template>
  <div class="login-page">
    <div class="brand">
      <div class="brand-inner">
        <div class="brand-logo">ERP</div>
        <h1>中小零售门店<br />进销存与库存预警管理系统</h1>
        <p class="brand-desc">
          采购 · 销售 · 库存 · 盘点 · 预警 · 智能补货，一套系统管完一家门店的货。
        </p>
        <ul class="brand-points">
          <li><span class="dot" />批次与保质期管理，先到期先出（FEFO）</li>
          <li><span class="dot" />库存扣减一致性：数据库乐观锁 / Redis+Lua 双方案</li>
          <li><span class="dot" />Spring AI 智能补货建议与库存问答</li>
        </ul>
      </div>
    </div>

    <div class="form-wrap">
      <div class="form-box">
        <h2>账号登录</h2>
        <p class="tip">请输入账号密码进入系统</p>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          size="large"
          @keyup.enter="onSubmit"
        >
          <el-form-item prop="username">
            <el-input
              v-model="form.username"
              placeholder="用户名"
              clearable
            >
              <template #prefix><el-icon><User /></el-icon></template>
            </el-input>
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="密码"
              show-password
            >
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </el-form-item>

          <el-form-item>
            <el-button
              type="primary"
              class="submit"
              :loading="loading"
              @click="onSubmit"
            >
              {{ loading ? '登录中…' : '登 录' }}
            </el-button>
          </el-form-item>
        </el-form>

        <el-alert type="info" :closable="false" class="account-tip">
          <div class="account-line">
            演示账号：
            <el-link type="primary" :underline="false" @click="fill('admin')">admin / 123456</el-link>
            （管理员，全部权限）
          </div>
          <div class="account-line">
            <el-link type="primary" :underline="false" @click="fill('manager')">manager / 123456</el-link>
            （店长）
          </div>
          <div class="account-line">
            <el-link type="primary" :underline="false" @click="fill('staff')">staff / 123456</el-link>
            （店员）
          </div>
        </el-alert>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)

const form = reactive({
  username: 'admin',
  password: '123456'
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ]
}

function fill(username) {
  form.username = username
  form.password = '123456'
}

async function onSubmit() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  loading.value = true
  try {
    await userStore.login({ username: form.username, password: form.password })
    ElMessage.success('登录成功')
    const redirect = route.query.redirect
    router.replace(redirect ? String(redirect) : '/dashboard')
  } catch (e) {
    // 登录接口设了 silent，这里统一提示，避免和拦截器重复弹两次
    ElMessage.error(e.message || '登录失败，请检查账号密码')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  overflow: hidden;
}

.brand {
  flex: 1.15;
  background: linear-gradient(140deg, #1f2937, #2b4a7d 55%, #1d4ed8);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
  position: relative;
}

.brand::after {
  content: '';
  position: absolute;
  right: -80px;
  bottom: -80px;
  width: 320px;
  height: 320px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.06);
}

.brand-inner {
  max-width: 460px;
  position: relative;
  z-index: 1;
}

.brand-logo {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.16);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  letter-spacing: 1px;
  margin-bottom: 22px;
}

.brand h1 {
  font-size: 27px;
  line-height: 1.45;
  margin: 0 0 14px;
  font-weight: 600;
}

.brand-desc {
  color: rgba(255, 255, 255, 0.78);
  line-height: 1.7;
  margin: 0 0 26px;
  font-size: 14px;
}

.brand-points {
  list-style: none;
  padding: 0;
  margin: 0;
}

.brand-points li {
  display: flex;
  align-items: center;
  gap: 9px;
  color: rgba(255, 255, 255, 0.86);
  font-size: 13.5px;
  margin-bottom: 12px;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #60a5fa;
  flex-shrink: 0;
}

.form-wrap {
  flex: 0.85;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  padding: 40px;
}

.form-box {
  width: 100%;
  max-width: 360px;
}

.form-box h2 {
  margin: 0 0 6px;
  font-size: 22px;
  color: #1f2d3d;
}

.tip {
  margin: 0 0 26px;
  color: #909399;
  font-size: 13px;
}

.submit {
  width: 100%;
  letter-spacing: 4px;
}

.account-tip {
  margin-top: 6px;
}

.account-line {
  line-height: 1.9;
  font-size: 13px;
}

@media (max-width: 860px) {
  .brand {
    display: none;
  }
  .form-wrap {
    flex: 1;
    padding: 24px;
  }
}
</style>
