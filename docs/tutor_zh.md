# 教学演示

这份教程的目标是帮你从零到可用地跑通 LinkMind，同时不再把产品里仍然存在的功能过度删减。建议先读 **基础（Essential）**，再继续读 **进阶（Advanced）**。

## 第一部分：基础（Essential）

### 1.1 先启动 LinkMind

先按[快速开始](guide_zh.md)和[部署、安全与级联组网](deployment-and-security_zh.md)启动 LinkMind。下面 4 种方式是并列选项，任选其一即可：

- 官方安装脚本
- 预打包 JAR
- Docker 镜像
- 源码编译

服务启动后，浏览器打开：

- `http://localhost:8080`

### 1.2 完成第一份可用配置

开始测试之前，请先确保至少配置了一个真实可用的模型密钥。

最短路径通常是：

1. 登录控制台。
2. 打开模型或 API Key 设置页。
3. 填入一个模型厂商的真实密钥。
4. 在 `lagi.yml` 中启用一个聊天后端。

一个最小可用聊天配置示例如下：

```yaml
models:
  - name: qwen
    type: Alibaba
    enable: true
    model: qwen-plus,qwen-max
    driver: ai.wrapper.impl.AlibabaAdapter
    api_key: your-api-key
    # 如果有多个 Key，可改用 Key 池：
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

### 1.3 先在控制台验证聊天

回到聊天页，发送一条简单消息，例如：

- `请用一段话介绍 LinkMind。`

如果能正常得到回复，说明第一条模型链路已经跑通。

### 1.4 再验证 HTTP 接口

#### LinkMind 原生路由

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

#### OpenAI Chat Completions 兼容路由

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

#### Anthropic Messages 兼容路由

支持 Anthropic Messages 协议的客户端可调用 `/v1/messages`。该接口需要 `max_tokens`；模型名应替换为当前已启用的聊天模型。

```bash
curl http://localhost:8080/v1/messages \
  -H "Content-Type: application/json" \
  -d '{
    "model": "qwen-plus",
    "max_tokens": 256,
    "stream": false,
    "messages": [
      {"role": "user", "content": "列出 LinkMind 的三个核心能力。"}
    ]
  }'
```

将 `stream` 改为 `true` 可接收 Messages 格式的 SSE 流式事件。请求字段与返回格式见 [API 参考](API_zh.md#post-anthropic-messages)；Claude Code 接入步骤见[配置参考](config_zh.md#claude-code-和-gemini-cli)。OpenAI Responses 客户端也可使用 `/v1/responses`，配置方法见[配置参考中的 Codex 部分](config_zh.md#codex)。

如果系统开启了鉴权，上述请求可加上以下请求头；Messages 接口也接受 `x-api-key: <你的-linkmind-api-key>`：

```http
Authorization: Bearer <你的-linkmind-api-key>
```

### 1.5 启用 RAG

如果你希望回答建立在自己的知识数据上，建议按这个顺序做：

1. 启动 Chroma。
2. 把 `stores.vector[*].url` 指向 Chroma。
3. 打开 `stores.rag`。
4. 配置一个 Embedding 后端。
5. 通过控制台或向量接口写入知识内容。

Chroma 快速启动：

```bash
pip install chromadb
mkdir db_data
chroma run --path db_data
```

然后补上：

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

### 1.6 试一下多模态能力

当前服务端仍然保留这些常见能力：

- `POST /audio/speech2text`
- `GET /audio/text2speech`
- `POST /image/text2image`
- `POST /image/image2ocr`
- `POST /image/image2text`
- `POST /image/image2enhance`
- `POST /image/image2video`
- `POST /video/video2tracking`
- `POST /video/video2enhance`
- `POST /ocr/doc2ocr`
- `POST /doc/doc2ext`
- `POST /doc/doc2struct`
- `POST /sql/text2sql`

具体请求示例请查看 [API 参考](API_zh.md)。

### 1.7 可选：接入 Agent 运行时

如果你的本地工作流已经有 OpenClaw、Hermes Agent、DeerFlow 或 OpenHuman，请直接查看[智能体、工具与任务编排](agents-and-tools_zh.md)及其中的客户端实操。

1. 重新安装或重启统一的 LinkMind，并在安装器中按需选择 Agent 前端接入。
2. 检查运行时配置路径是否正确。
3. 让 LinkMind 充当统一中间层，而不是业务系统分别直连各个模型厂商。

### 1.8 下一步建议

- 调整模型和路由：看[模型接入、路由与多模态](models-and-multimodal_zh.md)
- 配置检索：看[知识库](knowledge-base_zh.md)
- 接入自己的业务系统：看[系统集成与二次开发](integration_zh.md)
- 扩展模型或向量库：看[更多功能接入与自定义扩展](extensions_zh.md)

## 第二部分：进阶（Advanced）

### 2.1 从源码构建、IDE 调试或部署 WAR

如果你需要更典型的开发者工作流，这些方式依然有效。

#### Maven 打包

```bash
git clone https://github.com/landingbj/lagi.git
cd lagi
mvn clean package -pl lagi-web -am -DskipTests -U
```

构建产物：

- `lagi-web/target/LinkMind.jar`
- `lagi-web/target/ROOT.war`

#### IDE 调试

你仍然可以把项目导入 IntelliJ IDEA 或 Eclipse，本地编译并按自己的调试配置启动。

#### WAR / Tomcat 部署

如果团队依然偏向传统 Servlet 容器：

1. 构建 `ROOT.war`。
2. 放入 Tomcat 的 `webapps`。
3. 保持 `lagi.yml` 与独立 JAR 启动时相同的模型与存储配置。

### 2.2 模型切换与路由编排

LinkMind 并不只支持一个聊天后端。你可以保留多个模型后端，再用路由规则统一调度。

示例：

```yaml
functions:
  chat:
    route: best((landing&qwen),(kimi|chatgpt))
    backends:
      - backend: landing
        model: cascade
        enable: true
        stream: true
        priority: 350

      - backend: qwen
        model: qwen-plus
        enable: true
        stream: true
        priority: 100

      - backend: kimi
        model: moonshot-v1-8k
        enable: true
        stream: true
        priority: 90
