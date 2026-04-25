# MCP数据库一体化服务重设计方案

## 1. 文档目标

本文档基于原始需求文档和已完成的需求澄清，重新设计一个可执行、可扩展、便于测试和后续实现的方案。

设计原则：

- 不再以固定 9 个接口为约束，接口数量服从职责边界。
- 保留原需求中的核心安全规则、双数据库支持和运行模式要求。
- 明确哪些能力属于探查、校验、执行、运维，避免高耦合接口。
- 直接面向实现，输出清晰的模块划分、接口清单、返回契约和配置模型。
- **不做权限控制**：内部小团队使用，权限模型过度设计，砍掉。

## 2. 产品目标

系统定位为面向 MCP 客户端和 HTTP 调试客户端的数据库 AI 执行网关，支持 MySQL 和 TDengine。

**典型使用场景**：多个 Java 项目并行开发，每个项目绑定自己的数据库，分别启动独立 MCP 进程，互不干扰：

```
项目A的Claude窗口  →  启动独立JVM  →  数据库1、数据库2
项目B的Claude窗口  →  启动独立JVM  →  数据库3
项目C的Claude窗口  →  启动独立JVM  →  数据库4、数据库5
```

核心目标：

- 让 AI 可以安全完成数据源发现、元数据探查、查询、写入、建表和执行计划分析。
- 对数据库执行建立统一的安全、健康、缓存和错误处理模型。
- 同时支持 `stdio` MCP 模式和 HTTP 调试模式，并保持业务语义一致。
- 优先保证确定性、排障效率和内部可运维性。

## 3. 已确认的关键业务规则

### 3.1 数据源与会话

- 支持多数据源，`datasourceName` 大小写不敏感。
- **不做权限控制**，所有数据源默认允许全部操作。
- 多数据源同名表时，返回所有命中项，由 AI 或用户选择。
- 仅在同一个 `stdio` MCP 连接内允许临时记住已选数据源。
- HTTP 调试接口保持无状态，每次请求必须显式传 `datasourceName`。

### 3.2 查询规则

- `executeQuery` 仅允许单条 `SELECT`。
- 默认不分页。
- 查询结果超过 500 行时，仅返回前 500 行，并标记 `truncated=true`。
- `markdownTable` 仅用于展示，结构化处理以 `rows` 为准。
- `totalRows` 尽量返回，复杂场景允许为空或 `unknown`。
- `explain` 仅支持 `SELECT`。

### 3.3 写入规则

- 支持 `INSERT ... VALUES`、批量 `INSERT ... VALUES`、`UPDATE`、`DELETE`、`CREATE TABLE`。
- 不允许 `INSERT ... SELECT`。
- 不允许 `CREATE TABLE ... AS SELECT ...`。
- `CREATE TABLE` 必须单独执行。
- `INSERT / UPDATE / DELETE` 可在同一请求中混合执行。
- 多语句写入按请求级原子性处理，任一失败整体回滚。
- 即使整体失败，也保留逐条语句明细。
- 事务仅在单次 `executeWrite` 请求内有效，不支持跨请求事务。

### 3.4 安全规则

- 永久禁止 `DROP`、`TRUNCATE`、`ALTER`、`GRANT`、`REVOKE`、`LOCK TABLES`、`UNLOCK TABLES`、`FLUSH`、`SET GLOBAL`、`SHUTDOWN`。
- 永久禁止 TDengine `JOIN`。
- `UPDATE / DELETE` 必须具备有效过滤条件。
- `WHERE 1=1`、`WHERE TRUE`、`WHERE id IS NOT NULL` 及其他可静态判定为伪过滤条件的语句视为高风险并拦截。
- SQL 执行前先移除注释。
- SQL 关键字归一化仅用于解析和检测层，不强制改写最终执行 SQL 文本。
- SQL 全局最大长度采用统一可配置上限。
- 批量插入最大行数采用统一配置项，超限时返回上限值和拆批建议。

### 3.5 缓存与健康规则

