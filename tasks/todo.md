# 项目里程碑

## 当前状态
- 项目核心需求已全部完成，原 Week 1 / Week 2 / Week 3 任务均已落地。
- HTTP + SSE MCP 已完成 Phase 1 同步入口和 Phase 2 异步 `tools/call`。
- 真实 MySQL 环境冒烟与轻压测已执行完成，结果记录见 `doc/smoke-load-test.md`。
- 当前自动化测试：`mvn test` 共 74 项，0 失败，0 错误。

## 已完成里程碑

### 核心数据库网关
- SPI 层：`DatasourceAdapter`、`MetadataAdapter`、`QueryExecutor`、`WriteExecutor`、`SqlGuardDialectSupport`
- MySQL 读写/元数据能力：查询、执行计划、事务写入、建表、索引/视图、对象描述
- TDengine 适配器：SPI 接入、查询/写入/元数据基础支持、方言级 JOIN 拦截
- 统一返回结构：`McpResponse`

### 安全与可用性
- SQL 安全拦截：危险关键字、伪 WHERE、`INSERT ... SELECT`、`CREATE TABLE ... AS SELECT`
- 批量 INSERT 行数上限保护
- 元数据缓存：启动预热、定时刷新、按数据源隔离、部分失败处理
- 健康检查：定时探测 + 调用前快速校验 + 二次确认
- MySQL 权限探测与运行时权限错误识别
- 审计日志：8 个 service 结构化审计输出

### 传输与接口
- `stdio MCP`
- `HTTP MCP`
- `HTTP + SSE MCP`
- 本机 `/api/*` 调试接口
- 健康端点：`/health`、`/ready`
- HTTP MCP 路由支持配置化：`mcp.http.path`、`mcp.http.ssePath`
- SSE 异步回传：`tools/call` 支持 `async=true` + `X-Mcp-Session-Id`

### 文档与示例
- `README.md`
- `doc/RequirementDocument.md`
- `doc/McpHttpSseDesign.md`
- `doc/test-report.md`
- `doc/review-report.md`
- `doc/smoke-load-test.md`
- 配置示例：`doc/example-config.yml`、`doc/example-config-stdio.yml`、`doc/example-config-http-sse.yml`

### 测试与验证
- 单元测试、集成测试、HTTP MCP 路由测试、SSE session 测试、异步 `tools/call` 测试
- 真实环境压测：
  - `root_db` 冒烟：20 请求 / 并发 4 / 100% 成功
  - `some_db` 轻压测：100 请求 / 并发 10 / 100% 成功

## 当前残余风险
- TDengine 测试覆盖仍弱于 MySQL，尤其是元数据、查询、权限/JOIN 拦截的更完整自动化覆盖。
- 目前没有 JaCoCo 覆盖率门禁，质量判断仍主要依赖关键路径测试清单。
- `test-report.md` / `review-report.md` 中的部分“改进建议”属于后续工程化优化，不影响当前需求完成状态。

## 建议的后续工作
- 补 TDengine 元数据、查询、JOIN 拦截相关自动化测试
- 引入 JaCoCo 覆盖率报告和发布门禁
- 继续完善发布前检查清单与回滚说明
