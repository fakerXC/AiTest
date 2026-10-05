# AiTest —— LLM 应用工程师学习实战

《Kimi Agent 教程 · llm-app-engineer-from-zero》第二部分「RAG 工程」的配套代码。

| 目录 | 对应章节 | 内容 |
| --- | --- | --- |
| `ch06-doc-chunking/` | 第 5~6 章 | pgvector 向量库 + 文档解析与 Chunking 全链路（Tika/清洗/四种切法/三档对比实验），默认本地 Ollama 模型，无需 API Key |
| `kb-assistant/` | 第 7~8 章 | P2 企业知识库问答系统：混合检索（向量+BM25+RRF）、引用溯源、对话记忆、golden set 评估、Vue3 前端 |

## 环境（全部便携安装于 E 盘）

- JDK 21：`E:\devtools\jdk-21`；Maven：`E:\devtools\maven`（仓库 `E:\devtools\maven-repo`，阿里云镜像）
- Node.js 22：`E:\app\node`；VS Code 便携版：`E:\app\VSCode`
- Docker Desktop（数据盘在 `E:\devtools\docker-data`）：pgvector / Ollama / Elasticsearch+ik
- 模型：对话 Kimi（`KIMI_KEY`，欠费时可切 local profile 用 Ollama qwen3）；embedding 本地 bge-m3
