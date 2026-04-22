# 教学演示

本文是从零到可用的最短实践路径，目标不是讲完所有细节，而是帮助你尽快把 LinkMind 跑起来并验证关键能力。

## 一、先启动 LinkMind

先按 [安装指南](install_zh.md) 完成启动。安装指南里有 4 种并列方式可选：

- 官方安装脚本
- 预打包 JAR
- Docker 镜像
- 源码编译

服务启动后，浏览器打开：

- `http://localhost:8080`

## 二、完成第一份可用配置

在开始测试前，请先确保至少有一个真实可用的模型密钥。

最简单的做法是：

1. 登录控制台
2. 打开模型或 API Key 设置页
3. 填入一个模型厂商的真实密钥
4. 在 `lagi.yml` 中启用一个聊天后端

一个最小聊天配置示例如下：

```yaml
models:
  - name: qwen
    type: Alibaba
    enable: true
    model: qwen-plus,qwen-max
    driver: ai.wrapper.impl.AlibabaAdapter
    api_key: your-api-key
    # 如果有多个 Key，可以改用 Key 池：
    # api_keys: sk-key1,sk-key2,sk-key3
    # key_route: polling  # polling（轮询）或 failover（故障转移）

functions:
  chat:
    route: pass(qwen)
    backends:
      - backend: qwen
        model: qwen-plus
        enable: true
        stream: true
        priority: 100

routers:
  enable: true
  items:
    - name: pass
      rule: (%)
```

## 三、先在控制台验证聊天

回到聊天页面，发送一条简单消息，例如：

- `请用一段话介绍 LinkMind。`

如果能正常得到回答，说明第一条模型链路已经通了。

## 四、再验证 HTTP 接口

### LinkMind 原生路由

```bash
curl http://localhost:8080/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "qwen-plus",
    "stream": false,
    "messages": [
      {"role": "user", "content": "列出 LinkMind 的三个核心能力。"}
    ]
  }'
```

### OpenAI 兼容路由

```bash
curl http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "qwen-plus",
    "stream": false,
    "messages": [
      {"role": "user", "content": "列出 LinkMind 的三个核心能力。"}
    ]
  }'
```

如果系统开启了鉴权，请加上：

```http
Authorization: Bearer <你的-linkmind-api-key>
```

## 五、启用 RAG

如果你希望回答建立在自己的知识数据上，请按这个顺序做：

1. 启动 Chroma
2. 在 `stores.vector[*].url` 中填入 Chroma 地址
3. 打开 `stores.rag`
4. 配置一个 Embedding 后端
5. 通过控制台或向量接口写入知识数据

Chroma 最快启动命令：

```bash
pip install chromadb
mkdir db_data
chroma run --path db_data
```

对应配置：

```yaml
stores:
  vector:
    - name: chroma
      driver: ai.vector.impl.ChromaVectorStore
      url: http://localhost:8000

  rag:
    vector: chroma
    enable: true
```

## 六、试一下多模态能力

当前服务端已经开放这些常用能力：

- `POST /audio/speech2text`
- `GET /audio/text2speech`
- `POST /image/text2image`
- `POST /image/image2ocr`
- `POST /ocr/doc2ocr`
- `POST /doc/doc2ext`
- `POST /doc/doc2struct`
- `POST /sql/text2sql`

各接口的请求示例见 [API 参考](API_zh.md)。

## 七、可选：接入 Agent 运行时

如果你的本地工作流已经有 OpenClaw、Hermes Agent 或 DeerFlow：

1. 重新以 `Agent Mate` 模式安装或启动 LinkMind
2. 确认对应运行时的配置路径正确
3. 让 LinkMind 成为统一的中间层，而不是让每个业务系统分别直连不同模型厂商

## 八、下一步建议

- 继续调模型、路由、过滤器、RAG：看 [配置参考](config_zh.md)
- 接入你的业务系统：看 [开发集成指南](guide_zh.md)
- 扩展新的模型或向量库：看 [扩展开发文档](extend_zh.md)
