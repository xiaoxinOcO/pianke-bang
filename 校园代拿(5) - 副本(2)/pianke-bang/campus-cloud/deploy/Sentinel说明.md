# Sentinel 接入说明（校园代拿 campus-cloud）

## 1. 启动顺序

1. MySQL、Nacos（8848）
2. **Sentinel Dashboard**（8858）
3. 各微服务 + Gateway（8080）
4. 前端

Dashboard 启动（PowerShell 需加引号）：

```powershell
java "-Dserver.port=8858" "-Dcsp.sentinel.dashboard.server=127.0.0.1:8858" -jar "sentinel-dashboard\target\sentinel-dashboard.jar"
```

## 2. Sentinel 控制台

| 项目 | 值 |
|------|-----|
| 地址 | http://127.0.0.1:8858 |
| 账号 | sentinel |
| 密码 | sentinel |

## 3. 已接入的服务

| 服务 | 应用名 | Sentinel 客户端端口 |
|------|--------|---------------------|
| Gateway | campus-gateway | 8719 |
| Auth | campus-auth-service | 8720 |
| User | campus-user-service | 8721 |
| Task | campus-task-service | 8722 |
| Audit | campus-audit-service | 8723 |
| Chat | campus-chat-service | 8724 |
| Wallet | campus-wallet-service | 8725 |

## 4. 已实现的代码级熔断（Fallback）

已在 `campus-common` 开启 `feign.sentinel.enabled=true`，并为以下 Feign 客户端加了降级实现：

| 步骤 | 客户端 | Fallback 类 | 效果 |
|------|--------|-------------|------|
| 1 | `AuditLogClient` | `AuditLogClientFallback` | 日志写失败不影响发任务/接单 |
| 2 | `RealtimePushClient` | `RealtimePushClientFallback` | 推送失败不影响任务流程 |
| 3 | `AdminAiClient` | `AdminAiClientFallback` | AI 不可用时返回 error，不自动驳回任务 |

**还需在控制台配置「降级规则」**，Sentinel 才会真正触发熔断；仅代码 fallback 会在 Feign 调用异常/熔断时生效。

---

## 5. 控制台配置降级规则（一步一步来）

> 每配一条规则前：先重启对应微服务，在控制台 **机器列表** 里确认应用已上线。

### 第 1 步：审计日志熔断（campus-task-service 等）

1. 控制台 → **降级规则** → 应用选 `campus-task-service`（user/wallet 服务同理）
2. 点击 **新增降级规则**
3. 填写：

| 字段 | 值 |
|------|-----|
| 资源名 | `POST:/internal/audit/log` |
| 降级策略 | 异常比例 |
| 比例阈值 | 0.5 |
| 时间窗口 | 10（秒） |
| 最小请求数 | 5 |

4. 保存后，**停止 audit-service**，在 task 服务里执行会写日志的操作（发任务、接单等）
5. 预期：主业务成功，日志里出现 `审计日志服务不可用，已跳过写入`

**如何查资源名：** 控制台 → **簇点链路** → 选应用 → 找到 Feign 对应 URL。

---

### 第 2 步：实时推送熔断（campus-task-service）

| 字段 | 值 |
|------|-----|
| 应用 | `campus-task-service` |
| 资源名 | `POST:/internal/realtime/push` |
| 降级策略 | 异常比例 |
| 比例阈值 | 0.5 |
| 时间窗口 | 10 秒 |

测试：停止 `campus-chat-service`，触发任务状态变更（会推送通知），任务仍应成功。

---

### 第 3 步：AI 审核慢调用熔断（campus-task-service）

| 字段 | 值 |
|------|-----|
| 应用 | `campus-task-service` |
| 资源名 | `POST:/internal/ai/audit-task` |
| 降级策略 | 慢调用比例 |
| 最大 RT | 3000（毫秒） |
| 比例阈值 | 0.6 |
| 时间窗口 | 60 秒 |

也可在 **campus-audit-service** 上对 Ollama 相关接口配慢调用降级（资源名在簇点链路里查看）。

测试：关闭 Ollama 或把 timeout 设很短，触发自动 AI 审核，任务应**保持待审核**，不会被误驳回。

---

### 第 4 步：网关路由降级（campus-gateway）

1. 控制台 → **网关降级规则**
2. 示例：

| 资源名（路由 id） | 策略 | 阈值 | 熔断时长 |
|------------------|------|------|----------|
| `campus-chat-service-api` | 异常比例 | 0.3 | 20s |
| `campus-wallet-service-api` | 异常比例 | 0.3 | 20s |

停止 chat 或 wallet 服务后，经网关访问对应 API 应快速返回 429 JSON。

---

## 6. 限流 vs 熔断

