# 片刻帮 · LangGraph 客服编排（接入生产主链路）

Java 客服 `POST /api/support/chat` 优先调用本服务；失败自动回落现有 Java Agent（工具 + RAG）。

## 图结构

```text
START → route → tools → retrieve → generate → END
```

| 节点 | 作用 |
|------|------|
| route | 规则 + LLM 决定调哪些工具、是否 RAG |
| tools | 回调 Java `POST /internal/support/run-tools`（带 username） |
| retrieve | 调已有向量服务 `8765/api/retrieve` |
| generate | DeepSeek 综合回答 |

## 一次启用

```powershell
cd agent\graph
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
copy .env.example .env
# 编辑 .env：填 DEEPSEEK_API_KEY；JAVA_TOOLS_URL / RAG_VECTOR_URL 一般默认即可

python server.py
```

健康检查：http://127.0.0.1:8766/health

## Java 配置

`application-campus-shared.yml`：

```yaml
app:
  agent:
    graph:
      enabled: true   # 或 CAMPUS_AGENT_GRAPH_ENABLED=false 关闭
      base-url: http://127.0.0.1:8766
      timeout-ms: 120000
      internal-token:   # 可选，与 .env CAMPUS_AGENT_GRAPH_TOKEN 一致
```

## 启动顺序（本地）

1. 微服务（至少 audit `8104`、wallet、task、gateway）
2. `agent/rag/vector` → `python retrieve_server.py`（8765）
3. `agent/graph` → `python server.py`（8766）
4. 前端做客服问答

响应里 `source=langgraph` 且可有 `trace` 字段，便于确认走了 Graph。

关闭 Graph：`CAMPUS_AGENT_GRAPH_ENABLED=false` 后重启 audit-service，即回到纯 Java Agent。
