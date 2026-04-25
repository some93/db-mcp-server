# HTTP 冒烟/轻压测说明

本文档配套 `scripts/http-smoke-load.ps1` 使用，用于在已有服务实例时做一轮快速验证。

## 目标

- 验证 HTTP 入口可用
- 验证查询接口在短时并发下稳定
- 验证统一返回结构未漂移

## 使用前提

- 服务已启动
- 已知一个可用 `datasourceName`
- 已知一条可执行的只读 SQL

## 示例

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\http-smoke-load.ps1 `
  -BaseUrl "http://127.0.0.1:11198" `
  -DatasourceName "some_db" `
  -Sql "SELECT 1" `
  -Iterations 20 `
  -Concurrency 4
```

## 可直接执行的命令模板

到公司后，先把下面 3 个参数替换成你的真实环境：

- `BaseUrl`：服务实际地址，例如 `http://127.0.0.1:11198`
- `DatasourceName`：目标数据源名称，例如 `some_db`
- `Sql`：只读 SQL，建议先用 `SELECT 1`

### 冒烟测试

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\http-smoke-load.ps1 `
  -BaseUrl "http://127.0.0.1:11198" `
  -DatasourceName "some_db" `
  -Sql "SELECT 1" `
  -Iterations 20 `
  -Concurrency 4
```

### 轻压测

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\http-smoke-load.ps1 `
  -BaseUrl "http://127.0.0.1:11198" `
  -DatasourceName "some_db" `
  -Sql "SELECT 1" `
  -Iterations 100 `
  -Concurrency 10
```

## 输出

脚本会输出：

- `/health` 和 `/api/status` 是否通过
- 查询总请求数、成功数、失败数
- 总耗时、平均耗时、最大耗时

## 建议阈值

- 冒烟阶段：`Iterations=20`，`Concurrency=4`
- 轻压测阶段：`Iterations=100`，`Concurrency=10`

## 注意

- 该脚本只做只读请求，不执行写入。
- 该脚本不是正式基准压测工具，只用于上线前快速冒烟。

---

## 本次真实环境执行结果（2026-04-24）

执行环境：

- 配置文件：`src/main/resources/application.yml`
- 服务地址：`http://127.0.0.1:9348`
- 数据源：`root_db`、`some_db`

### 1) `root_db` 冒烟测试

命令：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\http-smoke-load.ps1 `
  -BaseUrl "http://127.0.0.1:9348" `
  -DatasourceName "root_db" `
  -Sql "SELECT 1" `
  -Iterations 20 `
  -Concurrency 4
```

结果：

- `/health`：成功
- `/api/status`：成功
- 请求数：20
- 成功数：20
- 失败数：0
- 总耗时：12529 ms
- 平均耗时：211 ms
- 最大耗时：253 ms

### 2) `some_db` 轻压测

命令：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\http-smoke-load.ps1 `
  -BaseUrl "http://127.0.0.1:9348" `
  -DatasourceName "some_db" `
  -Sql "SELECT 1" `
  -Iterations 100 `
  -Concurrency 10
```

结果：

- `/health`：成功
- `/api/status`：成功
- 请求数：100
- 成功数：100
- 失败数：0
- 总耗时：30360 ms
- 平均耗时：193 ms
- 最大耗时：361 ms

结论：

- 两个真实 MySQL 数据源均可正常初始化并通过权限探测
- HTTP 调试接口和健康接口在真实环境下可用
- 轻压测阶段未出现失败请求，当前版本达到“上线前快速冒烟/轻压测通过”的目标
