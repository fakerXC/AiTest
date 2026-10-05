# eval_ragas_demo.py —— Ragas 0.4.x 生成层评估（裁判模型：Kimi，OpenAI 兼容协议）
# 用法：
#   1. pip install "ragas==0.4.3" openai
#   2. 先让 Java 系统对 golden set 批量问答，导出 qa_export.jsonl（每行 {"id","question","answer","contexts":[...]}）
#   3. 设置环境变量 KIMI_KEY，python eval_ragas_demo.py
# 注意：Python 端 base_url 要带 /v1（与 Spring AI 2.0 openai-java SDK 一致）
import asyncio, json, os
from ragas.llms import llm_factory
from ragas.metrics.collections import Faithfulness, AnswerRelevancy

# 裁判模型：Kimi。报告里必须写明裁判模型与版本，否则分数无法横向对比
judge = llm_factory(
    "kimi-k2-0905-preview",
    api_key=os.environ["KIMI_KEY"],
    base_url="https://api.moonshot.cn/v1",
)

faithfulness = Faithfulness(llm=judge)   # 忠实度：答案的每个论断能否被上下文支持
relevancy = AnswerRelevancy(llm=judge)   # 相关性：答案是否回应了问题

async def score_one(row: dict) -> dict:
    f = await faithfulness.ascore(
        response=row["answer"],
        retrieved_contexts=row["contexts"],
    )
    r = await relevancy.ascore(
        user_input=row["question"],
        response=row["answer"],
    )
    return {"id": row["id"], "faithfulness": f.value, "answer_relevancy": r.value}

async def main():
    with open("qa_export.jsonl", encoding="utf-8") as fp:
        rows = [json.loads(line) for line in fp if line.strip()]
    # 拒答题（unanswerable）的正确行为是拒答，relevancy 会误伤它——单独统计，不混入平均值
    answerable = [r for r in rows if r.get("type") != "unanswerable"]
    results = [await score_one(r) for r in answerable]   # 教学示例串行跑；生产用 asyncio.gather 并发
    for r in results:
        print(r)
    avg = lambda k: sum(r[k] for r in results) / len(results)
    print(f"faithfulness={avg('faithfulness'):.3f}  answer_relevancy={avg('answer_relevancy'):.3f}")

asyncio.run(main())
