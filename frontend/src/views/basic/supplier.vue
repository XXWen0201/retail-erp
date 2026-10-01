<template>
  <div class="page">
    <div class="page-header">
      <div class="page-title">
        供应商管理
        <span class="sub">共 {{ total }} 家</span>
      </div>
      <div class="page-actions">
        <el-button type="primary" :icon="Plus" @click="openForm()">新增供应商</el-button>
      </div>
    </div>

    <div class="card">
      <div class="search-bar">
        <el-input
          v-model="query.keyword"
          placeholder="名称 / 编码 / 联系人"
          clearable
          style="width: 220px"
          @keyup.enter="search"
        />
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 120px">
          <el-option label="合作中" :value="1" />
          <el-option label="已停用" :value="0" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button :icon="Refresh" @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" stripe border>
        <el-table-column prop="code" label="编码" width="100" />
        <el-table-column prop="name" label="供应商名称" min-width="220" show-overflow-tooltip />
        <el-table-column prop="contact" label="联系人" width="110" />
        <el-table-column prop="phone" label="联系电话" width="140" />
        <el-table-column prop="address" label="地址" min-width="180" show-overflow-tooltip />
        <el-table-column prop="remark" label="备注" min-width="130" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" effect="plain">
              {{ row.status === 1 ? '合作中' : '已停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" align="center" fixed="right">
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
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="load"
          @current-change="load"
        />
      </div>
    </div>

    <el-dialog
      v-model="formVisible"
      :title="form.id ? '编辑供应商' : '新增供应商'"
      width="620px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-row :gutter="14">
          <el-col :span="12">
            <el-form-item label="编码" prop="code">
              <el-input v-model="form.code" placeholder="如 SUP007" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="名称" prop="name">
              <el-input v-model="form.name" placeholder="如 长沙晨光文具批发有限公司" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系人" prop="contact">
              <el-input v-model="form.contact" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话" prop="phone">
              <el-input v-model="form.phone" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="地址" prop="address">
              <el-input v-model="form.address" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio :value="1">合作中</el-radio>
                <el-radio :value="0">已停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
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
import {
  createSupplier,
  deleteSupplier,
  pageSuppliers,
  updateSupplier
} from '@/api/supplier'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)

const query = reactive({ keyword: '', status: null, page: 1, size: 10 })

async function load() {
  loading.value = true
  try {
    const res = await pageSuppliers(query)
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
  Object.assign(query, { keyword: '', status: null, page: 1, size: 10 })
  load()
}

const formVisible = ref(false)
const formRef = ref()
const form = reactive({
  id: null,
  code: '',
  name: '',
  contact: '',
  phone: '',
  address: '',
  remark: '',
  status: 1
})

const rules = {
  code: [{ required: true, message: '请输入编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入供应商名称', trigger: 'blur' }],
  phone: [
    {
      pattern: /^1[3-9]\d{9}$|^0\d{2,3}-?\d{7,8}$/,
      message: '手机号或座机号格式不正确',
      trigger: 'blur'
    }
  ]
}

function openForm(row) {
  Object.assign(form, {
    id: null,
    code: '',
    name: '',
    contact: '',
    phone: '',
    address: '',
    remark: '',
    status: 1
  })
  if (row) Object.assign(form, row)
  formVisible.value = true
}

async function onSave() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    const payload = { ...form }
    delete payload.id
    // 空字符串的备注/地址后端会当成有效值，统一转成 undefined 让后端走默认
    Object.keys(payload).forEach((k) => {
      if (payload[k] === '') payload[k] = undefined
    })
    if (form.id) {
      await updateSupplier(form.id, payload)
      ElMessage.success('修改成功')
    } else {
      await createSupplier(payload)
      ElMessage.success('新增成功')
    }
    formVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function onDelete(row) {
  await ElMessageBox.confirm(`确定删除供应商「${row.name}」吗？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  await deleteSupplier(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>
