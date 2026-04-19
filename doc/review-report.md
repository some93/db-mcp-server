# db-mcp-server 代码 & 文档 Review 报告

**Review 日期**：2026-04-18
**Review 视角**：资深 AI 测试工程师
**Review 范围**：`src/main`、`src/test`、`doc/`、`README.md`
**风险分级**：P0 数据安全 / P1 功能缺陷 / P2 改进项

---

## 执行摘要

| 维度 | 评价 |
|------|------|
| 架构 | ✅ SPI 分层清晰，方言切换零 if-else，易扩展 |
| 安全设计 | ✅ 多层防线思路正确；原 `CREATE TABLE AS SELECT` 与拆分状态机风险已修复并补回归 |
| 代码质量 | ✅ 注释完整、命名清晰，近期重构收敛良好 |
| 测试工程 | ⚠️ 当前 60 用例已覆盖权限/事务/健康/缓存并发/TDengine 写入关键路径，但 TDengine 更完整覆盖与更高层集成门禁仍不足 |
| 文档 | ✅ 主要口径已同步；仍可继续补充更细的覆盖率与发布闸门说明 |

**发布前必做**（按优先级）：
1. 补 TDengine 元数据/查询/JOIN 拦截等测试
2. 评估启动关闭期 probe 竞态与并发重复 probe 是否要在当前版本处理
3. 如需发布门禁，补 JaCoCo 或关键路径覆盖清单
4. 视发布要求决定是否补 Spring 上下文级集成测试

---

## P0 历史高风险问题（已修复）

### P0-1 `CREATE TABLE ... AS SELECT` 变体绕过（已关闭）

**位置**：`src/main/java/com/mcp/domain/security/SqlGuard.java:150`

**历史问题**：早期实现使用字符串包含判断，`AS\nSELECT`、`AS\tSELECT`、多空格、括号等变体存在绕过风险。

**当前状态**：已修复为正则匹配：
```java
private static final Pattern CREATE_AS_SELECT = Pattern.compile(
    "\\bAS\\s*(?:\\(\\s*)?SELECT\\b",
    Pattern.CASE_INSENSITIVE
);
if (CREATE_AS_SELECT.matcher(clean).find()) {
    throw new SqlBlockedException("SQL_BLOCK_CREATE_AS_SELECT", ...);
}
```

**回归测试**：已覆盖换行、Tab、多空格、`AS ( SELECT` 变体。

---

### P0-2 反斜杠转义破坏 `splitStatements` 状态机（已关闭）

**位置**：`src/main/java/com/mcp/application/execution/ExecuteWriteService.java:141-198`

**历史问题**：引号内遇到反斜杠转义时，合法 SQL 可能被误拆并报 `SQL_BLOCK_PARSE`。

**当前状态**：已修复，当前实现已增加反斜杠处理分支：
```java
if (ch == '\\' && (inSingleQuote || inDoubleQuote)) {
    current.append(ch);
    if (next != '\0') {
        current.append(next);
        i++;
    }
    continue;
}
```

**回归测试**：已覆盖单引号内分号、双引号内分号、`\\'`、`\\\"` 场景。

---

### P0-3 字符串字面量内含黑名单关键字会误拦 ⚠️ 设计权衡

**位置**：`src/main/java/com/mcp/domain/security/SqlGuard.java:182-192`

```java
if (upper.contains(kw)) { throw ... }
```

**示例**：`INSERT INTO logs(msg) VALUES('User tried to DROP table')` 会被 `DROP` 命中误拦。

**现状评估**：注释中声明"快速门卫+JSqlParser 两道防线"，误拦是**安全默认**（拒绝优先于放过），可接受。

**建议**：在错误响应 message 中加引导："If the keyword appears inside a string literal, please use CHR() or parameter binding to avoid detection."，或在文档中明确此限制。

---

## P1 功能缺陷与测试覆盖缺口

