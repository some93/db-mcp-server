# db-mcp-server 项目说明

## 概述

`db-mcp-server` 是一个基于 Java 11 + Spring Boot 2.7.18 构建的 **MCP（Model Context Protocol）数据库网关**，通过标准化工具接口让 AI（Claude）安全访问 MySQL 和 TDengine 数据库，当前同时支持：

- `stdio MCP`
- `HTTP MCP`
- `HTTP + SSE MCP`
- 本机 `/api/*` 调试接口

每个业务项目独立启动一个 JVM 进程，绑定自己的数据库，互不干扰：

```
项目A的Claude窗口 → 独立JVM → 数据库1、数据库2
项目B的Claude窗口 → 独立JVM → 数据库3
```

---

## 快速启动

```bash
java -Xms32m -Xmx128m -XX:TieredStopAtLevel=1 \
     -jar db-mcp-server.jar \
     --config /path/to/your-project.yml
```

配置文件模板见 [example-config.yml](doc/example-config.yml)。
如果想直接复制最小可用版本，也可以用：

- [example-config-stdio.yml](doc/example-config-stdio.yml)
- [example-config-http-sse.yml](doc/example-config-http-sse.yml)

常用最小配置建议：

- `stdio-only`：`mcp.stdio.enabled=true`、`mcp.http.enabled=false`、`http.enabled=false`
- `http+sse`：`mcp.stdio.enabled=false`、`mcp.http.enabled=true`，并显式设置 `mcp.http.path` / `mcp.http.ssePath`

### Claude Desktop 配置

```json
{
  "mcpServers": {
    "my-project-db": {
      "command": "java",
      "args": [
        "-Xms32m", "-Xmx128m", "-XX:TieredStopAtLevel=1",
        "-jar", "/path/to/db-mcp-server.jar",
        "--config", "/path/to/my-project.yml"
      ]
    }
  }
}
```

---

## MCP 工具清单（11个）

说明：

- 当前对外能力共 15 个接口：11 个 MCP 工具 + 2 个 MCP HTTP 传输端点（`POST /mcp`、`GET /mcp/sse`）+ 2 个 HTTP 健康端点（`/health`、`/ready`）。
- 下表只统计 MCP `tools/list` 中暴露的 11 个工具，不把 HTTP 传输端点和健康端点计入工具数。

### 元数据探查

| 工具 | 必填参数 | 说明 |
|------|----------|------|
| `listDatasources` | — | 列出所有数据源及健康状态 |
| `locateTable` | `tableName` | 跨数据源精确/模糊查找表，返回编辑距离相似推荐（最多5条） |
| `listTables` | `datasourceName` | 列出指定数据源所有表 |
| `listIndexesAndViews` | `datasourceName` | 列出索引和视图 |
| `describeObject` | `datasourceName`, `objectName` | 查看表/视图结构；对象不存在时返回相似对象推荐 |

### 执行

| 工具 | 必填参数 | 说明 |
|------|----------|------|
| `executeQuery` | `datasourceName`, `sql` | 执行只读 SELECT，超 500 行自动截断并标记 `truncated=true` |
| `executeWrite` | `datasourceName`, `sql` | 执行 INSERT/UPDATE/DELETE，多语句请求级事务，任一失败全部回滚 |
| `createTable` | `datasourceName`, `sql` | 执行 CREATE TABLE，成功后自动触发元数据全量刷新 |
| `explainQuery` | `datasourceName`, `sql` | 分析 SELECT 执行计划，返回索引使用情况 |

### 运维

| 工具 | 可选参数 | 说明 |
|------|----------|------|
| `refreshMetadataCache` | `datasourceNames[]`, `mode` | 手动刷新元数据缓存（SYNC/ASYNC，默认全量SYNC） |
| `getServiceStatus` | — | 服务版本、运行时间、数据源健康、HTTP端口、配置摘要 |

---

## HTTP 调试接口

启动后自动在 `http://127.0.0.1:<随机端口>` 监听，实际端口在启动日志中打印。与 MCP 工具语义完全一致，用于开发期本地验证。

```
GET  /health                        # 进程存活检查（始终 200）
GET  /ready                         # 就绪检查（所有数据源 UP 才返回 200，否则 503）

GET  /api/status                    # getServiceStatus
GET  /api/datasources               # listDatasources
GET  /api/locate-table?tableName=   # locateTable
GET  /api/tables?datasourceName=    # listTables
GET  /api/indexes-and-views?datasourceName=
GET  /api/describe?datasourceName=&objectName=&objectType=

POST /api/query        {"datasourceName": "", "sql": ""}
POST /api/write        {"datasourceName": "", "sql": ""}
POST /api/create-table {"datasourceName": "", "sql": ""}
POST /api/explain      {"datasourceName": "", "sql": ""}
POST /api/refresh-metadata {"datasourceNames": [], "mode": "SYNC"}
```

