# NexRouter 路由与连通性测试记录

- 测试时间：2026-11-20 11:18-11:23（Asia/Shanghai）
- 发起服务器：211.149.178.30
- 测试目标：`api.nexrouter.ai`
- 固定解析：`3.114.70.62 api.nexrouter.ai`
- hosts 备份：`C:\Windows\System32\drivers\etc\hosts.linkmind-nexrouter-20261120-111852.bak`

## DNS 说明

`ai.nexrouter.ai` 在 223.5.5.5、119.29.29.29、1.1.1.1、8.8.8.8 均返回 NXDOMAIN。
实际可用入口为 `api.nexrouter.ai`，公共 DNS 将其解析为 AWS 东京 ELB：`3.114.70.62`。

## 路由跟踪

执行命令：

```text
tracert -4 -d -h 30 -w 1500 api.nexrouter.ai
```

完整输出：

```text
Tracing route to api.nexrouter.ai [3.114.70.62]
over a maximum of 30 hops:

  1     *        *        *     Request timed out.
  2     *        *        *     Request timed out.
  3     *        *        *     Request timed out.
  4     *        2 ms     1 ms  118.123.230.21
  5     *        *        1 ms  139.206.135.133
  6     *        *        *     Request timed out.
  7     *        *        *     Request timed out.
  8     *        *        *     Request timed out.
  9    83 ms    83 ms    83 ms  202.97.116.198
 10     *        *        *     Request timed out.
 11     *        *        *     Request timed out.
 12     *        *        *     Request timed out.
 13     *        *        *     Request timed out.
 14     *        *        *     Request timed out.
 15     *        *        *     Request timed out.
 16     *        *        *     Request timed out.
 17     *        *        *     Request timed out.
 18     *        *        *     Request timed out.
 19     *        *        *     Request timed out.
 20     *        *        *     Request timed out.
 21     *        *        *     Request timed out.
 22     *        *        *     Request timed out.
 23     *        *        *     Request timed out.
 24     *        *        *     Request timed out.
 25     *        *        *     Request timed out.
 26     *        *        *     Request timed out.
 27     *        *        *     Request timed out.
 28     *        *        *     Request timed out.
 29     *        *        *     Request timed out.
 30     *        *        *     Request timed out.

Trace complete.
```

## TCP 443 连通性

对 `3.114.70.62:443` 连续建立 10 次 TCP 连接，全部成功：

```text
111 ms, 86 ms, 79 ms, 90 ms, 87 ms,
80 ms, 106 ms, 72 ms, 82 ms, 96 ms
```

- 成功率：10/10
- 最低：72 ms
- 最高：111 ms
- 平均：88.9 ms
- TCP 连接超时：0 次

## 结论

从第 10 跳起的 `*` 表示中间设备或 AWS ELB 不响应 ICMP TTL 超时报文，不能据此判定 TCP/HTTPS 链路中断。目标 `3.114.70.62:443` 的 TCP 连接稳定成功，因此不存在到该目标的 TCP 建连超时。

如仍存在首包慢或流式速度异常，应由云服务商检查 211 到 AWS 东京 `3.114.70.62:443` 的国际出口质量（丢包、抖动、重传、BGP 路由），并向 NexRouter 索取更近区域入口或链路优化方案。
