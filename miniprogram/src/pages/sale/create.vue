<template>
  <view class="page">
    <!-- ---------- 客户信息 ---------- -->
    <view class="card">
      <view class="field">
        <text class="label">客户</text>
        <input
          class="input"
          v-model="customerName"
          placeholder="散客"
          placeholder-class="ph"
        />
      </view>
      <view class="field">
        <text class="label">单据日期</text>
        <text class="static-value">{{ orderDate }}</text>
      </view>
      <view class="field no-border">
        <text class="label">备注</text>
        <input
          class="input"
          v-model="remark"
          placeholder="可不填"
          placeholder-class="ph"
        />
      </view>
    </view>

    <!-- ---------- 商品明细 ---------- -->
    <view class="card">
      <view class="card-head">
        <text class="card-title">商品明细</text>
        <text class="card-extra">{{ cart.length }} 种 / {{ fmtQty(totalQuantity) }} 件</text>
      </view>

      <view v-if="cart.length" class="cart">
        <view v-for="item in cart" :key="item.productId" class="cart-item">
          <view class="cart-top">
            <text class="cart-name ellipsis">{{ item.name }}</text>
            <text class="cart-del" @click="removeItem(item)">删除</text>
          </view>

          <view class="cart-mid">
            <view class="price-edit">
              <text class="price-symbol">￥</text>
              <input
                class="price-input"
                type="digit"
                :value="String(item.price)"
                @input="onPriceInput(item, $event)"
              />
            </view>
            <text class="cart-stock">库存 {{ fmtQty(item.stock) }} {{ item.unit }}</text>
          </view>

          <view class="cart-bottom">
            <view class="stepper">
              <view class="step-btn" @click="changeQty(item, -1)">−</view>
              <input
                class="step-input"
                type="number"
                :value="String(item.quantity)"
                @input="onQtyInput(item, $event)"
              />
              <view class="step-btn" @click="changeQty(item, 1)">+</view>
            </view>
            <text class="cart-subtotal">￥{{ money(item.price * item.quantity) }}</text>
          </view>
        </view>
      </view>

      <view v-else class="cart-empty">还没有添加商品</view>

      <view class="add-product" @click="openPicker">
        <text class="add-text">+ 添加商品</text>
      </view>
    </view>

    <!-- ---------- 金额汇总 ---------- -->
    <view class="card">
      <view class="kv">
        <text class="k">合计金额</text>
        <text class="v">￥{{ money(totalAmount) }}</text>
      </view>
      <view class="kv">
        <text class="k">优惠金额</text>
        <view class="discount-edit">
          <text class="price-symbol">￥</text>
          <input
            class="discount-input"
            type="digit"
            v-model="discountAmount"
            placeholder="0.00"
            placeholder-class="ph"
          />
        </view>
      </view>
      <view class="kv total">
        <text class="k strong">应收金额</text>
        <text class="v pay">￥{{ money(payAmount) }}</text>
      </view>
    </view>

    <!-- 库存不足等业务错误就地展示，方便直接改数量重试 -->
    <view v-if="errorMsg" class="error-banner">
      <text class="error-text">{{ errorMsg }}</text>
    </view>

    <view class="bottom-space"></view>

    <!-- ---------- 底部结算栏 ---------- -->
    <view class="bar safe-bottom">
      <view class="bar-left">
        <text class="bar-label">应收</text>
        <text class="bar-amount">￥{{ money(payAmount) }}</text>
      </view>
      <view class="bar-btn" :class="{ disabled: submitting }" @click="submit">
        {{ submitting ? '提交中…' : '确认开单' }}
      </view>
    </view>

    <!-- ---------- 商品选择弹层 ---------- -->
    <view v-if="pickerVisible" class="mask" @click="closePicker">
      <view class="picker" @click.stop>
        <view class="picker-head">
          <text class="picker-title">选择商品</text>
          <text class="picker-close" @click="closePicker">完成</text>
        </view>

        <view class="picker-search">
          <input
            class="picker-input"
            v-model="pickerKeyword"
            placeholder="搜索商品名称 / 编码 / 条码"
            placeholder-class="ph"
            confirm-type="search"
            @confirm="loadPicker"
            @input="onPickerInput"
          />
        </view>

        <scroll-view class="picker-list" scroll-y>
          <view
            v-for="p in pickerList"
            :key="p.id"
            class="picker-item"
            @click="addProduct(p)"
          >
            <view class="picker-main">
              <text class="picker-name ellipsis">{{ p.name }}</text>
              <view class="picker-meta">
                <text class="meta-text">{{ p.code }}</text>
                <text class="meta-dot">·</text>
                <text class="meta-text">库存 {{ fmtQty(p.stockQuantity) }} {{ p.unit }}</text>
              </view>
            </view>
            <view class="picker-side">
              <text class="picker-price">￥{{ money(p.salePrice) }}</text>
              <text
                class="picker-tag"
                :class="Number(p.stockQuantity) > 0 ? 'ok' : 'no'"
              >
                {{ Number(p.stockQuantity) > 0 ? '可售' : '无货' }}
              </text>
            </view>
          </view>

          <view v-if="pickerLoading" class="picker-tip">加载中…</view>
          <view v-else-if="!pickerList.length" class="picker-tip">没有找到商品</view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getProduct, pageProducts } from '../../api/product'
