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
