# 片刻帮 Agent：推荐 Tool / MCP 清单

> 维护说明：这是规划备忘，不是运行时配置。  
> 写码前以本文 + `tools/README.md` + `rag/knowledge/` 为准。  
> 更新时间：2026-09-11

---

## 现状（不要忘记）

| 项 | 现状 |
|----|------|
| RAG | `agent/rag/knowledge/01～10.md`；运行时同步到 `campus-audit-service/.../rag/` |
| 检索 | **可选向量**（`agent/rag/vector` + `app.rag.vector`）；关闭/失败则关键词 `RagKnowledgeStore` |
| 已有 Tool | 只读 11 个：钱包、任务列表/详情/**时间线**、提现、信誉、认证、申诉、优惠券、加急配额、流水 |
| 意图 | **LLM function calling 优先**；失败回落关键词 `detectIntent` |
| MCP | **生产未使用**；不要把测试里的天气/航班/时间 MCP 原样接到客服 |
| 安全 | username/userId 只来自 `AuthContext`；客服工具一律只读优先 |

---

## 一、优先补的业务 Tool（客服刚需）

继续 **只读**；实现可先做 Java Feign internal API，再视需要包一层 MCP。

| 优先级 | Tool 建议名 | 典型问法 | 说明 |
|--------|-------------|----------|------|
| P0 | `get_wallet` | 我的钱包 / 余额 | **已实现** |
| P0 | `list_my_tasks` | 我的任务 / 历史任务 | **已实现** |
| P0 | `get_task_by_id` | 任务 123 到哪了 | **已实现**（`tool:task_detail`） |
| P0 | `list_withdrawals` | 提现审到哪了 | **已实现**（`tool:withdrawals`） |
| P0 | `get_credit_score` | 信誉多少 / 为什么接不了单 | **已实现**（`tool:credit`） |
| P0 | `get_rider_cert_status` | 认证过了吗 | **已实现**（`tool:rider_cert`） |
| P1 | `get_appeal_status` | 申诉处理了吗 | **已实现**（`tool:appeals`） |
| P1 | `list_my_coupons` | 我有哪些券 | **已实现**（`tool:coupons`） |
| P1 | `get_urgent_quota` | 本周还能加急几次 | **已实现**（`tool:urgent_quota`） |
| P2 | `get_wallet_transactions` | 最近流水 / 这笔钱哪来的 | **已实现**（`tool:transactions`） |
| P2 | `get_task_timeline` | 自动确认还剩多久 / 是否超时 | **已实现**（`tool:task_timeline`） |

### 明确不做（或很久以后才考虑）

- 写操作：代用户取消任务、提交提现、改状态、发申诉（应引导点页面或转人工）
- 查他人数据、解析聊天文本里的「帮我查 xxx 的钱包」
- 联网搜索、天气、航班等与校园客服无关的工具

---

## 二、MCP 怎么加才「适配」

### 推荐做法

**自建「片刻帮只读 MCP Server」**，把上一节 Tool 用 MCP 暴露出来。

用途：

- Cursor / Python Agent / 多端共用同一套只读能力
- 与现有 `SupportAgentToolService` 共用后端 internal API，避免两套逻辑

建议暴露的 MCP tools（与上表对齐）：

1. `get_wallet`
2. `list_my_tasks`
3. `get_task_by_id`
4. `list_withdrawals`
5. `get_credit_score`
6. `get_rider_cert_status`
7. `get_appeal_status`
8. `list_my_coupons`
9. `get_urgent_quota`
10. （可选）`search_knowledge` —— 调 RAG/向量检索

鉴权原则：

- 生产客服入口：身份只来自登录态 / 网关，不信任模型传入的 username
- 开发用 MCP：可用显式 token + 严格只读；禁止写库

### 不推荐接到用户客服运行时的第三方 MCP

| 类型 | 原因 |
|------|------|
| 天气 / 航班 / 开方等 demo | 与业务无关 |
| 通用联网搜索 | 易脱离知识库乱答 |
| MySQL / 任意 DB MCP | 用户对话场景风险过大 |
| 时间 MCP | 收益极低；时限问题应查任务接口 |
| 浏览器操控 MCP | 属开发测试，不是客服产品能力 |

### 可选、非刚需

| 类型 | 何时考虑 |
|------|----------|
| 飞书 / Notion FAQ MCP | 若运营主要在外部文档维护 FAQ，再同步或检索 |
| 地图 MCP | 以后要做校内取送路线说明时再加 |
| 文档/GitHub MCP | 仅用于 **Cursor 开发**，不进片刻小新用户链路 |

---

## 三、建议落地顺序（以后接着做）

1. ~~**补 P0 业务 Tool**（任务详情、提现进度、信誉、认证）~~ ✅  
2. ~~**补 P1 + 流水**（申诉、优惠券、加急、流水）~~ ✅  
3. ~~**向量检索旁路**（`agent/rag/vector` + Java 混合检索）~~ ✅ 默认开启，不可用时降级关键词  
4. ~~**意图**：关键词 → LLM function calling~~ ✅（`AdminAiService.tryLlmToolAgent`）  
5. ~~**LangGraph 接入生产主链路**~~ ✅（`agent/graph` + `app.agent.graph`，失败回落 Java）  
6. **再**把已稳定的只读 Tool 包成「片刻帮 MCP」  
7. ~~P2：`get_task_timeline`~~ ✅

---

## 四、相关路径速查

| 路径 | 内容 |
|------|------|
| `agent/rag/knowledge/` | 知识库权威源（含 `00-知识库数据范围.md` 规划，勿进检索同步） |
| `agent/rag/prompts/system.md` | 客服系统提示 |
| `agent/rag/eval/cases.json` | 回归问句 |
| `agent/tools/README.md` | 现有只读工具说明 |
| `agent/README.md` | Agent 总览 |
| 运行时 | `campus-audit-service`：`SupportAgentToolService`、`RagKnowledgeStore`、`AdminAiService` |

---

## 五、一句话备忘

> 客服要的是「校园业务只读 Tool」；MCP 优先自建包装这些 Tool，不要为了用 MCP 而堆第三方无关服务。
