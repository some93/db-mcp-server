# MCP HTTP + SSE 设计方案

## 1. 目标

在当前项目已支持 `stdio MCP` 和 `HTTP 调试接口` 的基础上，新增 `HTTP + SSE` 模式的 MCP 服务能力，并保持三种传输并存：

- `stdio`
- `HTTP`
- `HTTP + SSE`

设计约束：

- 不破坏现有 `stdio` 行为。
- 不替换现有 `/api/*` 调试接口。
- 第一阶段 `tools/call` 继续同步返回。
- `SSE` 第一阶段只提供事件通道与会话能力，不强制所有工具流式输出。
- 后续可平滑升级到“长任务走 SSE 流式结果”。

---

## 2. 当前现状

### 2.1 已有能力

当前项目已具备：

- `McpStdioServer`
  - 手写 JSON-RPC 2.0
  - 已支持 `initialize` / `tools/list` / `tools/call`
- `McpHttpController`
  - 本机调试接口
  - 与业务 service 语义一致，但不是 MCP 协议接口
- 完整业务 service 层
  - metadata / execution / cache / status
- 已有统一返回结构
  - `McpResponse`

### 2.2 当前缺失

当前项目没有：

- MCP over HTTP 协议入口
- SSE 会话管理
- 可复用的 MCP 协议核心层
- 面向多 transport 的统一协议模型

---

## 3. 总体方案

### 3.1 传输层并存策略

项目保留三类入口：

1. `stdio MCP`
2. `HTTP MCP`
3. `HTTP 调试接口`

职责拆分：

- `stdio MCP`
  - 面向 Claude Desktop / 本地进程拉起场景
- `HTTP MCP`
  - 面向远程或本地 HTTP 客户端
  - 通过 `POST /mcp` 收请求
  - 通过 `GET /mcp/sse` 建立事件流
- `HTTP 调试接口`
  - 继续保留 `/api/*`
  - 仅用于开发调试
  - 不承载 MCP 协议

### 3.2 第一阶段行为

第一阶段采用“同步调用 + SSE 通道预留”模式：

- `POST /mcp`
  - 接收 MCP JSON-RPC 请求
  - `initialize` / `tools/list` / `tools/call` 同步返回
- `GET /mcp/sse`
  - 建立 SSE 通道
  - 返回 `sessionId`
  - 用于服务端事件推送、后续扩展异步结果

说明：

- 第一阶段不要求工具本身流式输出。
- 第二阶段已补最小可用异步能力：`tools/call` 支持 `async=true`，受理后通过 SSE 回传 `tool_progress` / `tool_result`。

---

## 4. 对外接口设计

### 4.1 MCP HTTP 协议入口

#### `POST /mcp`

用途：

- 接收 MCP JSON-RPC 请求
- 支持方法：
  - `initialize`
  - `tools/list`
  - `tools/call`

请求头建议：

- `Content-Type: application/json`
- 可选：`X-Mcp-Session-Id`

返回：

- 标准 JSON-RPC result / error

说明：

- 第一阶段 `tools/call` 的工具结果直接同步返回。
- 若后续引入异步工具，再扩展为返回受理信息并通过 SSE 下发结果。

#### `GET /mcp/sse`

用途：

- 建立服务端事件流
- 创建或恢复 MCP HTTP 会话

响应：

- `Content-Type: text/event-stream`
- 建立成功后立即推送初始化事件

建议首个事件：

```text
event: session
data: {"sessionId":"...","server":"db-mcp-server","version":"1.0.0"}
```

说明：

- 客户端后续通过 `X-Mcp-Session-Id` 关联同一会话。
- 第一阶段先支持：
  - session 建立通知
  - server event 推送
  - future async 预留

### 4.2 原有接口保留

以下接口保持不变：

- `GET /health`
- `GET /ready`
- `GET /api/*`
- `POST /api/*`

其中 `/api/*` 继续作为调试接口，不转为 MCP 协议接口。

---

## 5. 模块设计

### 5.1 新增协议核心层

建议新增目录：

```text
src/main/java/com/mcp/transport/mcp/core/
```

核心类：

#### `McpProtocolService`

职责：

- 统一处理 MCP 请求
- 不关心底层 transport 是 `stdio` 还是 `http`
- 负责：
  - `initialize`
  - `tools/list`
  - `tools/call`
  - JSON-RPC result/error 组装

#### `McpToolDispatcher`

职责：

