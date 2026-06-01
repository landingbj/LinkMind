# Benchmark 脚本说明

本目录用于放置基准/并发测试相关内容：脚本、测试数据和运行结果都保留在 `tools/benchmark` 下。当前只有一个脚本：

- `concurrent_testing.py`：并发请求 `http://127.0.0.1:8080/chat/completions`，用于观察聊天接口在固定并发下的响应耗时和 token 生成速度。

## 环境准备

脚本依赖 Python 和 `requests`：

```powershell
python --version
pip install requests
```

运行前需要先启动被测服务，并确认本机可访问：

```text
http://127.0.0.1:8080/chat/completions
```

如果接口地址、鉴权或模型不同，先编辑 `concurrent_testing.py` 顶部/配置区：

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `COMPLETIONS_URL` | `http://127.0.0.1:8080/chat/completions` | 被测接口地址 |
| `API_KEY` | 空字符串 | 作为 `Authorization: Bearer <API_KEY>` 发送 |
| `MODEL` | `None` | 非空时会写入请求体的 `model` 字段 |
| `MAX_THREADS` | `25` | 每批最大并发数 |
| `FETCH_TIMES` | `5` | 执行批次数 |
| `RANDOM_TEST` | `True` | 每个请求从问题池随机抽取问题 |

默认配置下，一次运行最多会发起 `MAX_THREADS * FETCH_TIMES`，也就是 125 个请求。

## 测试数据

测试问题目前直接写在 `concurrent_testing.py` 的 `questions` 数组中，没有单独的数据文件。后续如果需要维护多套问题集，建议仍放在本目录，例如：

```text
tools/benchmark/data/basic_questions.json
tools/benchmark/data/domain_questions.json
```

这样脚本、数据和结果都能一起归档。

## 运行方式

建议从仓库根目录进入 benchmark 目录后运行，避免输出文件散落到其他位置：

```powershell
cd tools\benchmark
python .\concurrent_testing.py
```

脚本默认把请求体、响应状态码、原始响应、解析后的 JSON、单请求耗时和批次平均值打印到控制台。为了把运行结果保存在 benchmark 目录，可以重定向到日志文件：

```powershell
cd tools\benchmark
python .\concurrent_testing.py *> .\run-20260529-1400.log
```

Linux/macOS 或 Git Bash 可以使用：

```bash
cd tools/benchmark
python concurrent_testing.py 2>&1 | tee run-20260529-1400.log
```

每次对比性能时，建议用新的日志文件名记录日期、并发数、模型或版本，例如：

```text
run-20260529-threads25-fetch5-baseline.log
run-20260529-threads25-fetch5-after-cache.log
```

## 结果怎么解读

每个请求都会带一个 `QID`，用于把同一次请求的日志串起来。重点看这些输出：

- `响应状态码`：`200` 表示接口成功返回；非 `200` 会抛出 HTTP 异常并打印堆栈。
- `原始响应全文` / `请求结果`：确认返回结构是否符合预期，尤其是 `usage.completion_tokens` 是否存在。
- `总耗时`：单个请求从发送到收到完整响应的耗时，单位是秒。
- `请求速度`：单请求 `completion_tokens / 总耗时`，只有响应里包含 `usage.completion_tokens` 时才会打印。
- `平均每秒 token 数`：当前批次所有成功请求的 `completion_tokens` 之和，除以这些请求耗时之和。
- `平均每个请求耗时`：当前批次成功请求耗时之和，除以该批请求数。

注意：脚本按并发方式执行，但批次平均值使用的是“各请求耗时之和”，不是整批 wall-clock 耗时。因此它适合粗略观察单请求延迟和 token 速度，不等同于服务整体吞吐上限。

## 对比建议

做性能对比时尽量固定这些条件：

- 相同的 `MAX_THREADS`、`FETCH_TIMES`、接口地址、模型、请求体参数和问题池。
- 相同的机器、网络、服务版本、缓存状态和预热方式。
- 至少保留优化前后两份日志，并记录变更点。
- 同时关注失败请求、超时、p95/p99 等尾延迟；当前脚本没有自动统计分位数，需要时可以从日志或后续脚本扩展中补充。

一次本机运行只能作为基线或回归线索，不能直接代表生产容量。要给出容量结论，还需要真实流量模型、压测端资源、服务端 CPU/内存/IO、下游依赖和错误率等证据。
