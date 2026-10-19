# Agent / RAG（片刻帮）

智能客服「片刻小新」主链路：

**前端 → Java `/api/support/chat` →（优先）LangGraph → 失败回落 Java Agent（工具 + RAG + DeepSeek）**

## 结构

| 路径 | 说明 |
|------|------|
| `graph/` | **LangGraph 编排服务**（生产接入，默认 8766） |
| `rag/knowledge/` | 片刻帮业务知识（权威源） |
| `rag/vector/` | Chroma 向量入库 + 检索服务（8765） |
| `rag/prompts/system.md` | RAG 系统提示词 |
| `rag/eval/cases.json` | 回归问句 |
| `tools/README.md` | 只读工具说明 |
| `TOOL-MCP-清单.md` | Tool / MCP 规划备忘 |

运行时知识副本：`campus-audit-service/src/main/resources/rag/`

## 链路

1. `SmartSupportChat` → `POST /api/support/chat`
2. 若 `app.agent.graph.enabled=true` → Python LangGraph（`route→tools→retrieve→generate`）
3. Graph 回调 Java `/internal/support/run-tools` 查钱包/任务等；检索走 `8765`
4. Graph 不可用 → 现有 Java：LLM 选工具 / 关键词工具 / RAG

详见：`graph/README.md`、`rag/README.md`、`tools/README.md`

## 本地最小启动

1. 微服务（audit / wallet / task / gateway）
2. `agent/rag/vector`：`python retrieve_server.py`
3. `agent/graph`：配好 `.env` 后 `python server.py`
4. 前端提问；响应 `source` 为 `langgraph` 即走了 Graph