- 承接现有 `McpStdioServer.dispatchTool(...)`
- 将 toolName 分发到现有 application service
- 返回 `McpResponse`

#### `McpToolCatalog`

职责：

- 承接现有 `buildToolsList()`
- 输出 tools schema
- 保证 stdio / HTTP 看到完全一致的工具清单

#### `McpInitializeService`

职责：

- 输出 initialize result
- 管理协议版本、capabilities、serverInfo

### 5.2 新增 HTTP MCP 传输层

建议新增：

```text
src/main/java/com/mcp/transport/http/McpTransportController.java
```

职责：

- 处理 `POST /mcp`
- 处理 `GET /mcp/sse`
- 将协议处理委托给 `McpProtocolService`
- 将 SSE 连接交给 `SseSessionManager`

### 5.3 新增 SSE 会话管理

建议新增目录：

```text
src/main/java/com/mcp/transport/http/sse/
```

核心类：

#### `SseSessionManager`

职责：

- 创建 session
- 保存 `SseEmitter`
- 按 `sessionId` 发送事件
- 心跳保活
- 清理超时/断开连接

#### `SseClientSession`

字段建议：

- `sessionId`
- `createdAt`
- `lastSeenAt`
- `SseEmitter emitter`
- `clientInfo`
- 可选 `selectedDatasource`

---

## 6. 类改造方案

### 6.1 `McpStdioServer`

当前问题：

- 协议处理、工具分发、工具描述都写在一个类里
- 无法被 HTTP transport 复用

改造目标：

- `McpStdioServer` 只负责：
  - 读取 stdin
  - 调用 `McpProtocolService`
  - 输出 stdout

从 `McpStdioServer` 中抽离：

- `dispatchTool`
- `buildToolsList`
- `buildInitializeResult`
- JSON-RPC 包装逻辑

### 6.2 `McpHttpController`

当前类保留，不承载 MCP 协议。

职责继续保持：

- 本机回环调试
- `/api/*` 直连业务 service

### 6.3 `AppConfig` / `HttpConfig` / `McpConfig`

需要扩展为 transport 可配置模型。

---

## 7. 配置设计

建议新增配置结构：

```yaml
mcp:
  stdio:
    enabled: true
    exitOnDisconnect: true
  http:
    enabled: true
    path: /mcp
    ssePath: /mcp/sse
    sessionTimeoutSeconds: 1800
    heartbeatSeconds: 15
```

说明：

- `stdio.enabled`
  - 是否启用 stdio MCP
- `stdio.exitOnDisconnect`
  - 与当前配置兼容
- `http.enabled`
  - 是否启用 MCP over HTTP
- `http.path`
  - MCP 请求入口
- `http.ssePath`
  - SSE 通道入口
- `sessionTimeoutSeconds`
  - 会话超时清理
- `heartbeatSeconds`
  - SSE 心跳频率
- `path` / `ssePath`
  - 当前实现已生效，可分别覆盖 HTTP MCP 请求入口与 SSE 事件流入口
  - 未配置时默认回退到 `POST /mcp`、`GET /mcp/sse`

兼容策略：

- 保留现有 `http.host` / `http.port`
- 保留现有 `mcp.exitOnDisconnect`
- 实现阶段允许新旧配置同时兼容

建议兼容规则：

- 若 `mcp.stdio.exitOnDisconnect` 未配置，则回退到旧字段 `mcp.exitOnDisconnect`

---

## 8. 会话模型设计

### 8.1 第一阶段会话原则

第一阶段采用“轻会话”：

- 会话只用于 HTTP + SSE transport 层关联
- 不在服务端保存复杂业务上下文
- 不改变当前工具调用参数要求

即：

- `datasourceName` 依旧显式传递
- 不依赖 session 保存“当前数据源”
- 这样可以避免引入隐式状态

### 8.2 会话建立流程

1. 客户端连接 `GET /mcp/sse`
2. 服务端创建 `sessionId`
3. SSE 推送 session 初始化事件
4. 客户端调用 `POST /mcp`
5. 请求头带上 `X-Mcp-Session-Id`
6. 服务端通过 `sessionId` 找到对应 session

### 8.3 心跳策略

建议：

- 每 15 秒发送一次注释或 ping event
- 若发送失败，立即清理 session

---

## 9. 协议行为设计

### 9.1 initialize

无论 stdio 还是 HTTP，行为一致。

返回：

- `protocolVersion`
- `capabilities`
- `serverInfo`

