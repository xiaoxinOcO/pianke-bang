# 片刻帮 RAG 智能客服

面向「片刻小新」：`POST /api/support/chat` → 检索知识库 → DeepSeek 生成。

## 目录

```text
agent/rag/
  knowledge/     # 知识文档（权威源）
  prompts/       # 系统提示词
  eval/          # 回归问句
  vector/        # Python 向量入库 + 检索服务（可选）
  README.md
```

运行时副本在：
`校园代拿/.../campus-audit-service/src/main/resources/rag/`  
（改知识库后请同步复制到该目录）

## 流程

1. 用户提问（前端片刻小新）
2. **工具命中** → 只读查个人数据（不走 RAG）
3. 否则检索：
   - `app.rag.vector.enabled=true` 且 Python 服务可用 → **向量检索**
   - 否则 / 失败 → **关键词检索**（`RagKnowledgeStore`）
4. 组装上下文 + `prompts/system.md`
5. 调用 DeepSeek（可选）；无 Key 时返回知识片段摘要

## 启用 DeepSeek

1. 设置 `DEEPSEEK_API_KEY`
2. `app.llm.enabled=true`
3. 重启 `campus-audit-service`

## 启用向量检索（可选）

详见 `vector/README.md`。摘要：

```powershell
cd agent/rag/vector
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
copy .env.example .env   # 填 DASHSCOPE_API_KEY
python ingest_knowledge.py
python retrieve_server.py
```

Java 侧（默认已开启；不配也可）：

```yaml
app:
  rag:
    vector:
      enabled: true          # 默认 true；关：CAMPUS_RAG_VECTOR_ENABLED=false
      base-url: http://127.0.0.1:8765
      timeout-ms: 3000
```

向量服务挂了会自动回退关键词，客服仍可用。

## 同步知识库到 classpath

把 `agent/rag/knowledge/*.md`（排除 `00-*`）与 `prompts/system.md` 复制到：

`campus-cloud/campus-audit-service/src/main/resources/rag/knowledge/`  
`campus-cloud/campus-audit-service/src/main/resources/rag/prompts/`

若开了向量：改完 md 后还要再跑 `python ingest_knowledge.py`。
