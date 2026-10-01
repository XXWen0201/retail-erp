<template>
  <div class="page">
    <div class="page-header">
      <div class="page-title">
        库存流水
        <span class="sub">每一次库存变动都有据可查，是盘点差异追溯的唯一凭据</span>
      </div>
      <div class="page-actions">
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="card">
      <div class="search-bar">
        <el-select
          v-model="query.productId"
          placeholder="全部商品"
          clearable
          filterable
          style="width: 220px"
        >
          <el-option
            v-for="p in products"
            :key="p.id"
            :label="`${p.name}（${p.code}）`"
            :value="p.id"
          />
        </el-select>
        <el-select v-model="query.bizType" placeholder="全部业务类型" clearable style="width: 160px">
          <el-option v-for="(label, k) in BIZ_TYPE" :key="k" :label="label" :value="k" />
        </el-select>
        <el-input
          v-model="query.bizNo"
          placeholder="关联单号"
          clearable
          style="width: 180px"
          @keyup.enter="search"
        />
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          style="width: 250px"
        />
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button :icon="RefreshLeft" @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" stripe border>
        <el-table-column prop="createTime" label="发生时间" width="155" />
        <el-table-column prop="productName" label="商品" min-width="180" show-overflow-tooltip />
        <el-table-column prop="batchNo" label="批次号" width="150">
          <template #default="{ row }">{{ dash(row.batchNo) }}</template>
        </el-table-column>
        <el-table-column label="业务类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="bizTag(row.bizType)" size="small" effect="light">
              {{ row.bizTypeLabel || BIZ_TYPE[row.bizType] || row.bizType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="变动数量" width="110" align="right">
          <template #default="{ row }">
            <span :class="row.changeQuantity >= 0 ? 'text-ok' : 'text-danger'" class="money">
              {{ row.changeQuantity > 0 ? '+' : '' }}{{ num(row.changeQuantity) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="变动前" width="90" align="right">
          <template #default="{ row }">
            <span class="text-muted">{{ num(row.beforeQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="变动后" width="90" align="right">
          <template #default="{ row }">
            <span class="money">{{ num(row.afterQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="单位成本" width="100" align="right">
          <template #default="{ row }">
            <span class="text-muted">{{ num(row.unitCost, 4) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="bizNo" label="关联单号" width="165" />
        <el-table-column prop="operatorName" label="操作人" width="90" align="center" />
        <el-table-column prop="remark" label="备注" min-width="130" show-overflow-tooltip />
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[20, 50, 100]"
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
import { onMounted, reactive, ref, watch } from 'vue'
import { Refresh, RefreshLeft, Search } from '@element-plus/icons-vue'
import { pageRecords } from '@/api/stock'
import { pageProducts } from '@/api/product'
import { BIZ_TYPE, dash, num } from '@/utils/format'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const products = ref([])
const dateRange = ref([])

const query = reactive({
  productId: null,
  bizType: null,
  bizNo: '',
  page: 1,
  size: 20
})

// 入库类为正向（绿不用，中国习惯涨为红 —— 这里用「增加=绿、减少=红」的库存语义，
// 因为库存变动不是价格涨跌，用颜色区分增减方向更直观）
const IN_TYPES = ['PURCHASE_IN', 'SALE_RETURN', 'CHECK_PROFIT', 'CHECK_ADJUST']
function bizTag(type) {
  if (IN_TYPES.includes(type)) return 'success'
  if (type === 'SALE_OUT') return 'primary'
  if (type === 'CHECK_LOSS') return 'danger'
  return 'warning'
}

watch(dateRange, (val) => {
  query.startDate = val && val[0] ? val[0] : undefined
  query.endDate = val && val[1] ? val[1] : undefined
})

async function load() {
  loading.value = true
  try {
    const res = await pageRecords(query)
    rows.value = (res && res.records) || []
    total.value = (res && res.total) || 0
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  load()
}

function reset() {
  dateRange.value = []
  Object.assign(query, {
    productId: null,
    bizType: null,
    bizNo: '',
    startDate: undefined,
    endDate: undefined,
    page: 1,
    size: 20
  })
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
