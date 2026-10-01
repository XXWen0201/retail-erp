<template>
  <div class="page">
    <div class="page-header">
      <div class="page-title">
        商品管理
        <span class="sub">共 {{ total }} 个商品</span>
      </div>
      <div class="page-actions">
        <el-button type="primary" :icon="Plus" @click="openForm()">新增商品</el-button>
      </div>
    </div>

    <div class="card">
      <div class="search-bar">
        <el-input
          v-model="query.keyword"
          placeholder="名称 / 编码 / 条码"
          clearable
          style="width: 220px"
          @keyup.enter="search"
        />
        <el-select v-model="query.categoryId" placeholder="全部分类" clearable style="width: 150px">
          <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 120px">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <el-checkbox v-model="query.lowStockOnly" border>仅看低于下限</el-checkbox>
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button :icon="Refresh" @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" stripe border>
        <el-table-column prop="code" label="编码" width="100" />
        <el-table-column prop="name" label="商品名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="categoryName" label="分类" width="110" />
        <el-table-column prop="spec" label="规格" width="110" show-overflow-tooltip />
        <el-table-column prop="unit" label="单位" width="70" align="center" />
        <el-table-column label="进价" width="90" align="right">
          <template #default="{ row }">{{ money(row.purchasePrice) }}</template>
        </el-table-column>
        <el-table-column label="售价" width="90" align="right">
          <template #default="{ row }">
            <span class="money">{{ money(row.salePrice) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="当前库存" width="100" align="right">
          <template #default="{ row }">
            <span class="money">{{ num(row.stockQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="上下限" width="110" align="center">
          <template #default="{ row }">
            <span class="text-muted">{{ num(row.stockLower) }} ~ {{ num(row.stockUpper) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="平均成本" width="100" align="right">
          <template #default="{ row }">
            <span class="text-muted">{{ num(row.avgCost, 4) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="库存状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.stockStatus)" size="small" effect="light">
              {{ statusLabel(row.stockStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" effect="plain">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openForm(row)">编辑</el-button>
            <el-button link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="load"
          @current-change="load"
        />
      </div>
    </div>

    <el-dialog
      v-model="formVisible"
      :title="form.id ? '编辑商品' : '新增商品'"
      width="760px"
      top="6vh"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-row :gutter="14">
          <el-col :span="12">
            <el-form-item label="商品编码" prop="code">
              <el-input v-model="form.code" placeholder="如 P1031" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="条形码" prop="barcode">
              <el-input v-model="form.barcode" placeholder="选填" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="商品名称" prop="name">
              <el-input v-model="form.name" placeholder="如 晨光中性笔 0.5mm 黑" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="商品分类" prop="categoryId">
              <el-select v-model="form.categoryId" placeholder="请选择" style="width: 100%">
                <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="规格" prop="spec">
              <el-input v-model="form.spec" placeholder="如 12支/盒" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单位" prop="unit">
              <el-input v-model="form.unit" placeholder="如 支 / 瓶 / 个" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="进货价" prop="purchasePrice">
              <el-input-number
                v-model="form.purchasePrice"
                :min="0"
                :precision="2"
                :step="0.1"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="销售价" prop="salePrice">
              <el-input-number
                v-model="form.salePrice"
                :min="0"
                :precision="2"
                :step="0.1"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="库存下限" prop="stockLower">
              <el-input-number v-model="form.stockLower" :min="0" :step="10" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="库存上限" prop="stockUpper">
              <el-input-number v-model="form.stockUpper" :min="0" :step="10" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="保质期(天)" prop="shelfLifeDays">
              <el-input-number
                v-model="form.shelfLifeDays"
                :min="0"
                :step="30"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio :value="1">启用</el-radio>
                <el-radio :value="0">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-alert type="info" :closable="false" show-icon>
          保质期填 0 表示不管理效期（如文具）；填了天数后，采购入库时可按生产日期自动推算到期日。
        </el-alert>
      </el-form>

      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { createProduct, deleteProduct, pageProducts, updateProduct } from '@/api/product'
import { listCategories } from '@/api/category'
import { STOCK_STATUS, money, num } from '@/utils/format'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const categories = ref([])

const query = reactive({
  keyword: '',
  categoryId: null,
  status: null,
  lowStockOnly: false,
  page: 1,
  size: 10
})

const statusTag = (s) => (STOCK_STATUS[s] || {}).type || 'info'
const statusLabel = (s) => (STOCK_STATUS[s] || {}).label || s || '-'

async function load() {
  loading.value = true
  try {
    const res = await pageProducts(query)
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
  Object.assign(query, {
    keyword: '',
    categoryId: null,
    status: null,
    lowStockOnly: false,
    page: 1,
    size: 10
  })
  load()
}

async function loadCategories() {
  try {
    categories.value = (await listCategories()) || []
  } catch {
    /* 拦截器已提示 */
  }
}

/* ---------------- 表单 ---------------- */
const formVisible = ref(false)
const formRef = ref()

const emptyForm = () => ({
  id: null,
  code: '',
  barcode: '',
  name: '',
  categoryId: null,
  spec: '',
  unit: '',
  purchasePrice: 0,
  salePrice: 0,
  stockLower: 0,
  stockUpper: 0,
  shelfLifeDays: 0,
  status: 1
})

const form = reactive(emptyForm())

const rules = {
  code: [{ required: true, message: '请输入商品编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  unit: [{ required: true, message: '请输入单位', trigger: 'blur' }],
  purchasePrice: [{ required: true, message: '请输入进货价', trigger: 'blur' }],
  salePrice: [{ required: true, message: '请输入销售价', trigger: 'blur' }]
}

function openForm(row) {
  Object.assign(form, emptyForm())
  if (row) Object.assign(form, row)
  formVisible.value = true
}

async function onSave() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  if (form.stockUpper > 0 && form.stockUpper < form.stockLower) {
    ElMessage.warning('库存上限不能小于下限')
    return
  }

  saving.value = true
  try {
    const payload = { ...form }
    delete payload.id
    if (form.id) {
      await updateProduct(form.id, payload)
      ElMessage.success('修改成功')
    } else {
      await createProduct(payload)
      ElMessage.success('新增成功，库存已初始化为 0')
    }
    formVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function onDelete(row) {
  await ElMessageBox.confirm(
    `确定删除商品「${row.name}」吗？删除后该商品将不再出现在列表中。`,
    '删除确认',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
  )
  await deleteProduct(row.id)
  ElMessage.success('已删除')
  if (rows.value.length === 1 && query.page > 1) query.page -= 1
  load()
}

onMounted(() => {
  loadCategories()
  load()
})
</script>
