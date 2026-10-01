<script setup>
import { onLaunch, onShow } from '@dcloudio/uni-app'
import { useUserStore } from './stores/user'

onLaunch(() => {
  // 冷启动时尽力恢复登录态：本地还留着刷新令牌就用它换一张新的访问令牌，
  // 用户不必重新输账号密码。
  // 失败也刻意不弹提示 —— 用户还没开始操作，不该先被一个错误打断；
  // 后续任何接口返回 401 时，请求层会统一把人引导到登录页。
  useUserStore()
    .restore()
    .catch(() => {})
})

onShow(() => {
  // 从后台切回前台时不做强制校验，交给具体页面的下拉刷新处理，
  // 免得用户正在填单子时被弹窗打断
})
</script>

<style lang="scss">
/* ------------------------------------------------------------------
 * 全局基础样式
 * 只放真正跨页面复用的部分，页面私有样式留在各自的 vue 文件里
 * ------------------------------------------------------------------ */

page {
  background-color: $erp-bg;
  color: $erp-text;
  font-size: 28rpx;
  line-height: 1.55;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC',
    'Helvetica Neue', Helvetica, 'Microsoft YaHei', sans-serif;
}

/* ---------- 布局工具类 ---------- */
.row {
  display: flex;
  align-items: center;
}
.row-between {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.row-center {
  display: flex;
  align-items: center;
  justify-content: center;
}
.col {
  display: flex;
  flex-direction: column;
}
.flex-1 {
  flex: 1;
  min-width: 0;
}

/* ---------- 卡片 ---------- */
.card {
  background: $erp-card;
  border-radius: $erp-radius;
  padding: 28rpx;
  box-shadow: $erp-shadow;
}

/* ---------- 文本 ---------- */
.t-title {
  font-size: 32rpx;
  font-weight: 600;
}
.t-sub {
  font-size: 26rpx;
  color: $erp-text-sub;
}
.t-muted {
  font-size: 24rpx;
  color: $erp-text-muted;
}
.t-money {
  font-size: 46rpx;
  font-weight: 700;
  letter-spacing: -1rpx;
}
.ellipsis {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.nowrap {
  white-space: nowrap;
}

/* ---------- 空状态 ---------- */
.empty {
  padding: 140rpx 40rpx;
  text-align: center;
  color: $erp-text-muted;
  font-size: 26rpx;
}

/* ---------- 底部安全区（全面屏） ---------- */
.safe-bottom {
  padding-bottom: calc(24rpx + env(safe-area-inset-bottom));
}

/* 去掉 uni-app 默认按钮的边框，避免和自定义样式打架 */
button::after {
  border: none;
}
</style>
