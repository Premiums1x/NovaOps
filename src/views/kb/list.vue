<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { Modal, message, type TableProps, type UploadProps } from 'ant-design-vue'
import dayjs from 'dayjs'
import { deleteKbDocumentApi, getKbDocumentChunksApi, getKbDocumentsApi, updateKbDocumentTitleApi, uploadKbDocumentApi } from '@/api/kb'
import type { KbChunkDto, KbDocumentDto, KbDocumentStatus } from '@/types/kb'

defineOptions({ name: 'KbList' })
const loading=ref(false),uploading=ref(false),drawerOpen=ref(false),titleOpen=ref(false)
const list=ref<KbDocumentDto[]>([]),chunks=ref<KbChunkDto[]>([]),selected=ref<KbDocumentDto>(),editingTitle=ref('')
const page=ref(1),pageSize=ref(10),total=ref(0)
const filters=reactive<{keyword:string;fileType?:string;status?:KbDocumentStatus}>({keyword:''})
let pollTimer:number|undefined
const columns:TableProps['columns']=[{title:'文档',dataIndex:'title',key:'title'},{title:'源文件',dataIndex:'fileName',key:'fileName'},{title:'类型',dataIndex:'fileType',key:'fileType',width:90},{title:'状态',key:'status',width:130},{title:'分块',dataIndex:'chunkCount',key:'chunkCount',width:90},{title:'更新时间',key:'updatedAt',width:180},{title:'操作',key:'actions',width:210}]
const statusMeta:Record<KbDocumentStatus,{label:string;color:string}>={PARSING:{label:'解析中',color:'processing'},VECTORIZING:{label:'向量化中',color:'processing'},READY:{label:'就绪',color:'success'},FAILED:{label:'失败',color:'error'}}
const hasPending=computed(()=>list.value.some(item=>item.status==='PARSING'||item.status==='VECTORIZING'))
const fetchList=async()=>{loading.value=true;try{const data=await getKbDocumentsApi({page:page.value,pageSize:pageSize.value,keyword:filters.keyword||undefined,fileType:filters.fileType,status:filters.status});list.value=data.list;total.value=data.total;if(hasPending.value&&!pollTimer)pollTimer=window.setInterval(fetchList,3000);if(!hasPending.value&&pollTimer){clearInterval(pollTimer);pollTimer=undefined}}finally{loading.value=false}}
const beforeUpload:UploadProps['beforeUpload']=async(file)=>{const ext=file.name.split('.').pop()?.toLowerCase();if(!['md','pdf','doc','docx'].includes(ext||'')){message.error('仅支持 md/pdf/doc/docx');return false}if(file.size>30*1024*1024){message.error('文件不能超过 30MB');return false}uploading.value=true;try{await uploadKbDocumentApi(file as File);message.success('上传成功，后台正在解析');await fetchList()}finally{uploading.value=false}return false}
const showChunks=async(document:KbDocumentDto)=>{selected.value=document;chunks.value=await getKbDocumentChunksApi(document.id);drawerOpen.value=true}
const editTitle=(document:KbDocumentDto)=>{selected.value=document;editingTitle.value=document.title;titleOpen.value=true}
const saveTitle=async()=>{if(!selected.value||!editingTitle.value.trim())return;await updateKbDocumentTitleApi(selected.value.id,editingTitle.value.trim());titleOpen.value=false;message.success('标题已更新');await fetchList()}
const remove=(document:KbDocumentDto)=>Modal.confirm({title:'确认删除文档？',content:'系统会先清理向量，再删除元数据。此操作不可撤销。',okType:'danger',async onOk(){await deleteKbDocumentApi(document.id);message.success('文档已删除');await fetchList()}})
onMounted(fetchList);onBeforeUnmount(()=>{if(pollTimer)clearInterval(pollTimer)})
</script>

<template>
  <a-card title="知识库管理">
    <template #extra><a-upload :show-upload-list="false" :before-upload="beforeUpload" accept=".md,.pdf,.doc,.docx"><a-button type="primary" :loading="uploading">上传文档</a-button></a-upload></template>
    <a-alert type="info" show-icon message="上传后将自动解析、分块并向量化；支持 md、pdf、doc、docx，最大 30MB。" class="hint" />
    <a-form layout="inline" class="filters">
      <a-form-item><a-input v-model:value="filters.keyword" allow-clear placeholder="文件名或标题" /></a-form-item>
      <a-form-item label="类型"><a-select v-model:value="filters.fileType" allow-clear style="width:120px" :options="['md','pdf','doc','docx'].map(value=>({label:value.toUpperCase(),value}))" /></a-form-item>
      <a-form-item label="状态"><a-select v-model:value="filters.status" allow-clear style="width:140px" :options="Object.entries(statusMeta).map(([value,item])=>({value,label:item.label}))" /></a-form-item>
      <a-form-item><a-button type="primary" @click="page=1;fetchList()">查询</a-button></a-form-item>
    </a-form>
    <a-table :columns="columns" :data-source="list" :loading="loading" row-key="id" :pagination="{current:page,pageSize,total,showSizeChanger:true}" @change="p=>{page=p.current||1;pageSize=p.pageSize||10;fetchList()}">
      <template #bodyCell="{column,record}">
        <template v-if="column.key==='status'"><a-tooltip v-if="record.errorMsg" :title="record.errorMsg"><a-tag :color="statusMeta[record.status as KbDocumentStatus].color">{{statusMeta[record.status as KbDocumentStatus].label}}</a-tag></a-tooltip><a-tag v-else :color="statusMeta[record.status as KbDocumentStatus].color">{{statusMeta[record.status as KbDocumentStatus].label}}</a-tag></template>
        <template v-else-if="column.key==='updatedAt'">{{dayjs(record.updatedAt).format('YYYY-MM-DD HH:mm')}}</template>
        <template v-else-if="column.key==='actions'"><a-space><a-button type="link" size="small" :disabled="record.status!=='READY'" @click="showChunks(record as KbDocumentDto)">分块预览</a-button><a-button type="link" size="small" @click="editTitle(record as KbDocumentDto)">改标题</a-button><a-button type="link" danger size="small" @click="remove(record as KbDocumentDto)">删除</a-button></a-space></template>
      </template>
    </a-table>
    <a-drawer v-model:open="drawerOpen" width="min(720px, 92vw)" :title="`${selected?.title||''} · 分块预览`"><a-list :data-source="chunks"><template #renderItem="{item}"><a-list-item><a-card size="small" :title="`Chunk ${item.chunkIndex+1}`" class="chunk-card">{{item.content}}</a-card></a-list-item></template></a-list></a-drawer>
    <a-modal v-model:open="titleOpen" title="编辑文档标题" @ok="saveTitle"><a-input v-model:value="editingTitle" :maxlength="255" /></a-modal>
  </a-card>
</template>

<style scoped>
.hint,.filters{margin-bottom:16px}.chunk-card{width:100%;white-space:pre-wrap;line-height:1.7}
</style>
