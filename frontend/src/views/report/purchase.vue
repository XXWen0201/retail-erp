<template>
  <div class="page" v-loading="loading">
    <div class="page-header">
      <div class="page-title">
        采购统计
        <span class="sub">{{ query.startDate }} ~ {{ query.endDate }}</span>
      </div>
      <div class="page-actions">
        <el-radio-group v-model="query.groupBy" @change="load">
          <el-radio-button value="day">按日</el-radio-button>
          <el-radio-button value="month">按月</el-radio-button>
        </el-radio-group>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          style="width: 250px"
        />
        <el-button type="primary" :icon="Search" @click="load">查询</el-button>
      </div>
    </div>

    <div class="stat-row">
      <div class="stat-card">
        <div class="label">采购总额</div>
        <div class="value">{{ money(data.totalAmount) }}<small>元</small></div>
      </div>
      <div class="stat-card">
        <div class="label">采购数量</div>
        <div class="value">{{ num(data.totalQuantity) }}</div>
      </div>
      <div class="stat-card">
        <div class="label">采购单数</div>
        <div class="value">{{ num(data.orderCount) }}<small>单</small></div>
      </div>
      <div class="stat-card">
        <div class="label">单均金额</div>
        <div class="value">{{ money(avgOrderAmount) }}<small>元</small></div>
      </div>
    </div>

    <div class="card">
      <div class="card-head">采购趋势</div>
      <div ref="trendRef" class="chart"></div>
    </div>

    <el-row :gutter="14" style="margin-top: 14px">
      <el-col :xs="24" :lg="11">
        <div class="card">
          <div class="card-head">供应商采购金额占比</div>
          <div ref="pieRef" class="chart"></div>
        </div>
      </el-col>
      <el-col :xs="24" :lg="13">
        <div class="card">
          <div class="card-head">供应商采购明细</div>
          <el-table :data="data.bySupplier || []" size="small" stripe max-height="320">
            <el-table-column prop="supplierName" label="供应商" min-width="200" show-overflow-tooltip />
            <el-table-column label="采购数量" width="105" align="right">
              <template #default="{ row }">{{ num(row.quantity) }}</template>
            </el-table-column>
            <el-table-column label="采购金额" width="125" align="right">
              <template #default="{ row }">
                <span class="money">{{ money(row.amount) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="占比" min-width="130">
              <template #default="{ row }">
                <el-progress :percentage="shareOf(row.amount)" :stroke-width="10" :show-text="false" />
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { purchaseReport } from '@/api/report'
import { daysAgo, money, num, today } from '@/utils/format'
import { useChart, barOption, pieOption, PALETTE } from '@/composables/useChart'

const loading = ref(false)
const data = ref({})
const dateRange = ref([daysAgo(29), today()])

const query = reactive({
  startDate: daysAgo(29),
  endDate: today(),
  groupBy: 'day'
})

const { chartRef: trendRef, render: renderTrend } = useChart()
const { chartRef: pieRef, render: renderPie } = useChart()

const avgOrderAmount = computed(() => {
  const amount = Number(data.value.totalAmount || 0)
  const count = Number(data.value.orderCount || 0)
  return count ? amount / count : 0
})

function shareOf(amount) {
  const total = Number(data.value.totalAmount || 0)
  if (!total) return 0
  return Math.min((Number(amount || 0) / total) * 100, 100)
}

watch(dateRange, (val) => {
  query.startDate = val && val[0] ? val[0] : undefined
  query.endDate = val && val[1] ? val[1] : undefined
})

async function load() {
  loading.value = true
  try {
    data.value = (await purchaseReport(query)) || {}
    const trend = data.value.trend || []
    const labels = trend.map((t) =>
      query.groupBy === 'month' ? String(t.date).slice(0, 7) : String(t.date).slice(5)
    )

    renderTrend(
      barOption({
        xData: labels,
        series: [
          {
            name: '采购金额（元）',
            type: 'bar',
            barMaxWidth: 26,
            itemStyle: { color: PALETTE[2], borderRadius: [3, 3, 0, 0] },
            data: trend.map((t) => Number(t.amount || 0))
          },
          {
            name: '采购数量',
            type: 'line',
            smooth: true,
            symbol: 'none',
            itemStyle: { color: PALETTE[0] },
            data: trend.map((t) => Number(t.quantity || 0))
          }
        ]
      })
    )

    renderPie(
      pieOption({
        name: '采购金额',
        data: (data.value.bySupplier || []).map((r, i) => ({
          name: r.supplierName || '未知供应商',
          value: Number(r.amount || 0),
          itemStyle: { color: PALETTE[i % PALETTE.length] }
        }))
      })
    )
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.card-head {
  font-size: 15px;
  font-weight: 600;
  color: #1f2d3d;
  margin-bottom: 12px;
}
</style>