import { createSale } from '../../api/sale'
import { money, quantity as fmtQty, today } from '../../utils/format'

const orderDate = today()

const customerName = ref('散客')
const remark = ref('')
const discountAmount = ref('')

/** 购物车：[{ productId, name, unit, price, quantity, stock }] */
const cart = ref([])

const submitting = ref(false)
const errorMsg = ref('')

const pickerVisible = ref(false)
const pickerKeyword = ref('')
const pickerList = ref([])
const pickerLoading = ref(false)
let pickerTimer = null

const totalQuantity = computed(() =>
  cart.value.reduce((s, i) => s + Number(i.quantity || 0), 0)
)

const totalAmount = computed(() =>
  cart.value.reduce((s, i) => s + Number(i.price || 0) * Number(i.quantity || 0), 0)
)

const discount = computed(() => {
  const d = Number(discountAmount.value)
  return isFinite(d) && d > 0 ? d : 0
})

const payAmount = computed(() => Math.max(0, totalAmount.value - discount.value))

// ------------------------------------------------------------------
// 购物车
// ------------------------------------------------------------------

function addProduct(p) {
  const exist = cart.value.find((i) => i.productId === p.id)
  if (exist) {
    changeQty(exist, 1)
  } else {
    const stock = Number(p.stockQuantity || 0)
    cart.value.push({
      productId: p.id,
      name: p.name,
      unit: p.unit || '件',
      price: Number(p.salePrice || 0),
      quantity: stock > 0 ? 1 : 0,
      stock
    })
  }
  closePicker()
}

function removeItem(item) {
  cart.value = cart.value.filter((i) => i.productId !== item.productId)
}

function changeQty(item, delta) {
  const next = Number(item.quantity || 0) + delta
  if (next < 1) {
    return
  }
  // 前端先拦一道，避免白跑一次请求；真正的并发安全由后端的批次扣减保证
  if (item.stock > 0 && next > item.stock) {
    uni.showToast({ title: `库存仅 ${item.stock} ${item.unit}`, icon: 'none' })
    return
  }
  item.quantity = next
}

function onQtyInput(item, e) {
  const v = parseInt(e.detail.value, 10)
  item.quantity = isFinite(v) && v > 0 ? v : 1
}

function onPriceInput(item, e) {
  const v = Number(e.detail.value)
  item.price = isFinite(v) && v >= 0 ? v : 0
}

// ------------------------------------------------------------------
// 商品选择
// ------------------------------------------------------------------

async function loadPicker() {
  pickerLoading.value = true
  try {
    const params = { page: 1, size: 30 }
    if (pickerKeyword.value.trim()) {
      params.keyword = pickerKeyword.value.trim()
    }
    const res = await pageProducts(params)
    pickerList.value = (res && res.records) || []
  } catch (e) {
    pickerList.value = []
  } finally {
    pickerLoading.value = false
  }
}

function onPickerInput() {
  if (pickerTimer) {
    clearTimeout(pickerTimer)
  }
  pickerTimer = setTimeout(loadPicker, 350)
}

function openPicker() {
  pickerVisible.value = true
  if (!pickerList.value.length) {
    loadPicker()
  }
}

function closePicker() {
  pickerVisible.value = false
}

// ------------------------------------------------------------------
// 提交
// ------------------------------------------------------------------

