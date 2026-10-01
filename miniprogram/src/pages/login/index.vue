<template>
  <view class="login">
    <!-- 顶部品牌区 -->
    <view class="brand">
      <view class="logo">
        <text class="logo-text">零</text>
      </view>
      <text class="brand-name">零售进销存</text>
      <text class="brand-sub">门店移动管理端</text>
    </view>

    <!-- 登录表单 -->
    <view class="panel">
      <view class="field" :class="{ focus: focus === 'username' }">
        <text class="label">账号</text>
        <input
          class="input"
          v-model="form.username"
          placeholder="请输入账号"
          placeholder-class="ph"
          :adjust-position="true"
          @focus="focus = 'username'"
          @blur="focus = ''"
        />
      </view>

      <view class="field" :class="{ focus: focus === 'password' }">
        <text class="label">密码</text>
        <input
          class="input"
          v-model="form.password"
          password
          placeholder="请输入密码"
          placeholder-class="ph"
          :adjust-position="true"
          @focus="focus = 'password'"
          @blur="focus = ''"
          @confirm="submit"
        />
      </view>

      <view v-if="errorMsg" class="error">
        <text>{{ errorMsg }}</text>
      </view>

      <button class="btn-login" :class="{ disabled: loading }" :disabled="loading" @click="submit">
        {{ loading ? '登录中…' : '登 录' }}
      </button>
    </view>

    <!-- 演示账号：答辩或首次体验时一键填充，免得当场输错 -->
    <view class="demo">
      <text class="demo-title">演示账号（点击填充）</text>
      <view class="demo-list">
        <view
          v-for="item in demoAccounts"
          :key="item.username"
          class="demo-item"
          @click="fill(item)"
        >
          <text class="demo-role">{{ item.role }}</text>
          <text class="demo-account">{{ item.username }} / {{ item.password }}</text>
        </view>
      </view>
    </view>

    <view class="footer">
      <text class="t-muted">{{ tipText }}</text>
    </view>
  </view>
</template>

<script setup>
import { onLoad } from '@dcloudio/uni-app'
import { reactive, ref } from 'vue'
import { useUserStore } from '../../stores/user'

const store = useUserStore()

const form = reactive({
  username: '',
  password: ''
})

const loading = ref(false)
const focus = ref('')
const errorMsg = ref('')
const checking = ref(true)

const demoAccounts = [
  { role: '管理员', username: 'admin', password: '123456' },
  { role: '店长', username: 'manager', password: '123456' },
  { role: '店员', username: 'staff', password: '123456' }
]

const tipText = ref('请使用门店分配的账号登录')

function fill(item) {
  form.username = item.username
  form.password = item.password
  errorMsg.value = ''
}

async function submit() {
  if (loading.value) {
    return
  }
  errorMsg.value = ''
  if (!form.username.trim()) {
    errorMsg.value = '请输入账号'
    return
  }
  if (!form.password) {
    errorMsg.value = '请输入密码'
    return
  }

  loading.value = true
  try {
    await store.login(form.username.trim(), form.password)
    uni.reLaunch({ url: '/pages/dashboard/index' })
  } catch (e) {
    // 后端对「账号不存在」和「密码错误」返回同一句提示，
    // 这里原样展示即可，不额外暴露账号是否存在
    errorMsg.value = e && e.message ? e.message : '登录失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

onLoad(async () => {
  form.username = store.lastUsername()

  // 本地还有刷新令牌就直接换一张访问令牌进主页，
  // 不用让已经登录过的用户再看一次登录页
  const restored = await store.restore()
  checking.value = false
  if (restored) {
    uni.reLaunch({ url: '/pages/dashboard/index' })
  }
})
</script>

<style lang="scss" scoped>
.login {
  min-height: 100vh;
  padding: calc(var(--status-bar-height, 0px) + 100rpx) 56rpx 60rpx;
  box-sizing: border-box;
  background: linear-gradient(160deg, #3a78ff 0%, #2f6bff 42%, #1d4ed8 100%);
  display: flex;
  flex-direction: column;
}

/* ---------- 品牌区 ---------- */
.brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 72rpx;
}

.logo {
  width: 132rpx;
  height: 132rpx;
  border-radius: 36rpx;
  background: rgba(255, 255, 255, 0.18);
  border: 2rpx solid rgba(255, 255, 255, 0.32);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 32rpx;
}

.logo-text {
  font-size: 60rpx;
  font-weight: 700;
  color: #ffffff;
}

.brand-name {
  font-size: 46rpx;
  font-weight: 700;
  color: #ffffff;
  letter-spacing: 2rpx;
}

.brand-sub {
  margin-top: 12rpx;
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.78);
}

/* ---------- 表单面板 ---------- */
.panel {
  background: #ffffff;
  border-radius: 32rpx;
  padding: 44rpx 40rpx 48rpx;
  box-shadow: 0 20rpx 60rpx rgba(13, 40, 100, 0.16);
}

.field {
  border-bottom: 2rpx solid $erp-border;
  padding: 20rpx 0 24rpx;
  transition: border-color 0.2s;

  & + .field {
    margin-top: 8rpx;
  }

  &.focus {
    border-bottom-color: $erp-primary;
  }
}

.label {
  display: block;
  font-size: 24rpx;
  color: $erp-text-muted;
  margin-bottom: 10rpx;
}

.input {
  width: 100%;
  height: 56rpx;
  font-size: 32rpx;
  color: $erp-text;
}

.ph {
  color: #c2cad6;
  font-size: 30rpx;
}

.error {
  margin-top: 24rpx;
  padding: 18rpx 22rpx;
  border-radius: 14rpx;
  background: $erp-danger-soft;
  color: $erp-danger;
  font-size: 25rpx;
}

.btn-login {
  margin-top: 48rpx;
  height: 92rpx;
  line-height: 92rpx;
  border-radius: 46rpx;
  background: linear-gradient(135deg, #4b85ff, #2f6bff);
  color: #ffffff;
  font-size: 32rpx;
  font-weight: 600;
  letter-spacing: 6rpx;
  box-shadow: 0 12rpx 28rpx rgba(47, 107, 255, 0.32);

  &.disabled {
    opacity: 0.7;
    box-shadow: none;
  }
}

/* ---------- 演示账号 ---------- */
.demo {
  margin-top: 48rpx;
}

.demo-title {
  display: block;
  text-align: center;
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.72);
  margin-bottom: 20rpx;
}

.demo-list {
  display: flex;
  flex-direction: column;
}

.demo-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 28rpx;
  border-radius: 16rpx;
  background: rgba(255, 255, 255, 0.14);
  border: 2rpx solid rgba(255, 255, 255, 0.2);

  & + .demo-item {
    margin-top: 14rpx;
  }
}

.demo-role {
  font-size: 25rpx;
  color: #ffffff;
  font-weight: 600;
}

.demo-account {
  font-size: 25rpx;
  color: rgba(255, 255, 255, 0.82);
}

.footer {
  margin-top: auto;
  padding-top: 48rpx;
  text-align: center;

  .t-muted {
    color: rgba(255, 255, 255, 0.6);
  }
}
</style>
