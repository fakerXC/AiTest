# kb-assistant —— 企业知识库问答系统（P2，第 7~8 章实战）

前后端分离的知识库问答：文档上传摄取 → 混合检索（向量 + BM25 + RRF）→ 流式回答 → **引用溯源**（答案句末 [n] 可点击定位到原文 chunk）→ golden set 评估闭环。

## 架构

```
kb-assistant/
├── docker-compose.yml        # Elasticsearch 8.13.2 + ik 分词器（es-ik/Dockerfile 在线装插件）
├── es-ik/                    # pgvector / ollama 复用 ch06-doc-chunking 的容器
├── kb-server/                # Spring Boot 4.1 + Spring AI 2.0.1（Java 21）
│   └── src/main/java/com/example/aiapp/
│       ├── ingest/           # 文档管理 CRUD + 摄取管线（解析→清洗→条款切块→双写→版本号）
│       ├── rag/              # 混合检索：EsBm25Searcher / RrfFusion / Reranker / QueryRewriteService
│       │                     #   + HybridSearchService（问答与评估共用同一个 Bean）
│       │                     #   + HybridRagConfig（RetrievalAugmentationAdvisor 对照实现）
│       ├── chat/             # SSE 问答 + 引用溯源（CitationPromptBuilder / CitationService）
│       ├── memory/           # 对话记忆（JDBC 持久化，消息窗口 20 条）
│       └── eval/             # golden set 评估器（Recall@5 / MRR，确定性计算不调模型）
│   └── src/main/resources/eval/golden-set.jsonl   # 32 题黄金评测集（25 可答 + 7 拒答）
│   └── scripts/eval_ragas_demo.py                 # Ragas 0.4 生成层评估（裁判：Kimi）
└── kb-web/                   # Vue 3 + Vite 前端（问答页 / 文档管理页 / 引用卡片）
```

## 问答链路

```
用户提问 →（JDBC 对话记忆）→ 混合召回：pgvector 向量 top20 + ES BM25(ik) top20
        → RRF 融合（k=60）→ rerank 精排 top5（默认 Noop 直通；有 DASHSCOPE_API_KEY 可切 gte-rerank）
        → 编号上下文拼入 prompt → Kimi 流式生成（SSE）
        → 流尾追加 event: sources（[n] → chunkId 映射，权威在服务端）
```

## 快速开始

```bash
# 0. 基础设施：pgvector + ollama 复用 ch06 项目，ES 用本项目 compose
cd ../ch06-doc-chunking && docker compose up -d && cd ../kb-assistant
docker compose up -d
curl -X POST http://localhost:9200/_analyze -H 'Content-Type: application/json' \
  -d '{"analyzer":"ik_max_word","text":"差旅住宿费报销标准"}'   # 验证 ik 生效

# 1. 后端（默认 Kimi 对话 + Ollama bge-m3 embedding；Kimi 欠费或无 key 时用 local 降级）
cd kb-server
mvn spring-boot:run                                   # 默认：Kimi（需环境变量 KIMI_KEY）
mvn spring-boot:run -Dspring-boot.run.profiles=local  # 纯本地：qwen3:1.7b 对话

# 2. 前端
cd ../kb-web
npm install && npm run dev    # http://localhost:5173

# 3. 检索评估（golden set 回归，不动 Web 服务）
mvn spring-boot:run -Dspring-boot.run.profiles=eval \
  "-Dspring-boot.run.arguments=--spring.main.web-application-type=none"
# 报告写入 kb-server/eval-reports/，含 Recall@5 / MRR / 低分样本明细
```

## 实测结果（样例语料《员工考勤与报销制度》，8 个条款块）

| 项目 | 结果 |
| --- | --- |
| 摄取 | 8 块（条款结构切分），pgvector + ES 双写，chunkId 确定性（`hr-policy-2025#c005`） |
| SSE 问答 | 「出差住宿超标怎么办？」→ 流式回答 + `[1]` 引用 |
| 引用溯源 | `[1]` → `hr-policy-2025#c006`（第九条报销材料），点击角标弹出原文卡片 ✓ |
| 检索评估 | 32 题 golden set：Recall@5 = 1.000，MRR = 1.000 |

> 注意：满分是因为演示语料只有 8 个块、且题目由开发者围绕文档编写——这正是第 8 章警告的
> 「工程师自编题导致评估虚高」。真实语料（50+ 文档、业务方出题）下数字才有说服力。

## 配置要点（application.yml）

- **Kimi**：`base-url: https://api.moonshot.cn/v1`——Spring AI 2.0 底层换成官方 openai-java SDK，
  只补 `/chat/completions`，base-url 必须带 `/v1`（与 1.x 时代相反，教程按 1.x 写的）
- **embedding**：本地 Ollama bge-m3（1024 维），Kimi 无 embedding API
- **rerank**：`kb.rerank.provider=none` 默认直通；配 `DASHSCOPE_API_KEY` 后改 `dashscope` 启用 gte-rerank-v2
- **local profile**：无 Kimi key 或欠费时，对话降级到本地 qwen3:1.7b

## 与教程的差异（踩坑记录）

1. Spring Boot 4 / Spring 7 用 **Jackson 3**（`tools.jackson`），`RestClient.body(JsonNode.class)`
   必须用 Jackson 3 的 JsonNode；Jackson 2（`com.fasterxml`）仅用于独立解析（golden set 加载等）
2. WebFlux 事件循环禁止阻塞：ES / pgvector / embedding 的同步调用必须
   `subscribeOn(Schedulers.boundedElastic())`（ChatController、DocumentController 已处理）
3. chat-memory JDBC starter 会自动装配 `JdbcChatMemoryRepository`，不要再手写重复 Bean
4. ES 首次摄取的「先删」会因索引不存在报 404，`deleteByDocId` 先 `ensureIndex()`
5. Kimi 账户欠费返回 429（suspended）——切 local profile 即可离线演示

## 已知边界

- Ragas 脚本需 Python 3.10+（`pip install "ragas==0.4.3" openai`），本机未装 Python 时跳过
- Dify 对照体验（第 8 章第三部分）为可选环节，未纳入本工程
- 拒答闸（rerank 最高分低于阈值直接拒答，练习 1）未默认开启，阈值需用拒答题校准后配置