async function submit() {
  if (submitting.value) {
    return
  }
  errorMsg.value = ''

  if (!cart.value.length) {
    errorMsg.value = '请先添加商品'
    return
  }
  const invalid = cart.value.find((i) => Number(i.quantity) < 1)
  if (invalid) {
    errorMsg.value = `【${invalid.name}】的数量必须大于 0`
    return
  }

  submitting.value = true
  try {
    const id = await createSale({
      customerName: customerName.value.trim() || '散客',
      orderDate,
      discountAmount: discount.value,
      remark: remark.value.trim(),
      items: cart.value.map((i) => ({
        productId: i.productId,
        quantity: Number(i.quantity),
        price: Number(i.price)
      }))
    })

    uni.showToast({ title: '开单成功', icon: 'success', duration: 1200 })
    // 用 redirectTo 而不是 navigateBack：开完单看详情更自然，
    // 返回时也不会退回已经提交过的表单
    setTimeout(() => {
      uni.redirectTo({ url: '/pages/sale/detail?id=' + id })
    }, 700)
  } catch (e) {
    // 4001 库存不足这类错误要留在页面上，让用户能对照着改数量
    errorMsg.value = (e && e.message) || '开单失败，请重试'
  } finally {
    submitting.value = false
  }
}

onLoad(async (options) => {
  const productId = options && options.productId
  if (!productId) {
    return
  }
  try {
    const p = await getProduct(productId)
    if (p) {
      addProduct(p)
    }
  } catch (e) {
    /* 从详情页带过来的商品没拉到，用户手动加即可 */
  }
})
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  padding: 24rpx 24rpx 0;
  box-sizing: border-box;
}

.card {
  margin-bottom: 24rpx;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20rpx;
}

.card-title {
  font-size: 30rpx;
  font-weight: 600;
}

.card-extra {
  font-size: 24rpx;
  color: $erp-text-muted;
}

/* ---------- 表单 ---------- */
.field {
  display: flex;
  align-items: center;
  padding: 22rpx 0;
  border-bottom: 2rpx solid $erp-border;

  &.no-border {
    border-bottom: none;
    padding-bottom: 4rpx;
  }
}

.label {
  width: 160rpx;
  font-size: 28rpx;
  color: $erp-text-sub;
  flex-shrink: 0;
}

.input {
  flex: 1;
  font-size: 29rpx;
  color: $erp-text;
  text-align: right;
}

.static-value {
  flex: 1;
  font-size: 29rpx;
  color: $erp-text;
  text-align: right;
}

.ph {
  color: #c2cad6;
  font-size: 27rpx;
}

/* ---------- 购物车 ---------- */
.cart-item {
  padding: 22rpx 0;

  & + .cart-item {
    border-top: 2rpx solid $erp-border;
  }
}

.cart-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.cart-name {
  font-size: 29rpx;
  font-weight: 600;
  color: $erp-text;
  max-width: 480rpx;
}

.cart-del {
  font-size: 24rpx;
  color: $erp-danger;
}

.cart-mid {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 14rpx;
}

.price-edit {
  display: flex;
  align-items: center;
  height: 60rpx;
  padding: 0 18rpx;
  border-radius: 12rpx;
  background: $erp-bg;
}

.price-symbol {
  font-size: 25rpx;
  color: $erp-text-sub;
  margin-right: 6rpx;
}

.price-input {
  width: 150rpx;
  font-size: 28rpx;
  color: $erp-text;
  font-weight: 600;
}

.cart-stock {
  font-size: 23rpx;
  color: $erp-text-muted;
}

.cart-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16rpx;
}

.stepper {
  display: flex;
  align-items: center;
  border: 2rpx solid $erp-border;
  border-radius: 12rpx;
  overflow: hidden;
}

.step-btn {
  width: 68rpx;
  height: 62rpx;
  line-height: 62rpx;
  text-align: center;
  font-size: 34rpx;
  color: $erp-text;
  background: $erp-bg;
}

.step-input {
  width: 110rpx;
  height: 62rpx;
  text-align: center;
  font-size: 28rpx;
  color: $erp-text;
}

.cart-subtotal {
  font-size: 32rpx;
  font-weight: 700;
  color: $erp-primary;
}

.cart-empty {
  padding: 60rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: $erp-text-muted;
}

.add-product {
  margin-top: 20rpx;
  height: 84rpx;
  line-height: 84rpx;
  text-align: center;
  border-radius: 16rpx;
  border: 2rpx dashed #c8d3e6;
  background: #fafbfe;
}

