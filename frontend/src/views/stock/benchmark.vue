<template>
  <div class="page">
    <div class="page-header">
      <div class="page-title">
        并发扣减压测
        <span class="sub">同一件商品，两种扣减方案，谁更稳更快一测便知</span>
      </div>
    </div>

    <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 14px">
      <template #title>
        压测会真实扣减库存，并写入 bizNo=BENCHMARK 的库存流水（计入统计报表）。
        建议选一个库存充足的商品，测完后可到「库存流水」里核对。
      </template>
    </el-alert>

    <el-row :gutter="14">
      <el-col :xs="24" :lg="10">
        <div class="card">
          <div class="card-head">压测参数</div>
          <el-form :model="form" label-width="120px">
            <el-form-item label="压测商品" required>
              <el-select
                v-model="form.productId"
                placeholder="请选择商品"
                filterable
                style="width: 100%"
              >
                <el-option
                  v-for="p in products"
                  :key="p.id"
                  :label="`${p.name}（库存 ${p.stockQuantity}）`"
                  :value="p.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="并发线程数">
              <el-input-number v-model="form.threads" :min="1" :max="100" style="width: 100%" />
            </el-form-item>
            <el-form-item label="每线程次数">
              <el-input-number
                v-model="form.timesPerThread"
                :min="1"
                :max="500"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item label="每次扣减量">
              <el-input-number
                v-model="form.quantityPerRequest"
                :min="1"
                :max="50"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item label="扣减方案">
              <el-radio-group v-model="form.mode">
                <el-radio-button value="DB">数据库乐观锁</el-radio-button>
                <el-radio-button value="REDIS">Redis + Lua</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                :loading="running"
                :icon="VideoPlay"
                style="width: 100%"
                @click="run"
              >
                {{ running ? '压测进行中…' : '开始压测' }}
              </el-button>
            </el-form-item>
          </el-form>

          <div class="plan-tip">
            <p><b>数据库乐观锁</b>：UPDATE ... WHERE version = ?，靠影响行数判断是否被并发改动，冲突则重试（最多 3 次）。</p>
            <p><b>Redis + Lua</b>：先在 Redis 里用 Lua 脚本原子预扣减，再把结果异步落库，把竞争挡在数据库之前。</p>
          </div>
        </div>
      </el-col>

      <el-col :xs="24" :lg="14">
        <div class="card">
          <div class="card-head">
            压测结果
            <el-tag v-if="result" :type="result.consistent ? 'success' : 'danger'" size="small">
              {{ result.consistent ? '库存一致 ✓' : '出现超卖 ✗' }}
            </el-tag>
          </div>

          <el-empty v-if="!result" description="尚未执行压测" :image-size="90" />

          <template v-else>
            <div class="stat-row" style="margin-bottom: 0">
              <div class="stat-card">
                <div class="label">总请求数</div>
                <div class="value">{{ num(result.totalRequests) }}</div>
              </div>
              <div class="stat-card">
                <div class="label">成功 / 失败</div>
                <div class="value">
                  <span class="text-ok">{{ num(result.successCount) }}</span>
                  <span class="text-muted"> / </span>
                  <span class="text-danger">{{ num(result.failCount) }}</span>
                </div>
              </div>
              <div class="stat-card">
                <div class="label">总耗时</div>
                <div class="value">{{ num(result.costMs) }}<small>ms</small></div>
              </div>
              <div class="stat-card">
                <div class="label">吞吐量 QPS</div>
                <div class="value">{{ num(result.qps, 1) }}</div>
              </div>
            </div>

            <el-descriptions :column="2" border size="small" style="margin-top: 14px">
              <el-descriptions-item label="扣减方案">
                {{ result.mode === 'REDIS' ? 'Redis + Lua' : '数据库乐观锁' }}
              </el-descriptions-item>
              <el-descriptions-item label="并发配置">
                {{ result.threads }} 线程 × {{ result.timesPerThread }} 次
              </el-descriptions-item>
              <el-descriptions-item label="压测后库存">
                {{ num(result.finalStock) }}
              </el-descriptions-item>
              <el-descriptions-item label="一致性结论">
                <span :class="result.consistent ? 'text-ok' : 'text-danger'">
                  {{ result.consistent ? '一致，未超卖' : '不一致，存在超卖' }}
                </span>
              </el-descriptions-item>
              <el-descriptions-item label="说明" :span="2">
                {{ dash(result.message) }}
              </el-descriptions-item>
            </el-descriptions>

            <el-alert
              type="info"
              :closable="false"
              show-icon
              style="margin-top: 12px"
              title="怎么读这组数据"
            >
              <template #default>
                <div class="read-tip">
                  成功次数 × 每次扣减量 = 应当扣掉的库存。如果实际库存减少量与它相等，
                  说明并发下没有丢更新，也就是「一致」。把两种方案各跑一遍，就能同时比出
                  <b>性能差异</b>和<b>正确性差异</b>。
                </div>
              </template>
            </el-alert>
          </template>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { VideoPlay } from '@element-plus/icons-vue'
import { runBenchmark } from '@/api/stock'
import { pageProducts } from '@/api/product'
import { dash, num } from '@/utils/format'

const running = ref(false)
const result = ref(null)
const products = ref([])

const form = reactive({
  productId: null,
  threads: 20,
  timesPerThread: 20,
  quantityPerRequest: 1,
  mode: 'DB'
})

async function run() {
  if (!form.productId) {
    ElMessage.warning('请先选择压测商品')
    return
  }
  const total = form.threads * form.timesPerThread
  await ElMessageBox.confirm(
    `即将对所选商品发起 ${form.threads} 线程 × ${form.timesPerThread} 次 = ${total} 次并发扣减。\n\n` +
      '这会真实修改库存并写入流水，请确认商品库存充足。',
    '确认压测',
    { type: 'warning', confirmButtonText: '开始', cancelButtonText: '取消' }
  )

  running.value = true
  result.value = null
  try {
    result.value = await runBenchmark(form)
    ElMessage.success(
      result.value && result.value.consistent ? '压测完成：库存一致' : '压测完成：请查看一致性结论'
    )
  } finally {
    running.value = false
  }
}

onMounted(async () => {
  try {
    const p = await pageProducts({ page: 1, size: 200, status: 1 })
    products.value = (p && p.records) || []
    // 默认挑一个库存最多的商品，减少压测把库存打空的概率
    if (products.value.length) {
      const best = products.value.reduce(
        (a, b) => (Number(b.stockQuantity) > Number(a.stockQuantity) ? b : a),
        products.value[0]
      )
      form.productId = best.id
    }
  } catch {
    /* 拦截器已提示 */
  }
})
</script>

<style scoped>
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 15px;
  font-weight: 600;
  color: #1f2d3d;
  margin-bottom: 14px;
}

.plan-tip {
  background: #f7f8fa;
  border-radius: 6px;
  padding: 12px 14px;
  font-size: 12.5px;
  color: #606266;
  line-height: 1.8;
}

.plan-tip p {
  margin: 0 0 6px;
}

.plan-tip p:last-child {
  margin-bottom: 0;
}

.read-tip {
  line-height: 1.8;
  font-size: 12.5px;
}
</style>
