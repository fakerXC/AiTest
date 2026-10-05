# ch06-doc-chunking —— 文档解析与切块策略（Chunking）

《第 6 章 文档解析与切块策略》的配套工程：用 Spring AI ETL 管线把《员工考勤与报销制度》
**解析 → 清洗 → 切块 → 元数据增强 → 入 pgvector**，并做「不同 chunk size 检索效果对比」实验。

> RAG 质量的第一道关口：切出来的块是垃圾，embedding 再好、检索再准，找回来的也是垃圾。

## 目录结构

```
ch06-doc-chunking/
├── docker-compose.yml                  # pgvector（pg16，内置 vector 扩展）
├── docs/
│   └── 员工考勤与报销制度.txt           # 教程附录样例文档（含硬换行、页眉噪声，用于演示清洗）
├── pom.xml                             # Spring Boot 4.0.x + Spring AI BOM 2.0.1 + Java 21
└── src/main/
    ├── java/com/example/aiapp/
    │   ├── ChunkingApplication.java
    │   ├── etl/
    │   │   ├── DocumentLoadService.java    # 6.2 Tika 解析（txt/md 直读，PDF/Word 走 Tika）
    │   │   ├── TextCleaner.java            # 6.4 去页眉页脚水印 + 合并硬换行
    │   │   ├── ChunkingExperiments.java    # 6.5 TokenTextSplitter 切块 + 诊断打印
    │   │   ├── MetadataEnricher.java       # 6.6 docId / docName / titlePath 元数据
    │   │   ├── IngestService.java          # 6.7 先删后插入库 + 解析产出校验（坑三/坑六）
    │   │   └── splitter/
    │   │       ├── ClauseTextSplitter.java     # 练习1：按「第X条」结构切，超长条款二次切
    │   │       └── OverlapTextSplitter.java    # 练习2：段落贪心装填 + 15% 块间重叠
    │   ├── retrieval/
    │   │   ├── RetrievalComparator.java    # 6.8 七个代表性问题检索对比
    │   │   ├── RagAnswerService.java       # chunk + titlePath 拼 prompt，DeepSeek 作答
    │   │   └── ParentChildService.java     # 练习3A：父子检索最小实现
    │   ├── runner/DemoRunner.java          # 命令行入口（四种模式，见下）
    │   └── offline/OfflineChunkingDemo.java # 离线切块演示：不连库、不调 API
    └── resources/application.yml
```

## 前置要求

- JDK 21+、Maven 3.8+
- Docker（跑 pgvector + Ollama）；已启动过第 5 章 `vector-demo` 的 pgvector 容器可复用
- **默认无需任何 API Key**：embedding 用本地 Ollama bge-m3（1024 维），对话用本地 qwen3:1.7b，
  全部跑在 Docker 容器里，模型文件存于 `E:\devtools\ollama\models`
- 可选：配置 `DEEPSEEK_API_KEY` / `ZHIPUAI_API_KEY` 后用云端模型（效果更好）：
  `mvn spring-boot:run -Dspring-boot.run.profiles=cloud`

## 快速开始

### 0. 离线切块演示（无需 Docker / API Key，先看切块长什么样）

```bash
mvn compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java -Dexec.mainClass=com.example.aiapp.offline.OfflineChunkingDemo
```

打印清洗前后 diff、TokenTextSplitter 三档（200/500/1000）对比、条款结构切、带 overlap 段落切。
**调参必打印，打印必看头尾**：盯块尾是否句末标点收尾、token 分布是否均匀、主题是否跨界。

### 1. 启动 pgvector + Ollama

```bash
docker compose up -d
```

首次启动后 Ollama 需要模型（本机已内置，新机器需执行一次）：
`docker exec ollama ollama pull bge-m3` 和 `docker exec ollama ollama pull qwen3:1.7b`

### 2. 完整管线：入库 + 检索对比（默认 512 token）

```bash
mvn spring-boot:run
```

### 3. 三档 chunk size 检索对比实验（教程 6.8）

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--experiment
```

同一份文档按 200 / 500 / 1000 三档串行入库（每档先删后插），每档跑 7 个代表性问题。
预期规律：小块利精确点查询，大块利上下文完整，500 左右是平衡点；
表格伤、断句伤是 chunk size 救不了的，必须在解析清洗层解决。

### 4. 检索 + LLM 作答（观察 chunk 质量对答案的影响）

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--qa=出差住宿超标怎么办？"
```

### 5. 父子检索演示（练习 3A）

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--parent-child
```

子块（128 token）进 pgvector 负责命中；父块（按章聚合）存 `parent_chunk` 表负责喂模型。
观察输出中「命中子块 → 按 parentId 换回父块全文」的流向。

## 动手练习对应关系

| 练习 | 实现 | 验收方式 |
| --- | --- | --- |
| 练习 1：条款结构切分 | `etl/splitter/ClauseTextSplitter` | 离线演示第 2 段输出：块边界不跨条款，每块以完整条款收尾 |
| 练习 2：块间 overlap | `etl/splitter/OverlapTextSplitter` | 离线演示第 3 段输出：「超标部分需提前经分管副总裁审批」整句同时出现在相邻两块 |
| 练习 3A：父子检索 | `retrieval/ParentChildService` | `--parent-child` 模式：子块命中后按 parentId 换出父块全文 |
| 练习 3B：语义切块 | （未实现，可基于 `EmbeddingModel` 逐句算余弦相似度自行扩展） | — |

自定义切块器接入入库管线：`ingestService.ingest(path, docId, docName, 512, new ClauseTextSplitter(512))`。

## 关键配置说明（application.yml）

- **默认（本地）**：embedding 用 Ollama bge-m3（固定 1024 维），对话用 qwen3:1.7b；
  注意 `spring.ai.model.audio/image/moderation: none` 必须显式关闭——openai starter 在
  classpath 上时会尝试装配全部模态，无 API Key 会直接启动失败
- **cloud profile（可选）**：对话走 DeepSeek（`https://api.deepseek.com`，不带 `/v1`）；
  embedding 走智谱 OpenAI 兼容端点，`embeddings-path: /v4/embeddings` 必须覆盖默认值，否则 404
- `dimensions: 1024` 在 embedding 侧和 pgvector 侧必须一致（bge-m3 / embedding-3 都是 1024）
- `demo.doc-id` 用稳定业务标识（`hr-policy-2025`），别用文件哈希——文档更新按它先删后插

## 常见坑速查（教程「常见坑」一节的代码落点）

1. 切跨表格/列表 → 关键表格改写成自然语言句子再入库（样例文档已按此处理）
2. chunk size 走极端 → `--experiment` 用数据说话，别拍脑袋
3. 更新后旧 chunk 残留 → `IngestService` 固定「按 docId 先删后插」
4. Markdown 符号/水印污染 embedding → `TextCleaner` 清洗阶段剥离
5. 中文直接用默认 TokenTextSplitter → `minChunkSizeChars` 随 chunkSize 同步调小；2.0 用 builder
6. 扫描件静默入库 → `IngestService.validateParsedOutput` 清洗后正文不足 200 字直接报错
