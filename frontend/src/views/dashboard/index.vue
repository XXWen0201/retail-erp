<template>
  <div class="page" v-loading="loading">
    <div class="page-header">
      <div class="page-title">
        工作台
        <span class="sub">{{ todayText }} · {{ userStore.displayName }}，欢迎回来</span>
      </div>
      <div class="page-actions">
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="$router.push('/sale/create')">
          开销售单
        </el-button>
      </div>
    </div>

    <!-- 经营概览 -->
    <div class="stat-row">
      <div class="stat-card">
        <div class="label">今日销售额</div>
        <div class="value">{{ money(data.todaySalesAmount) }}<small>元</small></div>
        <div class="foot">共 {{ num(data.todaySalesCount) }} 单</div>
      </div>
      <div class="stat-card">
        <div class="label">今日毛利</div>
        <div class="value text-ok">{{ money(data.todayGrossProfit) }}<small>元</small></div>
        <div class="foot">
          毛利率 {{ todayMargin }}
        </div>
      </div>
      <div class="stat-card">
        <div class="label">本月销售额</div>
        <div class="value">{{ money(data.monthSalesAmount) }}<small>元</small></div>
        <div class="foot">本月毛利 {{ money(data.monthGrossProfit) }} 元</div>
      </div>
      <div class="stat-card">
        <div class="label">库存成本总额</div>
        <div class="value">{{ money(data.totalStockValue) }}<small>元</small></div>
        <div class="foot">按移动加权平均成本计算</div>
      </div>
    </div>

    <!-- 待办事项：把"需要人动手"的两件事前置 -->
    <div class="todo-row">
      <div class="todo" :class="{ active: data.pendingPurchaseCount > 0 }">
        <div class="todo-icon"><el-icon><ShoppingCart /></el-icon></div>
        <div class="todo-body">
          <div class="todo-title">待入库采购单</div>
          <div class="todo-num">{{ num(data.pendingPurchaseCount) }} 张</div>
        </div>
        <el-button link type="primary" @click="$router.push('/purchase')">去处理</el-button>
      </div>

      <div class="todo" :class="{ active: data.unhandledAlertCount > 0 }">
        <div class="todo-icon warn"><el-icon><BellFilled /></el-icon></div>
        <div class="todo-body">
          <div class="todo-title">未处理库存预警</div>
          <div class="todo-num">{{ num(data.unhandledAlertCount) }} 条</div>
        </div>
        <el-button link type="primary" @click="$router.push('/alert')">去查看</el-button>
      </div>

      <div class="todo">
        <div class="todo-icon ai"><el-icon><MagicStick /></el-icon></div>
        <div class="todo-body">
          <div class="todo-title">AI 补货建议</div>
          <div class="todo-num">按销量自动测算</div>
        </div>
        <el-button link type="primary" @click="$router.push('/ai')">去看看</el-button>
      </div>
    </div>

    <!-- 图表区 -->
    <el-row :gutter="14">
      <el-col :xs="24" :lg="16">
        <div class="card">
          <div class="card-head">
            <span>近 30 天销售趋势</span>
            <span class="text-muted">金额 / 销量</span>
          </div>
          <div ref="trendRef" class="chart"></div>
        </div>
      </el-col>
      <el-col :xs="24" :lg="8">
        <div class="card">
          <div class="card-head">
            <span>分类库存金额占比</span>
          </div>
          <div ref="pieRef" class="chart"></div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="14" style="margin-top: 14px">
      <el-col :span="24">
        <div class="card">
          <div class="card-head">
            <span>分类库存明细</span>
          </div>
          <el-table :data="data.categoryStock || []" size="small" stripe>
            <el-table-column prop="categoryName" label="商品分类" min-width="160" />
            <el-table-column label="库存数量" width="140" align="right">
              <template #default="{ row }">{{ num(row.quantity) }}</template>
            </el-table-column>
            <el-table-column label="库存金额（元）" width="160" align="right">
              <template #default="{ row }">
                <span class="money">{{ money(row.costValue) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="金额占比" min-width="200">
              <template #default="{ row }">
                <el-progress
                  :percentage="percentOf(row.costValue)"
                  :stroke-width="12"
                  :show-text="false"
                />
                <span class="text-muted" style="font-size: 12px">
                  {{ percentOf(row.costValue).toFixed(1) }}%
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
import { computed, onMounted, ref } from 'vue'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { dashboard } from '@/api/report'
import { useUserStore } from '@/stores/user'
import { money, num, today } from '@/utils/format'
import { useChart, lineOption, pieOption, PALETTE } from '@/composables/useChart'

const userStore = useUserStore()
const loading = ref(false)
const data = ref({})

const { chartRef: trendRef, render: renderTrend } = useChart()
const { chartRef: pieRef, render: renderPie } = useChart()

const todayText = today().replace(/-/g, '/')

const todayMargin = computed(() => {
  const amount = Number(data.value.todaySalesAmount || 0)
  const profit = Number(data.value.todayGrossProfit || 0)
  if (!amount) return '0.00%'
  return ((profit / amount) * 100).toFixed(2) + '%'
})

const totalCategoryAmount = computed(() =>
  (data.value.categoryStock || []).reduce((s, r) => s + Number(r.costValue || 0), 0)
)

function percentOf(amount) {
  if (!totalCategoryAmount.value) return 0
  return (Number(amount || 0) / totalCategoryAmount.value) * 100
}

async function load() {
  loading.value = true
  try {
    data.value = (await dashboard()) || {}
    drawTrend()
    drawPie()
  } finally {
    loading.value = false
  }
}

function drawTrend() {
  const trend = data.value.salesTrend || []
  renderTrend(
    lineOption({
      xData: trend.map((t) => String(t.date).slice(5)),
      series: [
        {
          name: '销售额（元）',
          type: 'line',
          smooth: true,
          symbol: 'none',
          areaStyle: { opacity: 0.12 },
          itemStyle: { color: PALETTE[0] },
          data: trend.map((t) => Number(t.amount || 0))
        },
        {
          name: '销量',
          type: 'bar',
          yAxisIndex: 0,
          barWidth: '42%',
          itemStyle: { color: 'rgba(103,194,58,0.35)' },
          data: trend.map((t) => Number(t.quantity || 0))
        }
      ]
    })
  )
}

function drawPie() {
  const rows = data.value.categoryStock || []
  renderPie(
    pieOption({
      name: '库存金额',
      data: rows.map((r, i) => ({
        name: r.categoryName || '未分类',
        value: Number(r.costValue || 0),
        itemStyle: { color: PALETTE[i % PALETTE.length] }
      }))
    })
  )
}

onMounted(load)
</script>

<style scoped>
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 15px;
  font-weight: 600;
  color: #1f2d3d;
  margin-bottom: 12px;
}

.todo-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 14px;
  margin-bottom: 14px;
}

.todo {
  background: #fff;
  border-radius: var(--app-card-radius);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  padding: 14px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-left: 3px solid #dcdfe6;
}

.todo.active {
  border-left-color: #e6a23c;
}

.todo-icon {
  width: 38px;
  height: 38px;
  border-radius: 9px;
  background: #ecf5ff;
  color: #409eff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}

.todo-icon.warn {
  background: #fdf6ec;
  color: #e6a23c;
}

.todo-icon.ai {
  background: #f0f9eb;
  color: #67c23a;
}

.todo-body {
  flex: 1;
  min-width: 0;
}

.todo-title {
  font-size: 13px;
  color: #909399;
}

.todo-num {
  font-size: 18px;
  font-weight: 700;
  color: #1f2d3d;
  line-height: 1.5;
}
</style>