建议补充 capability：

```json
{
  "tools": { "listChanged": false },
  "transport": {
    "stdio": true,
    "http": true,
    "sse": true
  }
}
```

### 9.2 tools/list

行为保持一致。

要求：

- stdio / HTTP 返回的 tools schema 必须完全一致
- 由 `McpToolCatalog` 统一生成

### 9.3 tools/call

第一阶段：

- 请求从 `POST /mcp` 进入
- 结果同步返回 JSON-RPC result
- 如果工具执行失败：
  - MCP result 中的 `isError=true`
  - tool content 仍返回可读错误 JSON

第二阶段补充：

- `tools/call.params.async=true`
  - 仅用于 HTTP MCP
  - 需要携带已建连的 `X-Mcp-Session-Id`
  - HTTP 响应先返回受理结果和 `requestId`
  - 后续通过 SSE 发送：
    - `tool_progress`：`ACCEPTED` / `RUNNING`
    - `tool_result`：最终 `McpResponse`

### 9.4 SSE 事件类型

建议预留以下事件：

- `session`
- `heartbeat`
- `notification`
- `tool_result`（第二阶段）
- `tool_progress`（第二阶段）
- `server_shutdown`

---

## 10. 错误处理设计

### 10.1 协议错误

由 `McpProtocolService` 返回 JSON-RPC error：

- `-32600` Invalid Request
- `-32601` Method not found
- `-32602` Invalid params
- `-32603` Internal error

### 10.2 工具错误

工具错误不走 JSON-RPC error，而继续沿用当前策略：

- JSON-RPC 层返回 `result`
- `content[].text` 中放 `McpResponse`
- `isError=true`

原因：

- 与当前 `McpStdioServer` 行为一致
- AI 客户端可以直接读取工具错误详情

### 10.3 SSE 错误

建议：

- SSE 建连失败 → 返回普通 HTTP 错误
- SSE 推送失败 → 清理 session
- 不在断流时尝试复杂重放机制

第一阶段不做：

- event replay
- last-event-id 恢复
- exactly-once 保证

---

## 11. 测试设计

### 11.1 单元测试

新增：

- `McpProtocolServiceTest`
  - initialize
  - tools/list
  - tools/call
  - unknown method
- `McpToolDispatcherTest`
  - 各 tool 分发正确

### 11.2 HTTP 集成测试

新增：

- `POST /mcp` initialize
- `POST /mcp` tools/list
- `POST /mcp` tools/call`
- 非法 JSON-RPC 请求

### 11.3 SSE 测试

新增：

- `GET /mcp/sse` 建连成功
- session 初始化事件返回
- session 清理
- heartbeat 推送

---

## 12. 实施顺序

### Phase 1：协议抽取

- 新增 `McpProtocolService`
- 新增 `McpToolDispatcher`
- 新增 `McpToolCatalog`
- 重构 `McpStdioServer`

### Phase 2：HTTP MCP 异步增强版

- 为 `tools/call` 增加 `async=true` 可选模式
- 要求客户端通过 `X-Mcp-Session-Id` 绑定现有 SSE 会话
- 通过 `tool_progress` / `tool_result` 事件回传异步执行状态与结果

### Phase 3：SSE 通道

- 新增 `SseSessionManager`
- 实现 `GET /mcp/sse`
- 增加 session / heartbeat / notification

### Phase 4：测试与文档

- 补单测和集成测试
- 更新 README
- 更新 RequirementDocument

---

## 13. 第一阶段范围确认

当前已确认：

- `SSE` 按标准 Server-Sent Events 理解
- 保留 `stdio + HTTP + SSE`
- 第一阶段 `tools/call` 同步返回
- MCP HTTP 路径：
  - `POST /mcp`
  - `GET /mcp/sse`

未纳入第一阶段：

- 工具结果流式分段输出
- 基于 session 的业务态记忆
- 事件重放
- HTTP 鉴权

---

## 14. 结论

本方案的核心原则是：

- 先抽协议核心，避免 stdio 和 HTTP 各写一套 MCP 逻辑
- 先打通 HTTP + SSE transport，再考虑工具流式化
- 保留现有 `/api/*` 调试接口，不混协议层
- 第一阶段优先低风险落地，第二阶段再逐步增强流式能力

这样可以在不破坏现有功能的前提下，把项目从“stdio-only MCP + 调试 HTTP”演进到“多 transport MCP 服务”。