## MCP HTTP + SSE 接口

除 `stdio` 外，当前也支持 MCP over HTTP：

```
POST /mcp         # MCP JSON-RPC 请求入口（initialize / tools/list / tools/call）
GET  /mcp/sse     # SSE 事件流通道，返回 session 事件与后续服务端通知
```

当前第一阶段行为：

- `tools/call` 仍通过 `POST /mcp` 同步返回结果
- `GET /mcp/sse` 先提供会话与事件通道能力
- `mcp.http.path` / `mcp.http.ssePath` 已生效，可改为自定义 MCP HTTP / SSE 路径
- `/api/*` 继续保留为调试接口，不承载 MCP 协议

可选的第二阶段异步模式：

- 客户端先连接 `GET /mcp/sse` 获取 `sessionId`
- 之后调用 `POST /mcp` 时带 `X-Mcp-Session-Id`
- 在 `tools/call.params` 中传 `async=true` 时，请求会立即返回受理结果
- 实际工具执行结果通过 SSE `tool_progress` / `tool_result` 事件回传

示例配置：

```yaml
mcp:
  http:
    enabled: true
    path: /custom-mcp
    ssePath: /custom-mcp/events
```

如果只想跑 HTTP + SSE，不想保留 stdio，建议同时配置：

```yaml
mcp:
  stdio:
    enabled: false
  http:
    enabled: true
    path: /custom-mcp
    ssePath: /custom-mcp/events
http:
  enabled: true
```

---

## 统一返回结构