### P1-1 测试覆盖的致命缺口（测试工程师视角）

**现状**：此前报告中的“42 通过但关键路径零测试”已经过时。当前 `mvn clean test` 为 60 通过，以下问题已补覆盖，但仍有剩余缺口：

| 模块 | 未覆盖的分支 | 风险 |
|------|------------|------|
| 集成测试 | Spring 上下文级事务/缓存/健康联动 | 已补事务回滚、健康恢复、缓存并发三类基础集成测试，但缺少更完整的 Spring 上下文级验证 |
| TDengine | 元数据、查询、权限/JOIN 拦截 | 当前仅覆盖写入失败后的 `FAILED/SKIPPED/rolledBack=false` |
| ExecuteWriteService.splitStatements | 末尾无分号、注释内分号等组合 | 已修反斜杠转义，但组合边界仍可继续补 |
| SqlGuard.checkWrite | `INSERT IGNORE`、`REPLACE INTO`、`INSERT INTO t SET a=1`（MySQL 扩展语法） | JSqlParser 兼容性未验证 |
| TDengineWriteExecutor | 元数据、查询、JOIN 拦截等未覆盖；写入失败后的 `SKIPPED/rolledBack=false` 已补单测 | TDengine 侧覆盖仍明显弱于 MySQL |
| SqlGuard.countBatchInsertRows | 含注释的 VALUES；字符串内含括号 | 批量上限检测的文本解析可靠性 |

**建议**：
- 事务测试：使用 H2 in-memory 数据库做轻量集成测试，验证 finally 块 autoCommit 恢复。
- 缓存/健康：补并发与恢复类测试，避免只覆盖单线程 happy path。
- TDengine：继续补元数据、查询、JOIN 拦截等用例。

**示例测试骨架**：
```java
@Test
void executeQuery_permissionDenied_wrapsException() throws Exception {
    SQLException sqlEx = new SQLException("Access denied for user 'test'", "28000", 1142);
    when(statement.executeQuery(anyString())).thenThrow(sqlEx);

    assertThatThrownBy(() -> executor.executeQuery(conn, sql, 100, 5))
        .isInstanceOf(DatasourcePermissionException.class)
        .hasMessageContaining("Access denied")
        .extracting(e -> ((DatasourcePermissionException) e).getGrantHint())
        .asString().contains("GRANT SELECT");
}
```

---

### P1-2 启动期 probe 与 @Scheduled 关闭竞态

**位置**：`HealthService.java:63` + `DataSourceManager.java:128`

`@PreDestroy` 关闭 HikariCP 连接池，但 `@Scheduled` 仍可能触发 `probe()` → `getConnection()` → `PoolInitializationException` → WARN 噪音。不影响数据安全，但影响优雅关闭的日志可读性。

**建议**：
```java
private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

@PreDestroy
public void destroy() {
    shuttingDown.set(true);
    // ... existing cleanup
}

private boolean probe(String datasourceName) {
    if (shuttingDown.get()) return false;
    // ... existing probe logic
}
```

---

### P1-3 quickCheck 并发重复 probe

**位置**：`HealthService.java:79`

非 UP 状态下两个并发请求都会触发 probe（ConcurrentHashMap 写入无互斥）。幂等但浪费连接，高并发下可能放大故障。

**建议**：用 `ConcurrentHashMap<String, CompletableFuture<Boolean>>` 合并同数据源的 in-flight probe，或用 `synchronized` 段保护 probe 调用。

---

### P1-4 权限探测不区分网络错误与权限错误

**位置**：`DataSourceManager.java:95-99`

```java
} else {
    log.warn("... non-permission error: {}", ...);
}
```

数据库暂时不可达、DNS 失败等都被降级为 WARN 继续启动。如果用户同时配置 `cache.preloadOnStartup=false`，首次业务请求才发现问题，排错困难。