- 元数据缓存默认开启启动预热。
- 只要有一个数据源预热失败，服务启动失败。
- 元数据缓存至少覆盖数据源元信息、表列表、表结构、索引、视图。
- 缓存刷新周期采用全局统一配置。
- `CREATE TABLE` 成功后触发所有数据源元数据全量刷新。
- 刷新失败按数据源隔离，不影响其他数据源。
- 某个数据源刷新失败后，后续元数据读取可继续使用旧缓存，并明确提示来自旧缓存。
- 数据源健康检查采用"定时探测 + 调用前快速校验"双机制。
- 快速校验失败后允许做一次最终确认；若确认成功，则更新健康状态为正常。

### 3.6 运行与可观测性

- 同时支持 `stdio MCP`、`HTTP MCP`、`HTTP + SSE MCP` 与 HTTP 调试服务，可按配置选择启用。
- **stdio 是主模式**：Claude Desktop 启动时拉起进程，断开连接后进程自动退出。
- **HTTP MCP / HTTP + SSE MCP**：用于远程或本地 HTTP 客户端接入，协议入口为 `POST /mcp`，事件流入口为 `GET /mcp/sse`。
- **HTTP 调试接口**：本机回环地址，随机端口，无鉴权，用于开发期验证。
- 配置文件密码明文存储，YAML 格式，每个项目独立一份配置文件。
- 默认记录完整原始 SQL 日志。
- 健康接口拆分为 `/health` 和 `/ready`。
- `/ready` 只有所有数据源正常时才返回就绪。
- MCP 工具调用层继续遵循 JSON-RPC / MCP 协议；业务结果体仍统一采用 `McpResponse` 结构：`success`、`code`、`message`、`data`、`costMs`。
- **启动时权限探测**：每个 MySQL 数据源初始化后执行轻量权限探测；权限不足时 WARN 日志附 GRANT 命令，不中断启动。
- **运行时权限错误识别**：MySQL 权限错误统一返回 `DATASOURCE_PERMISSION_DENIED`，消息中包含 GRANT 修复建议。

## 4. 接口清单（15个）

接口按四组设计：元数据探查、执行、缓存与运维、服务状态。

口径说明：

- MCP 对外暴露 11 个工具，对应 `tools/list` 返回结果。
- HTTP MCP 额外提供 2 个协议入口：`POST /mcp`、`GET /mcp/sse`。
- HTTP 侧另外提供 2 个健康端点：`/health`、`/ready`。
- 因此本文档总数写为 15 个接口，而 README 中的工具数写为 11 个，两者统计口径不同。

> 说明：相比原18接口方案，本版本做了精简：
> - 砍掉 `validateSql`（内部工具信任度高，合并到各执行接口前置校验）
> - 砍掉 `previewWriteImpact`（多一次 RTT，内部场景价值低，移至 Phase 3 按需加）
> - 合并 `listViews` + `listIndexes` 为 `listIndexesAndViews`
> - 砍掉 `searchMetadata`（Phase 2 按需加，`locateTable` + `describeObject` 已够用）
> - 合并 `getCacheStatus` 进 `getServiceStatus`

### 4.1 元数据探查接口（5个）

#### 4.1.1 `listDatasources`

用途：

- 返回所有已配置数据源的基础信息和健康状态。

入参：

- 无

返回 `data` 建议字段：

- `datasources`
- `datasources[].name`
- `datasources[].type`
- `datasources[].healthStatus`
- `datasources[].lastHealthCheckAt`
- `datasources[].healthMessage`

#### 4.1.2 `locateTable`

用途：

- 依据表名做全局精确匹配和模糊推荐，AI 主入口。

入参：

- `tableName`

返回 `data` 建议字段：

- `matchStatus`
- `matchedDatasources`
- `similarTables`
- `allDatasources`

规则：

- 精确匹配大小写不敏感。
- 相似匹配采用前缀匹配、包含匹配、编辑距离综合排序，最多返回 5 条。
- 单数据源环境 AI 自动跳过此接口，直接使用唯一数据源。
- 无精确匹配且无相似表时，返回空相似表列表和全部数据源列表。