| 类型 | 控制台菜单 | 场景 |
|------|-----------|------|
| 限流 | 流控规则 / 网关流控规则 | 防刷、防高并发 |
| 熔断 | 降级规则 / 网关降级规则 | 下游挂了或太慢，暂时不再调用 |

被限流/熔断时 HTTP 429，body：

```json
{"success":false,"message":"请求过于频繁，请稍后再试"}
```

## 7. Nacos 规则持久化（已接入）

规则默认存在 **Nacos 配置中心**，不再只存在 Dashboard 内存里。重启 Sentinel Dashboard 后，规则仍保留。

### 7.1 dataId 约定（分组均为 `SENTINEL_GROUP`）

| 应用 | 流控 dataId | 熔断 dataId | 网关流控 dataId |
|------|-------------|-------------|-----------------|
| campus-task-service | `campus-task-service-flow-rules` | `campus-task-service-degrade-rules` | — |
| campus-gateway | `campus-gateway-flow-rules` | `campus-gateway-degrade-rules` | `campus-gateway-gw-flow-rules` |
| 其他服务 | `{应用名}-flow-rules` | `{应用名}-degrade-rules` | — |

在 Nacos 控制台 → **配置管理** → 分组选 `SENTINEL_GROUP` 可查看、编辑 JSON。

### 7.2 首次初始化规则（推荐）

先启动 Nacos，再执行：

```powershell
cd campus-cloud\deploy
.\push-sentinel-rules-to-nacos.ps1
```

脚本会推送：
- 各服务空流控规则 `[]`
- `campus-task-service` 三条 Feign 熔断（审计 / 推送 / AI）
- `campus-gateway` 两条路由熔断（chat / wallet）

### 7.3 启动 Dashboard（Nacos 已写入源码配置）

已修改本地 Sentinel 1.8 源码：

- `微服务工具\Sentinel-1.8\Sentinel-1.8\sentinel-dashboard\src\main\resources\application.properties`
  - `nacos.server.addr=127.0.0.1:8848`
- 流控 / 熔断规则通过 Nacos 读写（不再仅存内存）

**首次或修改源码后需重新打包 Dashboard：**

```powershell
cd 微服务工具\Sentinel-1.8\Sentinel-1.8
mvn -pl sentinel-dashboard -am clean package -DskipTests
```

然后 `deploy\启动Sentinel.bat` 或：

```powershell
java "-Dserver.port=8858" "-Dcsp.sentinel.dashboard.server=127.0.0.1:8858" -jar "sentinel-dashboard\target\sentinel-dashboard.jar"
```

在控制台 **新增/修改** 流控或熔断规则后，会自动写入 Nacos；微服务监听 Nacos 变更会热更新。

### 7.4 工作流程

1. Nacos 有规则 → 微服务启动时自动加载  
2. Dashboard 改规则 → 写入 Nacos → 微服务热更新  
3. 重启 Dashboard → 从 Nacos 读规则，不丢失  
4. 重启微服务 → 再从 Nacos 加载  

### 7.5 机器列表「端口号」变化说明

列表里的端口是 Sentinel **客户端通信端口**（如 task 为 8722），不是业务端口 8103。若 8722 被旧进程占用，可能临时出现 8726 等失联记录，点 **移除** 即可；与 Nacos 规则无关。

### 7.6 熔断规则又没了？（常见原因）

| 现象 | 原因 | 处理 |
|------|------|------|
| 重启 Dashboard 后规则空 | 规则从未写入 Nacos，只在旧 Dashboard 内存里 | 执行 `push-sentinel-rules-to-nacos.ps1` 恢复 |
| 推送脚本报 404 | **Nacos 3.x** 已弃用 `v1/cs/configs`，需用 v3 API | 已修复脚本，重新执行即可 |
| Nacos 里也没有 | 没跑过推送脚本，且 Dashboard 未成功写入 | Nacos 控制台手动新建配置，或跑脚本 |

**恢复步骤（推荐）：**

1. 确认 Nacos（8848）已启动  
2. `cd campus-cloud\deploy` → `.\push-sentinel-rules-to-nacos.ps1`  
3. Nacos 控制台 → 配置管理 → 分组 `SENTINEL_GROUP` → 应能看到 `campus-task-service-degrade-rules`  
4. **重启** `campus-task-service`（及 gateway 等）让规则加载  

之后在 Dashboard 里改规则，会写入 Nacos；以后重启 Dashboard **不必再手动画一遍**（前提是 Nacos 里已有规则）。

## 8. 配置文件

- 公共 Sentinel + Nacos datasource：`campus-common/src/main/resources/application-sentinel.yml`
- 网关 gw-flow datasource：`campus-gateway/src/main/resources/application.yml`
- 规则 JSON 样例：`deploy/sentinel-nacos-rules/`
- 一键推送脚本：`deploy/push-sentinel-rules-to-nacos.ps1`
