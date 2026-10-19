# 片刻帮 · 微信小程序

本目录为**独立新建**的微信小程序前端，与现有 `frontend`（Vue 网页）和 `campus-cloud`（后端）完全隔离，**不会修改你原有项目代码**。

## 目录结构

```
miniprogram/
├── app.js / app.json / app.wxss   # 小程序入口
├── config/index.js                # API 地址配置
├── utils/                         # 工具（HTTP、鉴权、格式化）
├── services/                      # 后端 API 封装
├── components/task-card/          # 任务卡片组件
├── custom-tab-bar/                # 底部导航
└── pages/                         # 页面
    ├── hall/          任务大厅（首页）
    ├── publish/       发布任务
    ├── chat/          消息列表
    ├── profile/       个人中心
    ├── login/         登录/注册
    ├── task-detail/   任务详情
    ├── mine/          我的任务
    ├── wallet/        我的钱包
    ├── chat-room/     聊天详情
    ├── notices/       平台公告
    ├── feedback/      意见反馈
    └── rider-apply/   申请接单员
```

## 如何运行

### 1. 启动后端（与网页共用，不影响网页）

确保 `campus-cloud` 网关已在 **8080** 端口运行（与现有 Vue 前端相同）。

### 2. 用微信开发者工具打开

1. 下载安装 [微信开发者工具](https://developers.weixin.qq.com/miniprogram/dev/devtools/download.html)
2. 选择「导入项目」
3. 目录选择本文件夹：`校园代拿/miniprogram`
4. AppID 可先用测试号 `touristappid`，正式上线需替换 `project.config.json` 中的 `appid`

### 3. 开发环境配置

在微信开发者工具中：

- **详情 → 本地设置 → 勾选「不校验合法域名、web-view、TLS 版本以及 HTTPS 证书」**
- 这样开发时才能请求 `http://localhost:8080`

修改 API 地址：编辑 `config/index.js`

```js
const ENV = 'develop'  // 开发环境
// develop: http://localhost:8080
// release: https://你的域名
```

## 已实现功能

| 功能 | 对接 API |
|------|----------|
| 密码/短信登录注册 | `/api/auth/*` |
| 微信一键登录（授权手机号） | `POST /api/auth/wechat/mp/login` |
| 任务大厅浏览/搜索/筛选 | `GET /api/tasks` |
| 发布任务（钱包/支付宝） | `POST /api/tasks` |
| 接单/完成/确认/取消 | `/api/tasks/{id}/*` |
| 我的任务 | `GET /api/tasks/mine` |
| 钱包余额/流水/提现 | `/api/wallet/*` |
| 聊天 | `/api/chat/*` |
| 平台公告 | `GET /api/notices` |
| 意见反馈 | `POST /api/feedback` |
| 接单员申请 | `POST /api/auth/rider/apply` |

## 注意事项

1. **微信支付**：后端 `WECHAT` 支付方式尚未实现，小程序发布任务时请选「钱包余额」或「支付宝（沙箱）」
2. **生产部署**：需 HTTPS 域名，并在微信公众平台配置 request 合法域名
3. **WebSocket 实时聊天**：当前聊天页使用 HTTP 发送/刷新，后续可接入 `wss://域名/ws?token=xxx`
4. **微信一键登录**：服务器需配置环境变量 `WECHAT_MP_APP_SECRET`（小程序 AppSecret）；并在微信公众平台开通「手机号快速验证」能力。手机号须已在片刻帮注册。
5. **网页扫码登录**：未配置开放平台网站应用时，会降级为「手机扫码 + 短信确认」；配置 `WECHAT_WEB_APP_ID` / `WECHAT_WEB_APP_SECRET` 后可走官方微信扫码。

## 下一步

- 微信支付（任务赏金/充值）
- 订阅消息（任务状态变更通知）
