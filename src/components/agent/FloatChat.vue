<script setup lang="ts">
import { onBeforeUnmount,ref } from 'vue'
import { CloseOutlined,CustomerServiceOutlined } from '@ant-design/icons-vue'
import ChatCore from './ChatCore.vue'
const open=ref(false),position=ref({x:Math.max(20,window.innerWidth-470),y:Math.max(20,window.innerHeight-650)});let offset={x:0,y:0}
const move=(event:PointerEvent)=>{position.value={x:Math.max(8,Math.min(window.innerWidth-420,event.clientX-offset.x)),y:Math.max(8,Math.min(window.innerHeight-120,event.clientY-offset.y))}}
const stopDrag=()=>{window.removeEventListener('pointermove',move);window.removeEventListener('pointerup',stopDrag)}
const startDrag=(event:PointerEvent)=>{offset={x:event.clientX-position.value.x,y:event.clientY-position.value.y};window.addEventListener('pointermove',move);window.addEventListener('pointerup',stopDrag)}
onBeforeUnmount(stopDrag)
</script>
<template>
  <a-float-button v-if="!open" type="primary" tooltip="智能客服" @click="open=true"><template #icon><CustomerServiceOutlined/></template></a-float-button>
  <section v-else class="float-chat" :style="{left:`${position.x}px`,top:`${position.y}px`}">
    <header class="drag-handle" @pointerdown="startDrag"><span><CustomerServiceOutlined/> NovaOps 智能客服</span><a-button type="text" size="small" @pointerdown.stop @click="open=false"><CloseOutlined/></a-button></header>
    <ChatCore compact />
  </section>
</template>
<style scoped>
.float-chat{position:fixed;z-index:1000;width:min(420px,calc(100vw - 16px));height:min(610px,calc(100vh - 16px));display:grid;grid-template-rows:48px minmax(0,1fr);border:1px solid var(--nova-border);border-radius:14px;overflow:hidden;background:var(--nova-surface);box-shadow:0 20px 60px rgba(0,0,0,.25)}.drag-handle{display:flex;align-items:center;justify-content:space-between;padding:0 10px 0 16px;color:var(--nova-text);font-weight:600;border-bottom:1px solid var(--nova-border);cursor:move;user-select:none;background:var(--nova-surface-elevated)}
</style>
