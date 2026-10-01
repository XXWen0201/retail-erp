<template>
  <div class="page" v-loading="loading">
    <div class="page-header">
      <div class="page-title">
        库存统计
        <span class="sub">库存成本按移动加权平均法计价</span>
      </div>
      <div class="page-actions">
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="stat-row">
      <div class="stat-card">
        <div class="label">在库 SKU 数</div>
        <div class="value">{{ num(data.skuCount) }}<small>种</small></div>
      </div>
      <div class="stat-card">
        <div class="label">库存总数量</div>
        <div class="value">{{ num(data.totalQuantity) }}</div>
      </div>
      <div class="stat-card">
        <div class="label">库存成本总额</div>
        <div class="value">{{ money(data.totalCostValue) }}<small>元</small></div>
      </div>
      <div class="stat-card">
        <div class="label">低于下限商品</div>
        <div class="value" :class="(data.lowStockList || []).length ? 'text-warn' : ''">
          {{ (data.lowStockList || []).length }}<small>种</small>
        </div>
        <div class="foot">建议尽快补货</div>
      </div>
    </div>

    <el-row :gutter="14">
      <el-col :xs="24" :lg="10">
        <div class="card">
          <div class="card-head">各分类库存金额占比</div>
          <div ref="pieRef" class="chart"></div>
        </div>
      </el-col>
      <el-col :xs="24" :lg="14">
        <div class="card">
          <div class="card-head">分类库存明细</div>
          <el-table :data="data.byCategory || []" size="small" stripe max-height="320">
            <el-table-column prop="categoryName" label="商品分类" min-width="140" />
            <el-table-column label="SKU 数" width="85" align="right">
              <template #default="{ row }">{{ num(row.skuCount) }}</template>
            </el-table-column>
            <el-table-column label="库存数量" width="100" align="right">
              <template #default="{ row }">{{ num(row.quantity) }}</template>
            </el-table-column>
            <el-table-column label="成本金额" width="120" align="right">
              <template #default="{ row }">
                <span class="money">{{ money(row.costValue) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="占比" min-width="120">
              <template #default="{ row }">
                <el-progress :percentage="shareOf(row.costValue)" :stroke-width="10" :show-text="false" />
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="14" style="margin-top: 14px">
      <el-col :xs="24" :lg="12">
        <div class="card">
          <div class="card-head">
            低于库存下限的商品
            <el-tag type="warning" size="small" effect="plain">
              {{ (data.lowStockList || []).length }} 种
            </el-tag>
          </div>
          <el-table :data="data.lowStockList || []" size="small" stripe max-height="300">
            <el-table-column prop="productName" label="商品" min-width="170" show-overflow-tooltip />
            <el-table-column label="当前库存" width="100" align="right">
              <template #default="{ row }">
                <span class="text-danger money">{{ num(row.quantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="库存下限" width="100" align="right">
              <template #default="{ row }">
                <span class="text-muted">{{ num(row.stockLower) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="缺口" width="90" align="right">
              <template #default="{ row }">
                <span class="text-warn money">
                  {{ num(Math.max(Number(row.stockLower) - Number(row.quantity), 0)) }}
                </span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>

      <el-col :xs="24" :lg="12">
        <div class="card">
          <div class="card-head">
            临期批次（30 天内到期）
            <el-tag type="warning" size="small" effect="plain">
              {{ (data.expiringList || []).length }} 个
            </el-tag>
          </div>
          <el-table :data="data.expiringList || []" size="small" stripe max-height="300">
            <el-table-column prop="productName" label="商品" min-width="160" show-overflow-tooltip />
            <el-table-column prop="batchNo" label="批次号" width="145" />
            <el-table-column label="剩余库存" width="95" align="right">
              <template #default="{ row }">{{ num(row.stockQuantity) }}</template>
            </el-table-column>
            <el-table-column prop="expireDate" label="到期日" width="105" align="center" />
            <el-table-column label="剩余天数" width="100" align="center">
              <template #default="{ row }">
                <span :class="row.daysToExpire < 0 ? 'text-danger' : 'text-warn'">
                  {{ row.daysToExpire }} 天
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
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { stockReport } from '@/api/report'
import { money, num } from '@/utils/format'
import { useChart, pieOption, PALETTE } from '@/composables/useChart'

const loading = ref(false)
const data = ref({})

const { chartRef: pieRef, render: renderPie } = useChart()

function shareOf(value) {
  const total = Number(data.value.totalCostValue || 0)
  if (!total) return 0
  return Math.min((Number(value || 0) / total) * 100, 100)
}

async function load() {
  loading.value = true
  try {
    data.value = (await stockReport()) || {}
    renderPie(
      pieOption({
        name: '库存成本',
        data: (data.value.byCategory || []).map((r, i) => ({
          name: r.categoryName || '未分类',
          value: Number(r.costValue || 0),
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
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 15px;
  font-weight: 600;
  color: #1f2d3d;
  margin-bottom: 12px;
}
</style>
