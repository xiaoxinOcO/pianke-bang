# 片刻帮 · 向量检索（Python）

Java 客服主链路不变；本目录只负责 **embedding + Chroma 相似度检索**。  
生成回答仍由 `campus-audit-service` + DeepSeek 完成。

## 目录

```text
agent/rag/vector/
  ingest_knowledge.py   # 把 ../knowledge/*.md 写入 chroma_db
  retrieve_server.py    # FastAPI：POST /api/retrieve
  requirements.txt
  .env.example
  chroma_db/            # 运行 ingest 后生成（勿提交密钥）
```

## 一次启用

```powershell
cd agent/rag/vector
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
copy .env.example .env
# 编辑 .env，填入 DASHSCOPE_API_KEY

python ingest_knowledge.py
python retrieve_server.py
```

健康检查：`http://127.0.0.1:8765/health`  
检索：`POST http://127.0.0.1:8765/api/retrieve`  
body：`{"query":"怎么提现","top_k":3}`

## 与 Java 对接

`campus-audit-service` 配置（默认开启向量；失败或未启 Python 时自动关键词兜底）：

```yaml
app:
  rag:
    vector:
      enabled: true   # 或环境变量 CAMPUS_RAG_VECTOR_ENABLED=false 关闭
      base-url: http://127.0.0.1:8765
      timeout-ms: 3000
```

`RagKnowledgeStore` 会先调本服务；失败或无结果时回退关键词检索。

## 更新知识库后

1. 改 `agent/rag/knowledge/*.md`
2. 同步到 audit-service classpath（原流程）
3. 再跑一次 `python ingest_knowledge.py`
4. 重启 `retrieve_server.py`（或无需重启，Chroma 已落盘；为保险可重启）