```

常见路由规则：

- `A|B`：轮询
- `A,B`：故障转移
- `A&B`：并行

### 2.3 私训问答对

私训问答对仍然是产品能力的一部分，尤其适合需要把结构化业务知识快速接入的场景，不应该从教程里删掉。

推荐流程：

1. 准备领域 FAQ 或人工整理好的问答对。
2. 用 LinkMind 把它们整理成清晰、可复用的 QA 数据。
3. 写入对应知识分类，让 RAG 在对话中检索使用。

#### 私训数据处理架构图

![私训架构图](images/img_5.png)

#### 私训数据处理流程图

![私训流程图](images/img_6.png)

#### 实操建议

- 一条问答尽量只覆盖一个明确主题。
- 问题尽量按真实用户提问方式来写。
- 答案先短后长，先保证可检索性，再补充完整解释。
- 不同业务域尽量拆分不同分类，减少互相干扰。

### 2.4 生成指令集

当你希望把文档进一步转成适合训练或抽取 QA 的素材时，可以使用指令集生成功能。

通常建议遵循这些抽取原则：

1. 从原始文档中提取结构清晰的问题和答案。
2. 将关键信息压缩成准确、简洁的回答。
3. 保留后续训练或检索所需的必要上下文。
4. 按主题拆分，而不是整份文档一次性塞成一个块。

具体请求方式可查看 [API 参考](API_zh.md)。

### 2.5 上传私训学习文件

上传私训学习文件依然是构建内部知识库的重要路径。

你可以通过控制台完成，也可以根据工作流选择 `/uploadFile/*`、`/training/*` 以及文档/向量相关接口来做文件入库。

#### 支持的文件格式

- 文本类：`txt`、`doc`、`docx`、`pdf`
- 表格类：`xls`、`xlsx`、`csv`
- 图片类：`jpeg`、`png`、`jpg`、`webp`
- 演示文稿：`ppt`、`pptx`

#### 文件处理策略

不同文件类别仍然采用不同的处理方式：

1. 问答文件：自动抽取并拆分问答对。
2. 章节型文档：清理结构噪音后，尽量保留完整段落。
3. 表格与电子表：转成更适合模型理解的结构化内容。
4. 纯数字型表格：可与 text-to-SQL、关系库能力配合使用。
5. 图文混排文件：联合 OCR 和版面理解一起处理。
6. 标题明显的文件：把标题保留为独立知识锚点。
7. 演示文稿：按页处理文本与图片内容。
8. 纯图片文件：通过 OCR 或图像理解转成可检索内容。

## 第三部分：智能体客户端接入实操

OpenClaw、Hermes Agent、DeerFlow、OpenHuman 的安装、配置与验证步骤已迁移到[智能体、工具与任务编排](agents-and-tools_zh.md)及其独立的最佳实践页面。教学演示保留 LinkMind 基础能力与接口上手内容，客户端操作教程在最佳实践目录中独立维护。

## 第四部分：AI Agents Project Best Practice 延伸阅读

完成基础接入和客户端验证后，可以继续阅读下面这组 Best Practice。它们从单 Agent 可靠调用开始，逐步覆盖私有知识注入、Skills / Tools / MCP、Guardrails 与审计、路由编排、多 Agent 协作，以及 Agentic Social 场景，适合在 POC 之后规划生产级 Agent 项目时参考。

1. [Part 01：From Model Call to Reliable Single Agent](best-practices/01-reliable-single-agent.pdf)
2. [Part 02：Injecting Private Knowledge without Retraining](best-practices/02-private-knowledge-and-rag.pdf)
3. [Part 03：Injecting Private Capabilities with Skills, Tools and MCP](best-practices/03-skills-tools-and-mcp.pdf)
4. [Part 04：Safety by Design, Guardrails, Permissions and Audit](best-practices/04-agent-security-and-audit.pdf)
5. [Part 05：Routing, Orchestration and Multi-Agent Teamwork](best-practices/05-routing-and-multi-agent-orchestration.pdf)
6. [Part 06：Agentic Social, Agents in Shared Human Contexts](best-practices/06-agentic-social.pdf)
