<template>
  <div class="card">
    <h3>上传文档</h3>
    <p style="font-size:12px;color:#888;margin:6px 0">
      支持 PDF / Word / Markdown / TXT；docId 不传则用文件名（去后缀）。
      同名 docId 重复上传会触发「先删后插」，版本号 +1。
    </p>
    <div class="input-row">
      <input type="file" ref="fileInput" style="padding:8px" />
      <input v-model="docId" placeholder="docId（可选，如 hr-policy-2025）" style="max-width:280px" />
      <button @click="upload" :disabled="uploading">{{ uploading ? '摄取中…' : '上传并摄取' }}</button>
    </div>
    <p v-if="message" style="font-size:13px;margin-top:8px">{{ message }}</p>
  </div>

  <div class="card">
    <h3>文档列表</h3>
    <table class="docs">
      <thead>
        <tr><th>docId</th><th>名称</th><th>版本</th><th>块数</th><th>更新时间</th><th>操作</th></tr>
      </thead>
      <tbody>
        <tr v-for="d in docs" :key="d.docId">
          <td>{{ d.docId }}</td>
          <td>{{ d.name }}</td>
          <td>v{{ d.version }}</td>
          <td>{{ d.chunkCount }}</td>
          <td>{{ d.updatedAt }}</td>
          <td>
            <button class="btn" @click="reingest(d.docId)">重新摄取</button>
            <button class="btn danger" @click="remove(d.docId)">删除</button>
          </td>
        </tr>
        <tr v-if="docs.length === 0">
          <td colspan="6" style="color:#999;text-align:center">暂无文档，先上传一份</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { listDocuments, uploadDocument, deleteDocument, reingestDocument } from '../api';

const docs = ref([]);
const docId = ref('');
const fileInput = ref(null);
const uploading = ref(false);
const message = ref('');

async function refresh() {
  docs.value = await listDocuments();
}

async function upload() {
  const file = fileInput.value?.files?.[0];
  if (!file) { message.value = '请先选择文件'; return; }
  uploading.value = true;
  message.value = '';
  try {
    const r = await uploadDocument(file, docId.value);
    message.value = `摄取完成：${r.docName} v${r.version}，共 ${r.chunkCount} 块`;
    fileInput.value.value = '';
    await refresh();
  } catch (e) {
    message.value = '失败：' + e.message;
  } finally {
    uploading.value = false;
  }
}

async function reingest(id) {
  const r = await reingestDocument(id);
  message.value = `重新摄取完成：${id} -> v${r.version}`;
  await refresh();
}

async function remove(id) {
  if (!confirm(`确认删除文档 ${id}？其全部 chunk 将从向量库和 ES 中清除`)) return;
  await deleteDocument(id);
  await refresh();
}

onMounted(refresh);
</script>