#### 4.1.3 `listTables`

用途：

- 查询指定数据源下所有表。

入参：

- `datasourceName`

返回 `data` 建议字段：

- `tables`
- `tables[].name`
- `tables[].tableType`

规则：

- 表类型排序：普通表、超级表、子表。
- 组内按表名升序。
- 空库返回空列表和建表引导提示。

#### 4.1.4 `listIndexesAndViews`

用途：

- 查询指定数据源下所有索引和视图，合并返回。

入参：

- `datasourceName`

返回 `data` 建议字段：

- `indexes`
- `views`

#### 4.1.5 `describeObject`

用途：

- 查询表或视图结构详情。

入参：

- `datasourceName`
- `objectName`
- `objectType`，可选，支持 `TABLE`、`VIEW`

返回 `data` 建议字段：

- `objectName`
- `objectType`
- `tableType`
- `columns`
- `columns[].name`
- `columns[].dataType`
- `columns[].length`
- `columns[].precision`
- `columns[].primaryKey`
- `columns[].nullable`
- `columns[].defaultValue`
- `columns[].comment`

规则：

- 字段顺序严格按数据库原始顺序返回。
- 拿不到的可选元数据返回 `null`。
- 对象不存在时返回不存在提示和相似对象推荐。

### 4.2 执行接口（4个）

#### 4.2.1 `executeQuery`

用途：

- 执行单条只读查询。

入参：

- `datasourceName`
- `sql`

返回 `data` 建议字段：

- `rows`
- `returnedRows`
- `totalRows`
- `truncated`
- `markdownTable`
- `summary`
- `dbCostMs`

说明：

- `totalRows` 尽量返回。
- 截断后 `markdownTable` 只展示当前返回部分，并明确标记已截断。

#### 4.2.2 `executeWrite`

用途：

- 执行 `INSERT / UPDATE / DELETE` 写入，支持混合执行。

入参：

- `datasourceName`
- `sql`

返回 `data` 建议字段：

- `affectedRows`
- `warnings`
- `summary`
- `details`
- `details[].statementIndex`
- `details[].statementType`
- `details[].affectedRows`
- `details[].status`
- `details[].message`
- `details[].dbCostMs`
- `dbCostMs`

规则：

- 允许混合 `INSERT / UPDATE / DELETE`。
- 不允许出现 `CREATE TABLE`。
- 多语句按请求级事务执行。
- 任一失败整体回滚，但保留逐条明细。

#### 4.2.3 `createTable`

用途：

- 建表，和一般写操作分离，成功后触发元数据刷新。

入参：

- `datasourceName`
- `sql`

返回 `data` 建议字段：

- `generatedObjects`
- `warnings`
- `summary`
- `dbCostMs`
- `metadataRefreshTriggered`

规则：

- 只允许显式字段定义建表。
- 建议使用 `CREATE TABLE IF NOT EXISTS`。
- 成功后触发全量元数据刷新。

#### 4.2.4 `explainQuery`

用途：

- 分析单条 `SELECT` 的执行计划，不执行读写。

入参：

- `datasourceName`
- `sql`

返回 `data` 建议字段：

- `plan`
- `indexesUsed`
- `estimatedRows`
- `summary`
- `dbCostMs`

### 4.3 缓存与运维接口（1个）

#### 4.3.1 `refreshMetadataCache`

用途：

- 手动触发元数据缓存刷新。

入参：

- `mode`，可选，支持 `SYNC`、`ASYNC`，默认 `SYNC`
- `datasourceNames`，可选，默认全量

返回 `data` 建议字段：

- `successCount`
- `failedCount`
- `totalCostMs`
- `results`
- `results[].datasourceName`
- `results[].status`
- `results[].costMs`
- `results[].message`

规则：

- 部分成功场景使用 `code=PARTIAL_SUCCESS`。

### 4.4 服务状态接口（5个）

#### 4.4.1 `getServiceStatus`

用途：