**建议**：
- 检查 `SQLException.getSQLState()` 开头 "08"（连接类错误）时记 ERROR 并可选中止启动。
- 加配置开关 `health.failStartupOnProbeError: false`（默认），生产环境可开启。

---

## P2 改进项

### P2-1 SQLTimeoutException 包装的 SQLState 非标准

**位置**：`MySqlQueryExecutor.java:65`、`MySqlWriteExecutor.java:48` 等

```java
new SQLException(msg, "QUERY_TIMEOUT", cause);
```

`"QUERY_TIMEOUT"` 放在 SQLState 位置，不是标准 5 字符码（SQL:2003 查询取消应为 `"57014"`）。上层若想通过 SQLState 识别会失败。当前上层只看 message，暂无实际故障，但违反 JDBC 契约。

**建议**：改为标准 SQLState `"57014"`，或抛自定义 RuntimeException（如 `SqlExecutionTimeoutException`）。

---

### P2-2 DatasourcePermissionException.datasourceName 字段常年为空字符串

**位置**：`MySqlQueryExecutor.java:70`、`MySqlWriteExecutor.java:53,70`

Adapter 层抛异常时传 `""`（因为 adapter 层没有 datasourceName 上下文）。Service 层从自己的上下文填充 datasourceName 给 AuditLogger 和响应，异常上的这个字段**没人读**。保留空字段是误导性 API。

**建议**（两选一）：
- **方案 A**：删掉 `datasourceName` 字段，只保留 `grantHint`。
- **方案 B**：Service 层 catch 后通过 setter 回填，方便后续统一日志收集。

---

### P2-3 注释清洗先 BLOCK 再 LINE 的顺序需文档化+测试

**位置**：`SqlGuard.java:70-74`

```java
String s = COMMENT_BLOCK.matcher(sql).replaceAll(" ");
s = COMMENT_LINE.matcher(s).replaceAll(" ");
```

代码注释说明了顺序意图，但 **SqlGuardTest 未覆盖混合嵌套注释**（如 `/* -- */ SELECT 1`、`-- /* x */` 的场景），行为可能与预期不符。

**建议**：补充 SqlGuardTest 用例：
- `/* -- comment */ SELECT 1 FROM t`
- `SELECT 1 -- /* fake block */ FROM t`
- 验证结果符合 SQL 规范（`--` 不在 `/* */` 内优先级）。

---

### P2-4 文档与代码口径不一致（大部分已修复）

| 位置 | 问题 | 建议 |
|------|------|------|
| README.md:45 | 声称"11 个 MCP 工具" | 已补充说明：11 个工具 + 2 个 HTTP 健康端点 = 13 个接口 |
| RequirementDocument.md 第 4 节 | 声称"13 个接口" | 已补口径说明，与 README 对齐 |
| RequirementDocument.md 第 5 节 | 只描述错误码分组策略，未列完整错误码表 | 已补常用错误码速查表 |
| README.md:24 | 引用 `doc/example-config.yml` | 确认文件存在（已确认存在） |
| RequirementDocument.md 第 3.6 节 | 权限探测描述 | 当前已明确为 MySQL 数据源权限探测 |

**建议补充的错误码速查表**：