所有接口（MCP 和 HTTP）返回相同结构：

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Returned 42 row(s).",
  "data": { ... },
  "costMs": 18
}
```

**错误码分组：**

| 前缀 | 说明 |
|------|------|
| `PROTOCOL_*` | MCP 协议层错误 |
| `PARAM_*` | 参数校验失败 |
| `DATASOURCE_UNAVAILABLE` | 数据源不可用（健康检查失败） |
| `DATASOURCE_PERMISSION_DENIED` | 数据源账号权限不足，消息中包含 GRANT 修复建议 |
| `SQL_BLOCK_*` | SQL 安全拦截 |
| `SQL_EXECUTE_*` | SQL 执行错误 |
| `CACHE_*` | 元数据缓存错误 |
| `PARTIAL_SUCCESS` | 部分成功（如刷新缓存时部分失败） |
| `BUSINESS_*` | 业务逻辑错误 |
| `SYSTEM_*` | 系统内部错误 |

**常用错误码速查表：**

| 错误码 | 场景 |
|------|------|
| `PARAM_MISSING` | HTTP 请求缺少必填参数 |
| `PARAM_SQL_EMPTY` | SQL 为空或全空白 |
| `PARAM_SQL_TOO_LONG` | SQL 超过长度上限 |
| `DATASOURCE_NOT_FOUND` | 指定数据源不存在 |
| `DATASOURCE_UNAVAILABLE` | 健康检查失败，数据源当前不可用 |
| `DATASOURCE_PERMISSION_DENIED` | MySQL 权限不足，消息中附带 GRANT 修复建议 |
| `SQL_BLOCK_KEYWORD` | 命中危险关键字黑名单 |
| `SQL_BLOCK_TYPE` | 语句类型与接口用途不匹配 |
| `SQL_BLOCK_NO_WHERE` | UPDATE / DELETE 缺少 WHERE |
| `SQL_BLOCK_PSEUDO_WHERE` | WHERE 为伪过滤条件 |
| `SQL_BLOCK_INSERT_SELECT` | 禁止 `INSERT ... SELECT` |
| `SQL_BLOCK_CREATE_AS_SELECT` | 禁止 `CREATE TABLE ... AS SELECT` |
| `SQL_BLOCK_BATCH_TOO_LARGE` | 批量 INSERT 行数超过上限 |
| `SQL_BLOCK_PARSE` | SQL 解析失败 |
| `SQL_EXECUTE_ERROR` | 通用 SQL 执行失败 |
| `SQL_EXECUTE_ROLLBACK` | MySQL 写入失败且已整体回滚 |
| `SQL_EXECUTE_PARTIAL` | TDengine 写入失败，已执行语句无法回滚 |
| `PARTIAL_SUCCESS` | 批量刷新缓存时部分数据源成功 |

---

## 安全规则

### 全局禁止（所有数据库）

- **DDL / 权限：** `DROP` / `TRUNCATE` / `ALTER` / `GRANT` / `REVOKE`
- **危险管理命令：** `LOCK TABLES` / `UNLOCK TABLES` / `FLUSH` / `SET GLOBAL` / `SHUTDOWN`
- **危险写法：** `INSERT ... SELECT` / `CREATE TABLE ... AS SELECT`
- **无条件写入：** `UPDATE` / `DELETE` 没有 WHERE 条件
- **伪过滤条件：** `WHERE 1=1` / `WHERE TRUE` / `WHERE 1='1'` / `WHERE col IS NOT NULL`

### TDengine 额外禁止

- `JOIN`（TDengine 对通用 JOIN 支持有限）

### 批量写入限制

单次 INSERT 行数超过 `batchInsertMaxRows`（默认 1000）时拒绝执行，并在响应中说明上限和拆批建议。

---

## 启动行为

- **元数据预热失败 → 拒绝启动**：任何数据源预热失败，服务抛出异常不启动。
- **启动时权限探测**：每个 MySQL 数据源初始化后，自动执行一次轻量权限探测查询。若账号权限不足，在 stderr 打印 WARN 日志并附带 GRANT 修复命令；探测失败不中断启动，允许服务以降级状态运行。
- **运行时权限识别**：执行 SQL 遇到 MySQL 权限错误（错误码 1044/1045/1142/1143）时，返回 `DATASOURCE_PERMISSION_DENIED`，消息中包含完整 GRANT 修复命令，而非通用的 `SQL_EXECUTE_ERROR`。
- **stdout 严格隔离**：在 `stdio MCP` 模式下，stdout 仅输出 MCP JSON-RPC 协议消息，所有日志（包括审计）写入 stderr，不污染 MCP 通道。
- **连接断开自动退出**：`mcp.exitOnDisconnect=true` 时，Claude Desktop 关闭连接后 JVM 自动退出，无孤儿进程。

---

## 架构分层

```
┌─────────────────────────────────────────────────────┐
│ Transport                                           │
│   McpStdioServer   — JSON-RPC 2.0 over stdio        │
│   McpTransportController — MCP over HTTP + SSE      │
│   McpHttpController  — HTTP REST (127.0.0.1)          │
│   HealthController — /health  /ready                │
├─────────────────────────────────────────────────────┤
│ Application                                         │
│   metadata/   execution/   cache/   status/         │
├─────────────────────────────────────────────────────┤
│ Domain                                              │
│   SqlGuard       — SQL 安全链（注释清洗→解析→规则）  │
│   HealthService  — 定时探测 + 调用前快速校验         │
│   MetadataCache  — Caffeine 本地缓存                │
│   AuditLogger    — 结构化审计（SLF4J logger=audit） │
├─────────────────────────────────────────────────────┤
│ Adapter SPI                                         │
│   DatasourceAdapter / MetadataAdapter               │
│   QueryExecutor / WriteExecutor                     │
│   SqlGuardDialectSupport                            │
├─────────────────────────────────────────────────────┤
│ Adapter Impl                                        │
│   mysql/    — MySqlAdapter（HikariCP + JDBC）        │
│   tdengine/ — TDengineAdapter（RESTful 驱动）        │
├─────────────────────────────────────────────────────┤
│ Infrastructure                                      │
│   DataSourceManager / HikariCP                      │
│   AppConfig (YAML) / HttpPortLogger                 │
└─────────────────────────────────────────────────────┘
```

---

## 技术栈

| 组件 | 选型 | 说明                                   |
|------|------|--------------------------------------|
| 语言 | Java 11 | -                                    |
| 框架 | Spring Boot 2.7.18 | Java 11 下最高稳定版                       |
| 构建 | Maven | —                                    |
| MCP 协议 | 手写 stdio JSON-RPC 2.0 | ralscha starter 需要 Java 17，手写约 300 行 |
| 连接池 | HikariCP | Spring Boot 内置                       |
| 元数据缓存 | Caffeine | 轻量本地缓存                               |
| SQL 解析 | JSqlParser 4.7 | 安全拦截和语法校验                            |
| JSON | Jackson | Spring Boot 内置                       |
| MySQL 驱动 | mysql-connector-java 8.x | —                                    |
| TDengine 驱动 | taos-jdbcdriver 3.3.0 | RESTful 模式，无需安装客户端                   |

---

## 审计日志

每次工具调用自动写入独立 SLF4J logger `audit`（INFO 级别），格式为 JSON 单行：

```json
{"timestamp":"2026-04-18T10:00:00.000+0800","tool":"executeQuery","datasource":"user_db","sql":"SELECT * FROM users LIMIT 10","success":true,"durationMs":42,"errorCode":null,"errorMsg":null}
```

通过 logback 配置可将 `audit` logger 路由到独立文件或 ELK：

```xml
<logger name="audit" level="INFO" additivity="false">
    <appender-ref ref="AUDIT_FILE"/>
</logger>
```

---

## 多数据源同名表处理

`locateTable` 返回所有命中的数据源：

```json
{
  "matchStatus": "EXACT_MULTI",
  "matchedDatasources": [
    {"datasourceName": "user_db", "tableName": "orders"},
    {"datasourceName": "order_db", "tableName": "orders"}
  ]
}
```

AI 或用户根据业务语义选择目标数据源后，后续请求显式传入 `datasourceName`。