- 查看服务模式、版本、运行时间、HTTP 信息、缓存状态和全局配置摘要，合并 getCacheStatus 能力。

入参：

- 无

#### 4.4.2 `/health`

用途：

- 进程存活和基础服务状态检查（HTTP 端点）。

返回 `data` 建议字段：

- `serviceStatus`
- `uptimeMs`
- `datasources`

#### 4.4.3 `/ready`

用途：

- 对外服务就绪状态检查（HTTP 端点）。

规则：

- 所有数据源都正常时才就绪。

#### 4.4.4 `POST /mcp`

用途：

- MCP over HTTP 的 JSON-RPC 请求入口。

规则：

- 第一阶段支持 `initialize`、`tools/list`、`tools/call`
- `tools/call` 继续同步返回

#### 4.4.5 `GET /mcp/sse`

用途：

- MCP over HTTP 的 SSE 事件流通道。

规则：

- 第一阶段用于建立会话、发送 heartbeat 和服务端事件
- 后续阶段再扩展长任务工具的流式结果

## 5. 统一返回契约

所有接口统一返回：

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "查询成功",
  "data": {},
  "costMs": 18
}
```

约束：

- 顶层 `costMs` 是接口总耗时。
- 第一版仅标准化 `dbCostMs` 作为执行耗时明细。
- 错误码采用字符串语义码。

建议错误码分组：

- `PROTOCOL_*`
- `PARAM_*`
- `DATASOURCE_UNAVAILABLE` — 数据源不可用
- `DATASOURCE_PERMISSION_DENIED` — 账号权限不足（含 GRANT 修复建议）
- `SQL_BLOCK_*`
- `SQL_EXECUTE_*`
- `CACHE_*`
- `BUSINESS_*`
- `SYSTEM_*`

建议同时维护一份错误码速查表，便于 AI 与人工排障直接定位：

| 错误码 | 来源 | 触发场景 |
|------|------|------|
| `PARAM_MISSING` | Controller / Service | 必填参数缺失 |
| `PARAM_SQL_EMPTY` | SqlGuard / Service | SQL 为空或空白 |
| `PARAM_SQL_TOO_LONG` | SqlGuard | SQL 超过 `maxLength` |
| `DATASOURCE_NOT_FOUND` | DataSourceManager | 数据源名称不存在 |
| `DATASOURCE_UNAVAILABLE` | HealthService | 健康检查失败 |
| `DATASOURCE_PERMISSION_DENIED` | MySQL Adapter | 账号权限不足 |
| `SQL_BLOCK_KEYWORD` | SqlGuard | 命中 DROP / ALTER 等危险关键字 |
| `SQL_BLOCK_TYPE` | SqlGuard | 语句类型与工具不匹配 |
| `SQL_BLOCK_NO_WHERE` | SqlGuard | UPDATE / DELETE 缺少 WHERE |
| `SQL_BLOCK_PSEUDO_WHERE` | SqlGuard | WHERE 为 1=1 / TRUE / IS NOT NULL 等伪过滤 |
| `SQL_BLOCK_INSERT_SELECT` | SqlGuard | 禁止 `INSERT ... SELECT` |
| `SQL_BLOCK_CREATE_AS_SELECT` | SqlGuard | 禁止 `CREATE TABLE ... AS SELECT` |
| `SQL_BLOCK_BATCH_TOO_LARGE` | SqlGuard | 批量 INSERT 行数超限 |
| `SQL_BLOCK_PARSE` | SqlGuard | SQL 解析失败 |
| `SQL_EXECUTE_ERROR` | Executor / Service | 通用执行错误 |
| `SQL_EXECUTE_ROLLBACK` | MySQL WriteExecutor | 写入失败且事务已回滚 |
| `SQL_EXECUTE_PARTIAL` | TDengine WriteExecutor | 写入失败且已执行语句不可回滚 |
| `PARTIAL_SUCCESS` | RefreshMetadataCacheService | 缓存刷新部分成功 |
| `BUSINESS_OBJECT_NOT_FOUND` | DescribeObjectService | 对象不存在 |
| `CACHE_NOT_READY` | Metadata 相关 Service | 元数据缓存未就绪 |

## 6. 安全与执行链设计

所有执行类接口统一经过一条标准执行链：

1. 参数校验
2. 数据源解析
3. 健康快速校验
4. 失败后二次确认
5. SQL 注释清洗
6. SQL 解析与分类
7. 安全规则匹配
8. 数据库方言适配
9. 超时控制与执行
10. 结果清洗与格式化
11. 审计日志记录

说明：

- 超时后必须尝试取消 SQL。
- 若取消结果不确定，返回信息中明确说明。
- 去掉权限校验步骤（内部工具不做权限控制）。

## 7. 技术架构

### 7.1 最终确认技术栈

| 项目 | 选型 | 说明 |
|------|------|------|
| 语言 | Java 11 | 公司硬约束 |
| 框架 | Spring Boot 2.7.x | Java 11 下最高稳定版 |
| 构建 | Maven | 团队统一 |
| MCP 协议 | 手写 stdio JSON-RPC | ralscha starter 需要 Java 17，手写约300行，一次性 |
| HTTP 调试 | Spring MVC | Spring Boot 内置 |
| 连接池 MySQL | HikariCP | Spring Boot 内置 |
| 连接池 TDengine | 独立连接池 | 不与 MySQL 共用 |
| 缓存 | Caffeine | 轻量本地缓存 |
| JSON | Jackson | Spring Boot 内置 |
| SQL 解析 | JSqlParser | 安全拦截和语法校验 |
| 配置 | YAML | 每项目独立一份 |

### 7.2 运行模式

```
java -Xms32m -Xmx128m -XX:TieredStopAtLevel=1 \
     -jar db-mcp-server.jar \
     --config projectA.yml
