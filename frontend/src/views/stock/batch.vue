<template>
  <div class="page">
    <div class="page-header">
      <div class="page-title">
        批次与保质期
        <span class="sub">入库按批次记账，出库按先到期先出（FEFO）</span>
      </div>
      <div class="page-actions">
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="stat-row">
      <div class="stat-card">
        <div class="label">批次总数</div>
        <div class="value">{{ total }}<small>个</small></div>
        <div class="foot">当前筛选条件下</div>
      </div>
      <div class="stat-card">
        <div class="label">临期批次（30 天内）</div>
        <div class="value text-warn">{{ summary.nearExpiry }}</div>
        <div class="foot">需要优先促销或退换</div>
      </div>
      <div class="stat-card">
        <div class="label">已过期批次</div>
        <div class="value text-danger">{{ summary.expired }}</div>
        <div class="foot">必须下架，不可再销售</div>
      </div>
    </div>

    <div class="card">
      <div class="search-bar">
        <el-select
          v-model="query.productId"
          placeholder="全部商品"
          clearable
          filterable
          style="width: 240px"
        >
          <el-option
            v-for="p in products"
            :key="p.id"
            :label="`${p.name}（${p.code}）`"
            :value="p.id"
          />
        </el-select>
        <el-checkbox v-model="query.expiringSoon" border>仅看临期</el-checkbox>
        <el-checkbox v-model="query.expired" border>仅看已过期</el-checkbox>
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button :icon="RefreshLeft" @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" stripe border>
        <el-table-column prop="batchNo" label="批次号" width="160" />
        <el-table-column prop="productName" label="商品" min-width="190" show-overflow-tooltip />
        <el-table-column prop="productionDate" label="生产日期" width="115" align="center">
          <template #default="{ row }">{{ dash(row.productionDate) }}</template>
        </el-table-column>
        <el-table-column prop="expireDate" label="到期日期" width="115" align="center">
          <template #default="{ row }">{{ dash(row.expireDate) }}</template>
        </el-table-column>
        <el-table-column label="初始数量" width="100" align="right">
          <template #default="{ row }">{{ num(row.initQuantity) }}</template>
        </el-table-column>
        <el-table-column label="已出库" width="95" align="right">
          <template #default="{ row }">
            <span class="text-muted">{{ num(row.outQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="批次剩余" width="100" align="right">
          <template #default="{ row }">
            <span class="money">{{ num(row.stockQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="成本价" width="100" align="right">
          <template #default="{ row }">
            <span class="text-muted">{{ num(row.costPrice, 4) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="剩余天数" width="110" align="center">
          <template #default="{ row }">
            <span v-if="row.daysToExpire === null || row.daysToExpire === undefined">-</span>
            <span v-else :class="dayClass(row.daysToExpire)">{{ row.daysToExpire }} 天</span>
          </template>
        </el-table-column>
        <el-table-column label="效期状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="expireTag(row.expireStatus)" size="small">
              {{ expireLabel(row.expireStatus) }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="load"
          @current-change="load"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Refresh, RefreshLeft, Search } from '@element-plus/icons-vue'
import { pageBatches } from '@/api/stock'
import { pageProducts } from '@/api/product'
import { EXPIRE_STATUS, dash, num } from '@/utils/format'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const products = ref([])
const summary = reactive({ nearExpiry: 0, expired: 0 })

const query = reactive({
  productId: null,
  expiringSoon: false,
  expired: false,
  page: 1,
  size: 20
})

const expireTag = (s) => (EXPIRE_STATUS[s] || {}).type || 'info'
const expireLabel = (s) => (EXPIRE_STATUS[s] || {}).label || '正常'

function dayClass(days) {
  if (days < 0) return 'text-danger'
  if (days <= 30) return 'text-warn'
  return 'text-ok'
}

async function load() {
  loading.value = true
  try {
    const res = await pageBatches(query)
    rows.value = (res && res.records) || []
    total.value = (res && res.total) || 0
    summary.nearExpiry = rows.value.filter((r) => r.expireStatus === 'NEAR_EXPIRY').length
    summary.expired = rows.value.filter((r) => r.expireStatus === 'EXPIRED').length
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  load()
}

function reset() {
  Object.assign(query, { productId: null, expiringSoon: false, expired: false, page: 1, size: 20 })
  load()
}

onMounted(async () => {
  try {
    const p = await pageProducts({ page: 1, size: 200 })
    products.value = (p && p.records) || []
  } catch {
    /* 拦截器已提示 */
  }
  load()
})
</script>
