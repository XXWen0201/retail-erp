<template>
  <div class="page">
    <div class="page-header">
      <div class="page-title">
        商品分类
        <span class="sub">共 {{ rows.length }} 个分类</span>
      </div>
      <div class="page-actions">
        <el-button type="primary" :icon="Plus" @click="openForm()">新增分类</el-button>
      </div>
    </div>

    <div class="card">
      <el-table v-loading="loading" :data="rows" stripe border>
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="name" label="分类名称" min-width="180" />
        <el-table-column prop="sort" label="排序" width="100" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" effect="plain">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openForm(row)">编辑</el-button>
            <el-button link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无分类，点右上角新增" />
        </template>
      </el-table>
    </div>

    <el-dialog
      v-model="formVisible"
      :title="form.id ? '编辑分类' : '新增分类'"
      width="440px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-form-item label="分类名称" prop="name">
          <el-input v-model="form.name" placeholder="如 文具用品" />
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="form.sort" :min="0" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
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
import { Plus } from '@element-plus/icons-vue'
import {
  createCategory,
  deleteCategory,
  listCategories,
  updateCategory
} from '@/api/category'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])

async function load() {
  loading.value = true
  try {
    rows.value = (await listCategories()) || []
  } finally {
    loading.value = false
  }
}

const formVisible = ref(false)
const formRef = ref()
const form = reactive({ id: null, name: '', parentId: 0, sort: 1, status: 1 })

const rules = {
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }]
}

function openForm(row) {
  Object.assign(form, { id: null, name: '', parentId: 0, sort: 1, status: 1 })
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
    const payload = {
      name: form.name,
      parentId: form.parentId || 0,
      sort: form.sort,
      status: form.status
    }
    if (form.id) {
      await updateCategory(form.id, payload)
      ElMessage.success('修改成功')
    } else {
      await createCategory(payload)
      ElMessage.success('新增成功')
    }
    formVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function onDelete(row) {
  await ElMessageBox.confirm(`确定删除分类「${row.name}」吗？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  await deleteCategory(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>
