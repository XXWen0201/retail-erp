<template>
  <div class="page" v-loading="loading">
    <div class="page-header">
      <div class="page-title">
        毛利统计
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
        <div class="label">销售收入</div>
        <div class="value">{{ money(data.totalAmount) }}<small>元</small></div>
      </div>
      <div class="stat-card">
        <div class="label">销售成本</div>
        <div class="value text-muted">{{ money(data.totalCost) }}<small>元</small></div>
        <div class="foot">按出库批次的移动加权成本累计</div>
      </div>
      <div class="stat-card">
        <div class="label">毛利额</div>
        <div class="value text-ok">{{ money(data.totalProfit) }}<small>元</small></div>
      </div>
      <div class="stat-card">
        <div class="label">毛利率</div>
        <div class="value">{{ percent(data.grossMargin) }}</div>
        <div class="foot">毛利额 ÷ 销售收入</div>
      </div>
    </div>

    <div class="card">
      <div class="card-head">收入 / 成本 / 毛利趋势</div>
      <div ref="trendRef" class="chart"></div>
    </div>

    <el-row :gutter="14" style="margin-top: 14px">
      <el-col :xs="24" :lg="10">
        <div class="card">
          <div class="card-head">分类毛利额占比</div>
          <div ref="pieRef" class="chart"></div>
        </div>
      </el-col>
      <el-col :xs="24" :lg="14">
        <div class="card">
          <div class="card-head">分类毛利明细</div>
          <el-table :data="data.byCategory || []" size="small" stripe max-height="330">
            <el-table-column prop="categoryName" label="商品分类" min-width="130" />
            <el-table-column label="销售收入" width="115" align="right">
              <template #default="{ row }">{{ money(row.amount) }}</template>
            </el-table-column>
            <el-table-column label="销售成本" width="115" align="right">
              <template #default="{ row }">
                <span class="text-muted">{{ money(row.cost) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="毛利额" width="115" align="right">
              <template #default="{ row }">
                <span class="money text-ok">{{ money(row.profit) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="毛利率" width="100" align="right">
              <template #default="{ row }">
                <span :class="Number(row.margin) >= 30 ? 'text-ok' : 'text-warn'">
                  {{ percent(row.margin) }}
                </span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { profitReport } from '@/api/report'
import { daysAgo, money, percent, today } from '@/utils/format'
import { useChart, lineOption, pieOption, PALETTE } from '@/composables/useChart'

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

watch(dateRange, (val) => {
  query.startDate = val && val[0] ? val[0] : undefined
  query.endDate = val && val[1] ? val[1] : undefined
})

async function load() {
  loading.value = true
  try {
    data.value = (await profitReport(query)) || {}
    const trend = data.value.trend || []
    const labels = trend.map((t) =>
      query.groupBy === 'month' ? String(t.date).slice(0, 7) : String(t.date).slice(5)
    )

    renderTrend(
      lineOption({
        xData: labels,
        series: [
          {
            name: '销售收入',
            type: 'line',
            smooth: true,
            symbol: 'none',
            itemStyle: { color: PALETTE[0] },
            data: trend.map((t) => Number(t.amount || 0))
          },
          {
            name: '销售成本',
            type: 'line',
            smooth: true,
            symbol: 'none',
            itemStyle: { color: PALETTE[4] },
            data: trend.map((t) => Number(t.cost || 0))
          },
          {
            name: '毛利额',
            type: 'line',
            smooth: true,
            symbol: 'none',
            areaStyle: { opacity: 0.15 },
            itemStyle: { color: PALETTE[1] },
            data: trend.map((t) => Number(t.profit || 0))
          }
        ]
      })
    )

    renderPie(
      pieOption({
        name: '毛利额',
        // 负毛利的分类不画进饼图，否则图例会出现无意义的负值
        data: (data.value.byCategory || [])
          .filter((r) => Number(r.profit || 0) > 0)
          .map((r, i) => ({
            name: r.categoryName || '未分类',
            value: Number(r.profit || 0),
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
