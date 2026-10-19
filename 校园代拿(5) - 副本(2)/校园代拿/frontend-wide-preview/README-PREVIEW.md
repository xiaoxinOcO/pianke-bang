# 片刻帮 · 加宽布局预览版

从 `frontend` 完整复制，**不改动原版**。仅用于对比左右留白。

## 与原版差异

| 项 | 原版 `frontend` | 本预览 `frontend-wide-preview` |
| --- | --- | --- |
| 内容区宽度 | `1180px` 居中 | **居中定宽** `1480px`（大屏可到 `1560px`，非满宽） |
| 左右边距 | 偏多 | 比原版少一截，比满宽多呼吸感 |
| 开发端口 | `5173`（默认） | `5174` |

## 启动

```bash
cd frontend-wide-preview
npm install
npm run dev
```

浏览器打开：http://localhost:5174/

原版仍在 `frontend`，互不影响。看完若满意，再决定是否把同样改动合回原版。