```

- stdio、HTTP MCP、HTTP + SSE MCP 与 HTTP 调试服务可同时启动。
- stdout 只输出 MCP 协议消息，stderr 输出所有日志，严格隔离。
- HTTP 监听 `127.0.0.1`，端口 auto 分配，启动时打印实际端口。
- Claude Desktop 断开连接，JVM 进程自动退出，无孤儿进程。

### 7.3 六层架构

#### Transport Layer
- MCP `stdio` 适配器（手写 JSON-RPC 2.0）
- MCP `HTTP + SSE` 适配器
- HTTP 调试控制器
- 健康检查接口（`/health`、`/ready`）

#### Application Layer
- 各工具用例服务
- 返回结构组装
- 会话级数据源选择上下文管理

#### Policy Pipeline Layer
- 参数校验器
- SQL 安全校验器
- 限制规则校验器
- 健康校验器

#### Domain Layer
- 元数据领域服务
- 查询执行服务
- 写入执行服务
- 建表服务
- 缓存服务
- 健康状态服务

#### Adapter SPI Layer
- `DatasourceAdapter`
- `MetadataAdapter`
- `QueryExecutor`
- `WriteExecutor`
- `ExplainProvider`
- `SqlGuardDialectSupport`

MySQL、TDengine 都通过 SPI 适配，业务层零 `if-else`。

#### Infrastructure Layer
- JDBC 与连接池
- Caffeine 缓存
- 日志与审计
- 配置加载
- 线程池

## 8. 推荐代码目录

```text
src/main/java/com/mcp/
  bootstrap/
  transport/
    http/
    mcp/
  application/
    metadata/
    execution/
    cache/
    status/
  domain/
    metadata/
    execution/
    security/
    cache/
    health/
  adapter/
    mysql/
    tdengine/
    spi/
  infrastructure/
    config/
    datasource/
    cache/
    logging/
    support/
```

## 9. 配置模型

每个项目独立一份 YAML 配置文件，通过 `--config` 参数指定：

```yaml
# 数据源配置
datasources:
  - name: user_db
    type: mysql
    url: jdbc:mysql://localhost:3306/userdb
    username: root
    password: 123456

  - name: order_db
    type: mysql
    url: jdbc:mysql://localhost:3306/orderdb
    username: root
    password: 123456

  - name: sensor_db
    type: tdengine
    url: jdbc:TAOS://localhost:6030/sensordb
    username: root
    password: taosdata

