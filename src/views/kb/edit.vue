<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import dayjs from 'dayjs'
import { message } from 'ant-design-vue'
import { MdEditor, MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { useRoute, useRouter } from 'vue-router'
import { getKbDetailApi, getKbVersionsApi, saveKbApi } from '@/api/kb'
import { usePermissionStore } from '@/store/permission'
import type { KbVersionDto } from '@/types/kb'

defineOptions({
  name: 'KbEdit',
})

const route = useRoute()
const router = useRouter()
const permissionStore = usePermissionStore()
const loading = ref(false)
const saveLoading = ref(false)
const versions = ref<KbVersionDto[]>([])

const form = reactive({
  title: '',
  tags: [] as string[],
  content: '# 新建知识文档\n\n请开始编写内容...',
})

const articleId = computed(() => {
  const raw = route.params.id
  return raw ? String(raw) : ''
})

const canEdit = computed(() => permissionStore.hasPermission('kb:edit'))
const isEditMode = computed(() => Boolean(articleId.value))
const previewId = computed(() => `kb-preview-${articleId.value || 'new'}`)

const loadDetail = async () => {
  if (!articleId.value) {
    versions.value = []
    return
  }
  loading.value = true
  try {
    const detail = await getKbDetailApi(articleId.value)
    form.title = detail.title
    form.tags = detail.tags
    form.content = detail.content
    versions.value = await getKbVersionsApi(articleId.value)
  } finally {
    loading.value = false
  }
}

const save = async () => {
  if (!canEdit.value) {
    message.warning('当前账号没有编辑权限')
    return
  }
  if (!form.title.trim() || !form.content.trim()) {
    message.warning('标题和正文不能为空')
    return
  }
  saveLoading.value = true
  try {
    const saved = await saveKbApi({
      id: articleId.value || undefined,
      title: form.title.trim(),
      tags: form.tags,
      content: form.content,
    })
    message.success('知识库文章已保存')
    if (!articleId.value) {
      await router.replace(`/kb/edit/${saved.id}`)
    } else {
      await loadDetail()
    }
  } finally {
    saveLoading.value = false
  }
}

watch(
  () => route.params.id,
  () => {
    if (!articleId.value) {
      form.title = ''
      form.tags = []
      form.content = '# 新建知识文档\n\n请开始编写内容...'
    }
    void loadDetail()
  }
)

onMounted(() => {
  void loadDetail()
})
</script>

<template>
  <section class="kb-edit-page">
    <a-card :loading="loading" :bordered="false">
      <template #title>
        <a-space>
          <a-button size="small" @click="router.push('/kb/list')">返回列表</a-button>
          <span>{{ isEditMode ? '编辑知识文章' : '新建知识文章' }}</span>
        </a-space>
      </template>
      <template #extra>
        <a-space>
          <a-button @click="router.push('/kb/list')">取消</a-button>
          <a-button type="primary" :loading="saveLoading" :disabled="!canEdit" @click="save">保存</a-button>
        </a-space>
      </template>

      <a-form layout="vertical">
        <a-form-item label="标题" required>
          <a-input v-model:value="form.title" :disabled="!canEdit" />
        </a-form-item>
        <a-form-item label="标签">
          <a-select
            v-model:value="form.tags"
            mode="tags"
            :disabled="!canEdit"
            :token-separators="[',']"
            placeholder="输入标签后回车"
          />
        </a-form-item>
      </a-form>

      <a-row :gutter="12">
        <a-col :span="14">
          <a-card title="Markdown 编辑区" size="small">
            <MdEditor
              v-model="form.content"
              :preview="false"
              :read-only="!canEdit"
              :style="{ minHeight: '420px' }"
            />
          </a-card>
        </a-col>
        <a-col :span="10">
          <a-card title="实时预览" size="small">
            <div class="preview-wrap">
              <MdPreview :editor-id="previewId" :model-value="form.content" />
            </div>
          </a-card>
        </a-col>
      </a-row>
    </a-card>

    <a-card title="版本历史" :bordered="false">
      <a-timeline>
        <a-timeline-item v-for="version in versions" :key="version.id">
          <div class="version-title">
            {{ version.title }}
            <span class="version-time">{{ dayjs(version.createdAt).format('YYYY-MM-DD HH:mm:ss') }}</span>
          </div>
          <div>编辑人：{{ version.editor }}</div>
          <div>标签：{{ version.tags.join(', ') || '-' }}</div>
        </a-timeline-item>
      </a-timeline>
      <a-empty v-if="!versions.length" description="暂无版本记录" />
    </a-card>
  </section>
</template>

<style scoped>
.kb-edit-page {
  display: grid;
  gap: 12px;
}

.preview-wrap {
  min-height: 420px;
  max-height: 420px;
  overflow: auto;
}

.version-title {
  font-weight: 600;
  display: flex;
  gap: 10px;
}

.version-time {
  color: #64748b;
  font-size: 12px;
}
</style>
