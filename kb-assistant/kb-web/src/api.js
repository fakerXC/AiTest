/**
 * API 助手：文档管理 REST + 问答 SSE 读流（POST + fetch + ReadableStream，第 4 章套路）。
 */

const BASE = '/api';

export async function listDocuments() {
  const resp = await fetch(`${BASE}/documents`);
  return resp.json();
}

export async function uploadDocument(file, docId) {
  const form = new FormData();
  form.append('file', file);
  const url = docId ? `${BASE}/documents/upload?docId=${encodeURIComponent(docId)}`
                    : `${BASE}/documents/upload`;
  const resp = await fetch(url, { method: 'POST', body: form });
  if (!resp.ok) throw new Error('上传失败: ' + resp.status);
  return resp.json();
}

export async function deleteDocument(docId) {
  const resp = await fetch(`${BASE}/documents/${encodeURIComponent(docId)}`, { method: 'DELETE' });
  return resp.json();
}

export async function reingestDocument(docId) {
  const resp = await fetch(`${BASE}/documents/${encodeURIComponent(docId)}/reingest`, { method: 'POST' });
  return resp.json();
}

/**
 * SSE 问答读流。
 * @param conversationId 会话 ID
 * @param question 问题
 * @param onEvent 回调 (event, data)：chunk / sources / done / error
 */
export async function askStream(conversationId, question, onEvent) {
  const resp = await fetch(`${BASE}/chat/ask`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ conversationId, question }),
  });
  if (!resp.ok) throw new Error('请求失败: ' + resp.status);

  const reader = resp.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    // SSE 事件以空行分隔
    let idx;
    while ((idx = buffer.indexOf('\n\n')) >= 0) {
      const block = buffer.slice(0, idx);
      buffer = buffer.slice(idx + 2);
      const eventMatch = block.match(/event:(\w+)/);
      const data = block.split('\n')
          .filter(l => l.startsWith('data:'))
          .map(l => l.slice(5))
          .join('\n');
      if (eventMatch) onEvent(eventMatch[1], data);
    }
  }
}
