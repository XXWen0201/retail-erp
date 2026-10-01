<template>
  <div class="page">
    <div class="page-header">
      <div class="page-title">
        新建销售单
        <span class="sub">保存即出库，按先到期先出（FEFO）扣减批次</span>
      </div>
      <div class="page-actions">
        <el-button :icon="Back" @click="$router.back()">返回列表</el-button>
        <el-button type="primary" :loading="saving" :icon="Check" @click="onSubmit">
          开单并出库
        </el-button>
      </div>
    </div>

    <div class="card">
      <el-form :model="form" label-width="90px" :disabled="saving">
        <el-row :gutter="16">
          <el-col :xs="24" :md="8">
            <el-form-item label="客户名称">
              <el-input v-model="form.customerName" placeholder="默认散客" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="7">
            <el-form-item label="销售日期" required>
              <el-date-picker
                v-model="form.orderDate"
                type="date"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="5">
            <el-form-item label="优惠金额">
              <el-input-number
                v-model="form.discountAmount"
                :min="0"
                :precision="2"
                :step="1"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="4">
            <el-form-item label="备注">
              <el-input v-model="form.remark" placeholder="选填" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </div>

    <div class="card">
      <div class="card-head">
        <span>销售明细</span>
        <el-button type="primary" plain size="small" :icon="Plus" @click="addItem">
          添加商品
        </el-button>
      </div>

      <el-table :data="form.items" border size="small" empty-text="请点击右上角「添加商品」">
        <el-table-column type="index" label="#" width="46" align="center" />
        <el-table-column label="商品" min-width="260">
          <template #default="{ row }">
            <el-select
              v-model="row.productId"
              placeholder="搜索商品名称 / 编码"
              filterable
              style="width: 100%"
              @change="(val) => onProductChange(row, val)"
            >
              <el-option
                v-for="p in products"
                :key="p.id"
                :label="`${p.name}（${p.code}）`"
                :value="p.id"
              >
                <span>{{ p.name }}</span>
                <span class="opt-stock" :class="{ low: p.stockQuantity < p.stockLower }">
                  库存 {{ num(p.stockQuantity) }}{{ p.unit || '' }}
                </span>
              </el-option>
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="当前库存" width="100" align="right">
          <template #default="{ row }">
            <span :class="row._stock < row._need ? 'text-danger' : 'text-muted'">
              {{ num(row._stock) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="130">
          <template #default="{ row }">
            <el-input-number
              v-model="row.quantity"
              :min="1"
              :step="1"
              controls-position="right"
              style="width: 100%"
            />
          </template>
        </el-table-column>
        <el-table-column label="售价" width="130">
          <template #default="{ row }">
            <el-input-number
              v-model="row.price"
              :min="0"
              :precision="2"
              :step="0.1"
              controls-position="right"
              style="width: 100%"
            />
          </template>
        </el-table-column>
        <el-table-column label="金额" width="110" align="right">
          <template #default="{ row }">
            <span class="money">{{ money(row.quantity * row.price) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70" align="center">
          <template #default="{ $index }">
            <el-button link type="danger" @click="form.items.splice($index, 1)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="summary">
        <span>合计数量 <b>{{ num(totalQuantity) }}</b></span>
        <span>销售金额 <b>{{ money(totalAmount) }}</b> 元</span>
        <span>优惠 <b>{{ money(form.discountAmount) }}</b> 元</span>
        <span>应收 <b>{{ money(payAmount) }}</b> 元</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Back, Check, Plus } from '@element-plus/icons-vue'
import { createSale } from '@/api/sale'
import { pageProducts } from '@/api/product'
import { money, num, today } from '@/utils/format'

const router = useRouter()

const saving = ref(false)
const products = ref([])

const form = reactive({
  customerName: '散客',
  orderDate: today(),
  discountAmount: 0,
  remark: '',
  items: []
})

const totalQuantity = computed(() =>
  form.items.reduce((s, r) => s + Number(r.quantity || 0), 0)
)
const totalAmount = computed(() =>
  form.items.reduce((s, r) => s + Number(r.quantity || 0) * Number(r.price || 0), 0)
)
const payAmount = computed(() => Math.max(totalAmount.value - Number(form.discountAmount || 0), 0))

function addItem() {
  form.items.push({
    productId: null,
    quantity: 1,
    price: 0,
    _stock: 0,
    _need: 1
  })
}

function onProductChange(row, productId) {
  const p = products.value.find((x) => x.id === productId)
  if (p) {
    row.price = Number(p.salePrice || 0)
    row._stock = Number(p.stockQuantity || 0)
  }
}

/** 提交前先在前端把明显库存不足的行挑出来，省一次网络往返 */
function validate() {
  if (!form.items.length) {
    ElMessage.warning('请至少添加一条销售明细')
    return false
  }
  for (const row of form.items) {
    if (!row.productId) {
      ElMessage.warning('存在未选择商品的行')
      return false
    }
    if (!row.quantity || row.quantity <= 0) {
      ElMessage.warning('销售数量必须大于 0')
      return false
    }
    const p = products.value.find((x) => x.id === row.productId)
    if (p && Number(p.stockQuantity) < Number(row.quantity)) {
      ElMessage.warning(`商品「${p.name}」库存不足：当前 ${p.stockQuantity}，需要 ${row.quantity}`)
      return false
    }
  }
  return true
}

async function onSubmit() {
  if (!validate()) return
  if (payAmount.value < 0) {
    ElMessage.warning('优惠金额不能大于销售金额')
    return
  }

  saving.value = true
  try {
    await createSale({
      customerName: form.customerName || '散客',
      orderDate: form.orderDate,
      discountAmount: form.discountAmount || 0,
      remark: form.remark || undefined,
      items: form.items.map((r) => ({
        productId: r.productId,
        quantity: r.quantity,
        price: r.price
      }))
    })
    ElMessage.success('开单成功，库存已按批次扣减')
    router.push('/sale')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  try {
    const p = await pageProducts({ page: 1, size: 200, status: 1 })
    products.value = (p && p.records) || []
  } catch {
    /* 拦截器已提示 */
  }
  addItem()
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
  margin-bottom: 12px;
}

.summary {
  margin-top: 14px;
  text-align: right;
  color: #606266;
  font-size: 14px;
}

.summary span {
  margin-left: 22px;
}

.summary b {
  color: #f56c6c;
  font-size: 16px;
  margin: 0 3px;
}

.opt-stock {
  float: right;
  color: #67c23a;
  font-size: 12px;
  margin-left: 12px;
}

.opt-stock.low {
  color: #f56c6c;
}
</style>