.add-text {
  font-size: 28rpx;
  color: $erp-primary;
  font-weight: 600;
}

/* ---------- 汇总 ---------- */
.kv {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 0;

  & + .kv {
    border-top: 2rpx solid $erp-border;
  }
}

.k {
  font-size: 27rpx;
  color: $erp-text-sub;

  &.strong {
    font-weight: 600;
    color: $erp-text;
  }
}

.v {
  font-size: 28rpx;
  color: $erp-text;

  &.pay {
    font-size: 38rpx;
    font-weight: 700;
    color: $erp-danger;
  }
}

.discount-edit {
  display: flex;
  align-items: center;
  height: 60rpx;
  padding: 0 18rpx;
  border-radius: 12rpx;
  background: $erp-bg;
}

.discount-input {
  width: 150rpx;
  font-size: 28rpx;
  text-align: right;
  color: $erp-text;
}

/* ---------- 错误提示 ---------- */
.error-banner {
  margin-bottom: 24rpx;
  padding: 22rpx 26rpx;
  border-radius: 16rpx;
  background: $erp-danger-soft;
}

.error-text {
  font-size: 26rpx;
  color: $erp-danger;
  line-height: 1.5;
}

.bottom-space {
  height: 180rpx;
}

/* ---------- 底部结算栏 ---------- */
.bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 32rpx;
  background: #ffffff;
  box-shadow: 0 -6rpx 24rpx rgba(31, 39, 51, 0.08);
}

.bar-left {
  display: flex;
  align-items: baseline;
}

.bar-label {
  font-size: 26rpx;
  color: $erp-text-sub;
  margin-right: 12rpx;
}

.bar-amount {
  font-size: 44rpx;
  font-weight: 700;
  color: $erp-danger;
}

.bar-btn {
  height: 88rpx;
  line-height: 88rpx;
  padding: 0 60rpx;
  border-radius: 44rpx;
  background: linear-gradient(135deg, #4b85ff, #2f6bff);
  color: #ffffff;
  font-size: 31rpx;
  font-weight: 600;
  box-shadow: 0 10rpx 24rpx rgba(47, 107, 255, 0.3);

  &.disabled {
    opacity: 0.6;
    box-shadow: none;
  }
}

/* ---------- 商品选择弹层 ---------- */
.mask {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  background: rgba(15, 22, 36, 0.45);
  z-index: 99;
  display: flex;
  align-items: flex-end;
}

.picker {
  width: 100%;
  height: 78vh;
  background: #ffffff;
  border-radius: 32rpx 32rpx 0 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.picker-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 30rpx 32rpx 20rpx;
}

.picker-title {
  font-size: 32rpx;
  font-weight: 600;
}

.picker-close {
  font-size: 28rpx;
  color: $erp-primary;
  font-weight: 600;
}

.picker-search {
  padding: 0 32rpx 20rpx;
}

.picker-input {
  height: 76rpx;
  padding: 0 28rpx;
  border-radius: 38rpx;
  background: $erp-bg;
  font-size: 28rpx;
  color: $erp-text;
}

.picker-list {
  flex: 1;
  min-height: 0;
  padding: 0 32rpx;
}

.picker-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 0;
  border-top: 2rpx solid $erp-border;
}

.picker-main {
  flex: 1;
  min-width: 0;
}

.picker-name {
  font-size: 29rpx;
  font-weight: 600;
  color: $erp-text;
  max-width: 420rpx;
}

.picker-meta {
  display: flex;
  align-items: center;
  margin-top: 8rpx;
}

.meta-text {
  font-size: 23rpx;
  color: $erp-text-muted;
}

.meta-dot {
  margin: 0 8rpx;
  color: #ccd3de;
}

.picker-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  margin-left: 16rpx;
}

.picker-price {
  font-size: 28rpx;
  font-weight: 600;
  color: $erp-text;
}

.picker-tag {
  margin-top: 6rpx;
  font-size: 21rpx;
  padding: 2rpx 12rpx;
  border-radius: 6rpx;

  &.ok {
    background: $erp-success-soft;
    color: $erp-success;
  }

  &.no {
    background: $erp-danger-soft;
    color: $erp-danger;
  }
}

.picker-tip {
  padding: 60rpx 0;
  text-align: center;
  font-size: 25rpx;
  color: $erp-text-muted;
}
</style>
