# 片刻帮 · 接口文档

统一入口 **`http://localhost:8080`**（网关）  
客户端只走 `/api/**`，不要直连各微服务端口。

> 推荐在浏览器打开 [**可视化预览**](./preview.html)（更清晰）。  
> Cursor 内预览：`Ctrl + Shift + P` → 输入 `Markdown: Open Preview`

---

## 文档目录

| # | 文档 | 覆盖范围 |
|:---:|:---|:---|
| 00 | [网关与约定](./00-gateway.md) | 鉴权、路由、JWT 白名单 |
| 01 | [认证与用户](./01-auth.md) | `/api/auth` |
| 02 | [任务大厅](./02-tasks.md) | `/api/tasks` |
| 03 | [钱包与支付](./03-wallet-payments.md) | `/api/wallet` |
| 04 | [优惠与活动](./04-growth.md) | `/api/coupons` · `/api/hall-activities` |
| 05 | [聊天与实时](./05-chat-realtime.md) | `/api/chat` · `/ws` |
| 06 | [客服与支持](./06-support.md) | `/api/support` |
| 07 | [公告消息反馈](./07-comms.md) | notices · system-messages · feedback |
| 08 | [管理后台](./08-admin.md) | `/api/admin` |
| 09 | [内部 Feign](./09-internal-feign.md) | `/internal/**`（不对前端） |

---

## 按客户端速查

| 客户端 | 建议阅读 |
|:---|:---|
| 用户端 / 小程序 | 00 → 07 |
| 骑手台 | 00 · 01 · 02 · 03 · 05 |
| 客服台 | 00 · 01 · 05 · 06 |
| 管理后台 | 00 · 08（辅以 07） |
| 后端联调 | 00 · 09 |

---

## 说明

- 依据当前 `campus-cloud` Controller 整理；请求字段以 DTO 为准
- 响应多为 `Map` / 实体，以实际返回为准
- `/internal/**` 经网关访问会被拒绝，仅服务间调用
