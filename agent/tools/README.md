# Agent 只读工具

片刻小新在 RAG 之外，可调用 **只读工具** 查当前登录用户数据（更像 Agent）。

## 工具一览

| 工具 | source | 触发示例 | 后端调用 | 说明 |
|------|--------|----------|----------|------|
| 查钱包 | `tool:wallet` | 「我的钱包」「余额多少」 | `GET /internal/wallet/agent-snapshot` | 余额 / 累计收入 / 已提现 / 提现中 |
| 查任务列表 | `tool:tasks` | 「我的任务」「历史任务」 | `GET /internal/tasks/agent-summary` | 默认进行中，最多 5 条 |
| 查任务详情 | `tool:task_detail` | 「任务 12 到哪了」 | `GET /internal/tasks/agent-detail` | 仅发布者/接单员；可从问句解析任务 ID |
| 查任务时间线 | `tool:task_timeline` | 「任务 12 还要多久自动确认」 | `GET /internal/tasks/agent-timeline` | 自动确认倒计时 / 是否超时 |
| 查提现 | `tool:withdrawals` | 「提现审到哪了」 | `GET /internal/wallet/agent-withdrawals` | 最近申请进度（账号脱敏） |
| 查信誉 | `tool:credit` | 「信誉多少」「为什么接不了单」 | audit 本地 `UserAccount` | 分数 / 是否禁接 / 能否接单 |
| 查接单员认证 | `tool:rider_cert` | 「认证过了吗」 | audit 本地 `UserAccount` | 已通过 / 审核中 / 未认证 |
| 查申诉 | `tool:appeals` | 「申诉处理了吗」「我的申诉」 | `GET /internal/tasks/agent-appeals` | 已裁决申诉摘要 |
| 查优惠券 | `tool:coupons` | 「我有哪些券」「还有券吗」 | `GET /internal/wallet/agent-coupons` | 可用优先，含张数统计 |
| 查加急配额 | `tool:urgent_quota` | 「本周还能加急几次」 | `GET /internal/tasks/agent-urgent-quota` | 本周已用/剩余 |
| 查流水 | `tool:transactions` | 「我的流水」「最近流水」 | `GET /internal/wallet/agent-transactions` | 最近收支明细 |

规则类问题（「怎么提现」「如何发布」）仍可走 RAG；LLM 开启时可由模型同时选 `search_knowledge` + 数据工具。

### 任务 scope（列表工具）

| 问法 | scope | 说明 |
|------|-------|------|
| 我的任务 | `active` | 进行中（不含已取消/已完成） |
| 待接单的任务 | `open` | 仅 OPEN |
| 配送中 / 进行中 | `in_progress` | 配送中、逾期、待确认 |
| 历史任务 | `history` | 已完成 / 已取消 / 已驳回 |

## 意图路由

1. **优先**：DeepSeek function calling（`AdminAiService.tryLlmToolAgent`）在「查数据 / 查知识」间自选工具  
2. **兜底**：关键词 `detectIntent`（LLM 未开或选工具失败时）

## 安全

- **username / userId 只来自网关注入的 `AuthContext`**，不从聊天文本解析用户身份。
- 任务编号可从问句解析（仅 ID）。
- 仅读接口；无改余额、无改任务状态；提现账号脱敏。
- 客服入口本身要求已登录（`CustomerSupportController`）。

## 本地验证

1. 先安装并重启：`campus-common` → `task-service`、`wallet-service`、`audit-service`
2. 登录用户端，打开片刻小新（管理员 AI / DeepSeek 需开启）
3. 试：
   - 「我的钱包」「我的任务」「任务 12 到哪了」「任务 12 还要多久自动确认」
   - 「我钱包里的钱怎么才能拿出来」（应同时查数据 + 提现规则）
   - 「提现审到哪了」「我信誉多少」「认证过了吗」
4. 对比：纯规则「怎么提现」仍可走知识库说明

规划备忘见：`agent/TOOL-MCP-清单.md`