| 错误码 | 来源 | 触发场景 |
|--------|------|----------|
| PARAM_SQL_EMPTY | SqlGuard | SQL 为空或空白 |
| PARAM_SQL_TOO_LONG | SqlGuard | SQL 长度超过 maxLength |
| PARAM_MISSING | Controller / Service | 必填参数缺失 |
| SQL_BLOCK_KEYWORD | SqlGuard | 含禁止关键字（DROP/ALTER 等） |
| SQL_BLOCK_TYPE | SqlGuard | 语句类型与调用场景不符 |
| SQL_BLOCK_NO_WHERE | SqlGuard | UPDATE/DELETE 缺少 WHERE |
| SQL_BLOCK_PSEUDO_WHERE | SqlGuard | WHERE 为伪过滤（1=1/TRUE） |
| SQL_BLOCK_INSERT_SELECT | SqlGuard | 禁止 INSERT ... SELECT |
| SQL_BLOCK_BATCH_TOO_LARGE | SqlGuard | 批量 INSERT 超过 batchInsertMaxRows |
| SQL_BLOCK_CREATE_AS_SELECT | SqlGuard | 禁止 CREATE TABLE ... AS SELECT |
| SQL_BLOCK_PARSE | SqlGuard | JSqlParser 解析失败 |
| SQL_BLOCK_DIALECT | Service | 方言级安全规则拦截 |
| SQL_EXECUTE_ERROR | Executor | 通用 SQL 执行错误 |
| SQL_EXECUTE_ROLLBACK | MySqlWriteExecutor | 写入失败已回滚 |
| SQL_EXECUTE_PARTIAL | TDengineWriteExecutor | 写入失败无事务，已执行未回滚 |
| DATASOURCE_UNAVAILABLE | HealthService | 健康检查失败 |
| DATASOURCE_PERMISSION_DENIED | Adapter | 账号权限不足 |
| DATASOURCE_NOT_FOUND | DataSourceManager | 数据源名称不存在 |
| BUSINESS_OBJECT_NOT_FOUND | DescribeObjectService | 表/视图不存在 |
| CACHE_NOT_READY | ListTablesService 等 | 元数据缓存未就绪 |
| PARTIAL_SUCCESS | RefreshMetadataCacheService | 部分数据源刷新成功 |

---

### P2-5 文档承诺“测试通过”不等于覆盖率（仍成立）

`test-report.md` 当前已更新为 60 通过，并补了权限异常 / 事务回滚 / 健康检查 / 缓存并发 / TDengine 写入等关键路径测试；但“通过数”本身仍不能替代覆盖率或发布闸门。

**建议**：
- 引入 JaCoCo 行覆盖率报告（pom.xml 加 jacoco-maven-plugin）。
- 设立"关键路径覆盖清单"（见 P1-1 表格），作为发布闸门而非依赖测试数量。
- 在 test-report.md 中列出**分支覆盖率**而非仅"通过数"。

---

## 附录：误报澄清（子代理报告中的误判）

为避免误导，列出 review 过程中发现的**子代理误报**，这些项**不是真问题**：

1. **countBatchInsertRows 字符串内括号误计**（子代理 P0）
   - 验证：`INSERT INTO t VALUES ('(1)', 'x')` 经 depth 机制实际只算 1 行（内层 `(` 在 depth > 0 时不增 rows）。当前实现正确。

2. **SQLException errorCode 参数顺序错误**（子代理 P2）
   - 验证：`SQLException(msg, sqlState, cause)` 重载中第二参数是 SQLState 不是 errorCode；子代理把两者搞混。真正的问题是 SQLState 值非标准（见本报告 P2-1）。

3. **权限错误 datasourceName 为空影响审计日志**（子代理 P0）
   - 验证：Service 层 catch 后用自己的 datasourceName 调 AuditLogger，审计日志实际是正确的。问题只是异常上的字段没人用（见本报告 P2-2）。

4. **AS SELECT 检测的 `contains(" AS SELECT")` 可以被 `AS\nSELECT` 绕过**（子代理 P0）
   - 验证：历史上确实是真问题；当前版本已修复（见本报告 P0-1）。

---

## 结论

项目核心架构良好，安全设计思路正确。原始两个 P0 风险已经修复，文档主要口径也已同步；当前剩余问题集中在集成测试、TDengine 更完整覆盖、以及少量工程化改进项。

测试工程视角的核心建议：**不要用测试数量作为质量证明，要用关键路径覆盖清单或覆盖率门禁**。当前 60 个测试已覆盖主要单元级风险与基础集成场景，但离“有集成发布门禁”的成熟状态还有距离。