# SQL 限制
sql:
  maxRows: 500           # 查询最大返回行数
  timeoutSeconds: 5      # SQL 执行超时
  maxLength: 10000       # SQL 最大长度
  batchInsertMaxRows: 1000  # 批量插入最大行数

# 元数据缓存
cache:
  preloadOnStartup: true          # 启动时预热
  refreshIntervalSeconds: 300     # 定时刷新间隔（5分钟）

# 健康检查
health:
  checkIntervalSeconds: 30

# 时区
timezone: Asia/Shanghai

# MCP 行为
mcp:
  exitOnDisconnect: true   # 兼容旧配置：stdio 客户端断开后进程退出
  stdio:
    enabled: true
    exitOnDisconnect: true
  http:
    enabled: true
    path: /mcp            # 可自定义，例如 /custom-mcp
    ssePath: /mcp/sse     # 可自定义，例如 /custom-mcp/events
    sessionTimeoutSeconds: 1800
    heartbeatSeconds: 15

# HTTP 调试服务
http:
  enabled: true
  host: 127.0.0.1
  port: 0   # 0 表示自动分配
```

Claude Desktop 配置示例：

```json
{
  "mcpServers": {
    "项目A-db": {
      "command": "java",
      "args": ["-Xms32m", "-Xmx128m", "-XX:TieredStopAtLevel=1",
               "-jar", "/path/to/db-mcp-server.jar",
               "--config", "/path/to/projectA.yml"]
    },
    "项目B-db": {
      "command": "java",
      "args": ["-Xms32m", "-Xmx128m", "-XX:TieredStopAtLevel=1",
               "-jar", "/path/to/db-mcp-server.jar",
               "--config", "/path/to/projectB.yml"]
    }
  }
}
```

## 10. 实施优先级

### Week 1：核心链路跑通
- 项目骨架（Maven + Spring Boot 2.7）
- 手写 MCP stdio JSON-RPC 协议层
- YAML 配置加载
- MySQL 适配器 + HikariCP
- `listDatasources`、`listTables`、`describeObject`
- `executeQuery`（含安全拦截链）
- `/health`

### Week 2：完整功能
- `locateTable`（编辑距离模糊匹配）
- `executeWrite` + `createTable`（含事务回滚）
- `explainQuery`
- 元数据缓存（Caffeine + 定时刷新 + 启动预热）
- `refreshMetadataCache`
- `listIndexesAndViews`
- stdio MCP 完整对接 Claude Desktop

### Week 3：TDengine + 稳定性
- TDengine 适配器（SPI 插拔）
- `getServiceStatus`
- `/ready`
- 审计日志完善
- JVM 参数调优验收
- 压测与稳定性验证

### Phase 3（按需）：
- `previewWriteImpact`
- `searchMetadata`
- HTTP 可配置鉴权

## 11. 结论

相比原始设计，本方案的核心调整是：

- **语言锁定 Java 11**：公司硬约束，不引入团队不熟悉的 Python/Go。
- **MCP 协议手写**：ralscha starter 要求 Java 17，手写 300 行替代，零依赖风险。
- **stdio 主模式 + HTTP 辅助**：天然实现项目间数据库隔离，生命周期绑定 Claude 窗口。
- **新增 HTTP + SSE MCP**：在保留 stdio 的同时，支持基于 `POST /mcp` + `GET /mcp/sse` 的远程接入模式。
- **砍掉权限控制**：内部小团队不需要，减少维护复杂度。
- **当前对外共 15 个接口**：11 个 MCP 工具 + 2 个 MCP HTTP 入口（`POST /mcp`、`GET /mcp/sse`）+ 2 个健康端点；其中真正的 MCP 工具仍是 11 个。
- **3周可交付**：Week1 跑通核心链路，Week2 完整功能，Week3 TDengine + 稳定性。

