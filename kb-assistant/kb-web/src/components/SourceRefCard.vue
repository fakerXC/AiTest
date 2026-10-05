<template>
  <div class="source-pop">
    <span class="close" @click="$emit('close')">✕</span>
    <h4>来源 [{{ source.index }}] {{ source.docName }}</h4>
    <div class="meta">{{ source.titlePath }} · chunkId: {{ source.chunkId }}</div>
    <div class="snippet">{{ source.snippet }}…</div>
  </div>
</template>

<script setup>
defineProps({ source: Object });
defineEmits(['close']);
</script>
