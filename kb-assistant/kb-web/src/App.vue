<template>
  <div class="nav">
    <span class="brand">kb-assistant 企业知识库</span>
    <router-link to="/chat">问答</router-link>
    <router-link to="/admin">文档管理</router-link>
  </div>
  <div class="page">
    <router-view />
  </div>
</template>

<script setup>
</script>
