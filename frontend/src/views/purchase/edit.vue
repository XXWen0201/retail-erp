<template>
  <div class="page">
    <div class="page-header">
      <div class="page-title">
        {{ form.id ? '编辑采购单' : '新建采购单' }}
        <span class="sub">保存后为草稿状态，入库才真正影响库存</span>
      </div>
      <div class="page-actions">
        <el-button :icon="Back" @click="$router.back()">返回列表</el-button>
        <el-button type="primary" :loading="saving" :icon="Check" @click="onSave">
          保存草稿
        </el-button>
      </div>
    </div>

    <div class="card">
      <el-form :model="form" label-width="90px" :disabled="saving">
        <el-row :gutter="16">
          <el-col :xs="24" :md="10">
            <el-form-item label="供应商" required>
              <el-select
                v-model="form.supplierId"
                placeholder="请选择供应商"
                filterable
                style="width: 100%"
              >
                <el-option v-for="s in suppliers" :key="s.value" :label="s.label" :value="s.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="7">
            <el-form-item label="下单日期" required>
              <el-date-picker
                v-model="form.orderDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="7">
            <el-form-item label="备注">
              <el-input v-model="form.remark" placeholder="选填" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </div>

    <div class="card">
      <div class="card-head">
        <span>采购明细</span>
        <div>
          <el-button type="primary" plain size="small" :icon="Plus" @click="addItem">
            添加商品
          </el-button>
          <el-button size="small" :icon="Delete" @click="clearItems">清空</el-button>
        </div>
      </div>

      <el-table :data="form.items" border size="small" empty-text="请点击右上角「添加商品」">
        <el-table-column type="index" label="#" width="46" align="center" />
        <el-table-column label="商品" min-width="230">
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
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="120">
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
        <el-table-column label="单价" width="130">
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
        <el-table-column label="金额" width="100" align="right">
          <template #default="{ row }">
            <span class="money">{{ money(row.quantity * row.price) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="生产日期" width="150">
          <template #default="{ row }">
            <el-date-picker
              v-model="row.productionDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选填"
              style="width: 100%"
              @change="() => onDateChange(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="到期日期" width="150">
          <template #default="{ row }">
            <el-date-picker
              v-model="row.expireDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="自动推算"
              style="width: 100%"
            />
          </template>
        </el-table-column>
        <el-table-column label="批次号" width="150">
          <template #default="{ row }">
            <el-input v-model="row.batchNo" placeholder="留空自动生成" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70" align="center">
          <template #default="{ $index }">
            <el-button link type="danger" @click="form.items.splice($index, 1)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="summary">
        <span>共 <b>{{ form.items.length }}</b> 种商品</span>
        <span>合计数量 <b>{{ num(totalQuantity) }}</b></span>
        <span>合计金额 <b class="money">{{ money(totalAmount) }}</b> 元</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Back, Check, Delete, Plus } from '@element-plus/icons-vue'
import { createPurchase, getPurchase, updatePurchase } from '@/api/purchase'
import { supplierOptions } from '@/api/supplier'
import { pageProducts } from '@/api/product'
import { money, num, today } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const saving = ref(false)
const suppliers = ref([])
const products = ref([])

const form = reactive({
  id: null,
  supplierId: null,
  orderDate: today(),
  remark: '',
  items: []
})

const totalQuantity = computed(() =>
  form.items.reduce((s, r) => s + Number(r.quantity || 0), 0)
)
const totalAmount = computed(() =>
  form.items.reduce((s, r) => s + Number(r.quantity || 0) * Number(r.price || 0), 0)
)

function addItem() {
  form.items.push({
    productId: null,
    quantity: 1,
    price: 0,
    productionDate: '',
    expireDate: '',
    batchNo: ''
  })
}

function clearItems() {
  form.items = []
}

/** 选中商品后带出默认进货价 */
function onProductChange(row, productId) {
  const p = products.value.find((x) => x.id === productId)
  if (p && !row.price) row.price = Number(p.purchasePrice || 0)
}

/** 有生产日期且商品配置了保质期时，自动推算到期日 */
function onDateChange(row) {
  if (!row.productionDate) return
  const p = products.value.find((x) => x.id === row.productId)
  const days = p ? Number(p.shelfLifeDays || 0) : 0
  if (days > 0) {
    const d = new Date(row.productionDate)
    d.setDate(d.getDate() + days)
    row.expireDate = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(
      d.getDate()
    ).padStart(2, '0')}`
  }
}

async function onSave() {
  if (!form.supplierId) {
    ElMessage.warning('请选择供应商')
    return
  }
  if (!form.orderDate) {
    ElMessage.warning('请选择下单日期')
    return
  }
  if (!form.items.length) {
    ElMessage.warning('请至少添加一条采购明细')
    return
  }
  const invalid = form.items.find((r) => !r.productId || !r.quantity || r.quantity <= 0)
  if (invalid) {
    ElMessage.warning('明细中存在未选择商品或数量非法的行')
    return
  }

  saving.value = true
  try {
    const payload = {
      supplierId: form.supplierId,
      orderDate: form.orderDate,
      remark: form.remark || undefined,
      items: form.items.map((r) => ({
        productId: r.productId,
        quantity: r.quantity,
        price: r.price,
        productionDate: r.productionDate || undefined,
        expireDate: r.expireDate || undefined,
        batchNo: r.batchNo || undefined
      }))
    }

    if (form.id) {
      await updatePurchase(form.id, payload)
      ElMessage.success('采购单已更新')
    } else {
      await createPurchase(payload)
      ElMessage.success('采购单已保存为草稿，可在列表中入库')
    }
    router.push('/purchase')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  try {
    const [s, p] = await Promise.all([
      supplierOptions(),
      pageProducts({ page: 1, size: 200, status: 1 })
    ])
    suppliers.value = s || []
    products.value = (p && p.records) || []
  } catch {
    /* 拦截器已提示 */
  }

  const id = route.query.id
  if (id) {
    const data = await getPurchase(id)
    if (data) {
      form.id = data.id
      form.supplierId = data.supplierId
      form.orderDate = data.orderDate
      form.remark = data.remark || ''
      form.items = (data.items || []).map((it) => ({
        productId: it.productId,
        quantity: it.quantity,
        price: Number(it.price),
        productionDate: it.productionDate || '',
        expireDate: it.expireDate || '',
        batchNo: it.batchNo || ''
      }))
    }
  }
  if (!form.items.length) addItem()
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
</style>
