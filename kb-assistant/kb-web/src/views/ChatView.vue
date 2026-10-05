<template>
  <div class="chat-history" ref="historyEl">
    <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
      <div class="role">{{ m.role === 'user' ? '我' : '知识库助手' }}</div>
      <div class="bubble">
        <template v-for="(seg, j) in renderSegments(m)" :key="j">
          <sup v-if="seg.cite && seg.ref" class="cite" @click="showSource(seg.ref)">[{{ seg.cite }}]</sup>
          <sup v-else-if="seg.cite" class="cite cite-broken">[{{ seg.cite }}]</sup>
          <span v-else>{{ seg.text }}</span>
        </template>
      </div>
    </div>
    <div v-if="streaming" class="msg assistant">
      <div class="role">知识库助手</div>
      <div class="bubble">
        <template v-for="(seg, j) in renderSegments({ text: answer, sources })" :key="j">
          <sup v-if="seg.cite && seg.ref" class="cite" @click="showSource(seg.ref)">[{{ seg.cite }}]</sup>
          <sup v-else-if="seg.cite" class="cite cite-broken">[{{ seg.cite }}]</sup>
          <span v-else>{{ seg.text }}</span>
        </template>
        <span v-if="!done">▍</span>
      </div>
    </div>
  </div>

  <div class="input-row">
    <input v-model="question" placeholder="问问制度、流程、IT 配置……"
           @keyup.enter="send" :disabled="streaming" />
    <button @click="send" :disabled="streaming || !question.trim()">发送</button>
  </div>

  <SourceRefCard v-if="currentSource" :source="currentSource" @close="currentSource = null" />
</template>

<script setup>
import { nextTick, ref } from 'vue';
import { askStream } from '../api';
import SourceRefCard from '../components/SourceRefCard.vue';

// 会话 ID 持久化：同一浏览器标签延续同一对话（服务端 JDBC 记忆）
const conversationId = localStorage.getItem('kb-conversationId') || crypto.randomUUID();
localStorage.setItem('kb-conversationId', conversationId);

const messages = ref([]);
const question = ref('');
const answer = ref('');
const sources = ref([]);       // SourceRef[]，流尾一次性到达
const streaming = ref(false);
const done = ref(false);
const currentSource = ref(null);
const historyEl = ref(null);

/** 把答案文本按 [n] 拆成片段，n 能映射到 sources 的渲染成可点击角标，否则降级为纯文本 */
function renderSegments(m) {
  const srcs = m.sources || [];
  const text = m.text || '';
  const segs = [];
  let last = 0;
  const re = /\[(\d{1,2})]/g;
  let match;
  while ((match = re.exec(text)) !== null) {
    if (match.index > last) segs.push({ text: text.slice(last, match.index) });
    const idx = parseInt(match[1], 10);
    segs.push({ cite: match[1], ref: srcs.find(s => s.index === idx) || null });
    last = match.index + match[0].length;
  }
  if (last < text.length) segs.push({ text: text.slice(last) });
  return segs;
}

function showSource(ref) {
  currentSource.value = ref;
}

async function send() {
  const q = question.value.trim();
  if (!q || streaming.value) return;
  messages.value.push({ role: 'user', text: q });
  question.value = '';
  answer.value = '';
  sources.value = [];
  done.value = false;
  streaming.value = true;
  scrollBottom();

  try {
    await askStream(conversationId, q, (event, data) => {
      if (event === 'chunk') {
        answer.value += data;
        scrollBottom();
      } else if (event === 'sources') {
        sources.value = JSON.parse(data);   // 引用列表在正文流末尾一次性到达
      } else if (event === 'done') {
        done.value = true;
      } else if (event === 'error') {
        answer.value += '\n（引用解析失败：' + data + '）';
      }
    });
  } catch (e) {
    answer.value += '\n（请求失败：' + e.message + '）';
  } finally {
    messages.value.push({ role: 'assistant', text: answer.value, sources: sources.value });
    streaming.value = false;
    scrollBottom();
  }
}

async function scrollBottom() {
  await nextTick();
  if (historyEl.value) historyEl.value.scrollTop = historyEl.value.scrollHeight;
}
</script>
