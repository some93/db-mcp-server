# Week 1 实现任务

## 已完成
- [x] SPI 层接口定义（DatasourceAdapter、MetadataAdapter、QueryExecutor、WriteExecutor、SqlGuardDialectSupport）
- [x] 安全拦截层（SqlGuard：注释清洗、关键字拦截、伪WHERE检测、INSERT SELECT禁止）
- [x] SqlBlockedException（带语义错误码）
- [x] MySqlMetadataAdapter（listTables / describeObject / listIndexes / listViews）
- [x] MySqlQueryExecutor（executeQuery / explainQuery）
- [x] MySqlWriteExecutor（executeWrite 含事务回滚）
- [x] MySqlSqlGuardDialect + MySqlAdapter 聚合
- [x] AdapterRegistry（Spring Bean，按 dialectType 分发）
- [x] MetadataCache（Caffeine 无侵入、启动预热、定时刷新、按数据源隔离）
- [x] HealthService（定时探测 + 调用前快速校验 + 二次确认）
- [x] DatasourceUnavailableException
- [x] McpResponse 统一返回结构
- [x] MarkdownTable 渲染工具
- [x] ListDatasourcesService
- [x] ListTablesService
- [x] ListIndexesAndViewsService
- [x] DescribeObjectService
- [x] ExecuteQueryService（含安全链、截断、markdownTable）
- [x] ExecuteWriteService（多语句分号拆分 + 事务）
- [x] CreateTableService（执行后触发全量元数据刷新）
- [x] ExplainQueryService
- [x] RefreshMetadataCacheService（全量/指定数据源，部分成功处理）
- [x] HTTP McpHttpController（9个 REST 端点）
- [x] HealthController（/health + /ready）
- [x] McpStdioServer（手写 JSON-RPC 2.0，tools/list + tools/call + initialize）
- [x] HttpPortLogger（启动时打印实际端口）
- [x] @EnableScheduling 注解

## 待实现（Week 2）
- [x] locateTable（编辑距离模糊匹配）
- [x] getServiceStatus MCP 工具 + HTTP 端点
- [x] executeWrite 批量 INSERT 行数上限检查

## 待实现（Week 3）
- [x] TDengine 适配器（SPI 插拔，RESTful 连接）
- [x] 结构化审计日志（AuditLogger，SLF4J，8 个 Service）
- [x] 启动时 MySQL 权限探测（DataSourceManager.probePermissions）
- [x] 运行时权限错误识别（DatasourcePermissionException + MySqlPermissionErrors）
- [ ] 压测与稳定性验证（已补 `scripts/http-smoke-load.ps1` 和 `doc/smoke-load-test.md`，待真实环境执行）

## Review
- `mvn test` 当前已通过，自动化测试总数 42，0 失败，0 错误。
- 已补 executeWrite 分号拆分边界测试与 HTTP 缺参校验测试，降低回归风险。
- 已补轻量 HTTP 冒烟/并发脚本，便于在真实数据库环境下推进最后一项稳定性验证。
- 已为主代码类注释补齐 `@author ouyanghang`，并合并重复的类级 Javadoc；复核后仅 `package-info.java` 未添加该标签。

## 当前复核计划（review-report）
- [x] 复核 P0：`SqlGuard` 的 `CREATE TABLE ... AS SELECT` 拦截与 `ExecuteWriteService.splitStatements()` 现状
- [x] 复核 P1：权限异常、事务回滚、健康检查的实现与测试覆盖
- [x] 复核 P2：文档口径、错误码表、实现细节差异

## 本轮复核结论（review-report）
- `SqlGuard.checkCreateTable` 的 `AS SELECT` 仍是字符串包含判断，`AS\\nSELECT` / `AS\\tSELECT` 仍可绕过。
- `ExecuteWriteService.splitStatements()` 仍未处理反斜杠转义，`\\'` 场景下仍可能误拆分或误判引号边界。
- 报告关于“权限异常 / 事务回滚 / 健康检查 / TDengine 写入缺少自动化测试”的判断仍成立；当前测试文件仍只有 5 个。
- `HealthService` 的 `isValid(false)` 失败分支已修复，因此 review-report 中这部分旧风险已失效。
- `README.md`、`doc/RequirementDocument.md`、`doc/test-report.md` 的口径问题仍部分存在，尤其是工具/接口数量说明与错误码速查表缺失。

## 本轮处理结果
- 已将 `SqlGuard.checkCreateTable` 改为正则匹配 `AS\\s*(?:\\(\\s*)?SELECT`，补上换行、Tab、多空格与括号场景测试。
- 已为 `ExecuteWriteService.splitStatements()` 增加单/双引号内反斜杠转义处理，补上 `\\'` 和 `\\\"` 场景测试。
- 已新增关键路径测试：`MySqlQueryExecutorTest`、`MySqlWriteExecutorTest`、`MySqlMetadataAdapterTest`、`HealthServiceTest`、`TDengineWriteExecutorTest`。
- 当前 `mvn test` 已通过，自动化测试总数 56，0 失败，0 错误。

## 本轮文档同步
- 已统一 `README.md` 与 `doc/RequirementDocument.md` 的数量口径：11 个 MCP 工具 + 2 个 HTTP 健康端点 = 13 个对外接口。
- 已为 `README.md` 与 `doc/RequirementDocument.md` 补充常用错误码/速查表，便于排障。
- 已更新 `doc/test-report.md`：测试总数调整为 56，并补录新增的权限、事务、健康检查、TDengine 关键路径覆盖。
- 已修正 `doc/test-report.md` 附录中过时的 P0 结论，将已修复问题改为“已关闭并有回归测试”，避免与当前代码状态冲突。
- 已同步修正 `doc/review-report.md`：移除已过时的 P0/42 测试结论，改为反映当前 56 测试、已关闭风险与剩余集成/TDengine 覆盖缺口。

## 当前执行计划
- [x] 将 `DebugController` 重命名为 `McpHttpController`，并同步代码与文档设计描述
- [x] 巡检 `README.md` / `doc/RequirementDocument.md` / `doc/test-report.md` / `doc/review-report.md` 四份文档的一致性
- [x] 补事务回滚、健康恢复、缓存并发三类集成测试并完成验证

## 本轮最终结果
- 已将 `DebugController` / `DebugControllerTest` 重命名为 `McpHttpController` / `McpHttpControllerTest`，并同步 README、测试报告、review 报告中的类名。
- 已补基础集成测试：`MySqlWriteExecutorIntegrationTest`、`HealthServiceIntegrationTest`、`MetadataCacheIntegrationTest`。
- 已执行 `mvn clean test`，当前自动化测试总数 60，0 失败，0 错误。
- 四份核心文档已对齐：接口数量口径、控制器命名、测试总数、已修复风险与剩余缺口保持一致。

