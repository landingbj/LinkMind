# 教学演示

这份教程的目标是帮你从零到可用地跑通 LinkMind，同时不再把产品里仍然存在的功能过度删减。建议先读 **基础（Essential）**，再继续读 **进阶（Advanced）**。

## 第一部分：基础（Essential）

### 1.1 先启动 LinkMind

先按 [安装指南](install_zh.md) 启动 LinkMind。下面 4 种方式是并列选项，任选其一即可：

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

#### OpenAI 兼容路由

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

如果你的本地工作流已经有 OpenClaw、Hermes Agent、DeerFlow 或 OpenHuman：

1. 以 `Agent Mate` 模式重新安装或重启 LinkMind。
2. 检查运行时配置路径是否正确。
3. 让 LinkMind 充当统一中间层，而不是业务系统分别直连各个模型厂商。

### 1.8 下一步建议

- 调整模型、路由、过滤器和 RAG：看 [配置参考](config_zh.md)
- 接入自己的业务系统：看 [开发集成指南](guide_zh.md)
- 扩展模型或向量库：看 [扩展开发文档](extend_zh.md)

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

## 第三部分：智能体客户端接入实操（Agent Mate）

本部分用于指导你把 LinkMind 接入 OpenClaw、Hermes Agent、DeerFlow、OpenHuman 四类智能体客户端，并完成从客户端对话到 LinkMind 用量记录的闭环验证。

### 3.1 接入路径总览

四类客户端的整体逻辑一致：先准备客户端运行环境，再以 `Agent Mate` 模式安装 LinkMind，随后在 LinkMind 中配置额度、API Key 和路由，最后把客户端模型请求指向 LinkMind。

| 客户端 | 入口/地址 | LinkMind 安装选择 | 核心配置 | 验证方式 |
| --- | --- | --- | --- | --- |
| OpenClaw | Web UI：`127.0.0.1:18789` | `Agent Mate`，框架选择 `openclaw` | `lagi.yml`，OpenClaw UI 中选择 LinkMind 中间件 | 切换到 `linkmind-Pro` 后在 OpenClaw 对话 |
| Hermes Agent | PowerShell/WSL 终端：`hermes` | `Agent Mate`，框架选择 `hermes` | `lagi.yml`，Hermes `config.yaml` | 在 Hermes 控制台直接输入消息 |
| DeerFlow | 前端：`localhost:3000` 或 `make dev` 输出地址 | `Agent Mate`，框架选择 `deer-flow`，并输入项目绝对路径 | `lagi.yml`，DeerFlow `config.yaml`，`.env` | 在 DeerFlow Chat 页面选择 LinkMind 模型并对话 |
| OpenHuman | 桌面应用 | `Agent Mate`，框架选择 `openhuman`，可选配置路径 | `lagi.yml`，OpenHuman `config.toml` | 在 OpenHuman 发消息，并确认 LinkMind 收到 `/v1/chat/completions` 请求 |

### 3.2 准备智能体客户端

#### OpenClaw 准备

先检查 Node.js、Git 和 PowerShell 脚本执行策略：

```powershell
node -v
git -v
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

如果尚未安装 OpenClaw，可以在 PowerShell 中执行官方安装脚本，按向导选择 QuickStart，填写模型服务商 API Key，并按需跳过即时通讯平台和 Skills。已安装时，先确认版本并启动 Gateway：

```powershell
iwr -useb https://openclaw.ai/install.ps1 | iex

openclaw --version
openclaw gateway start
```

服务启动后，打开 `127.0.0.1:18789`。

![图 1 OpenClaw 版本核验](images/linkmind_client_quickstart_01.png)

![图 2 OpenClaw Gateway 启动窗口](images/linkmind_client_quickstart_02.png)

#### Hermes Agent 准备

确认 `hermes` 命令可执行，并检查版本与状态：

```powershell
where.exe hermes
hermes --version
hermes status
```

![图 3 Hermes Agent 版本与状态核验](images/linkmind_client_quickstart_03.png)

如果 Hermes 运行在 WSL 或虚拟环境中，而 LinkMind 运行在 Windows 本机，需要把 `base_url` 写成 WSL 可访问的 Windows 主机地址，例如 `http://192.168.190.1:8080/v1`。如跨 WSL 访问不通，需要让 LinkMind 监听 `0.0.0.0:8080`，并检查 Windows 防火墙策略；只建议在可信本机网络中这样配置。

#### DeerFlow 准备

DeerFlow 对本机依赖更多：Python 需要 3.12 及以上，Node.js 需要 22 及以上，还需要 `pnpm`、`uv`、`nginx`。安装路径建议使用无中文、无空格的绝对路径。

| 检查项 | 命令/要求 |
| --- | --- |
| Python | `python --version`，要求 3.12 及以上 |
| Node.js | `node -v`，要求 22 及以上 |
| uv | `pip install uv`，再执行 `uv --version` |
| 项目配置 | `git clone https://github.com/bytedance/deer-flow.git`，再执行 `make config` 或 `python scripts/configure.py` |
| 依赖安装 | `make install`；Windows 无 `make` 时，backend 执行 `uv sync`，frontend 执行 `pnpm install` |

常见启动流程：

```bash
git clone https://github.com/bytedance/deer-flow.git
cd deer-flow
pip install uv
make config
make check
make install
make dev
```

![图 4 DeerFlow 服务启动后的访问页面](images/linkmind_client_quickstart_04.png)

#### OpenHuman 准备

从 `tinyhumansai/openhuman` 官方桌面 release 安装 OpenHuman；Windows 使用官方 MSI。启动一次并确认工作区已经生成。LinkMind 会按下面顺序发现 OpenHuman 配置：

1. 安装器或 JAR 参数传入的 `--openhuman-path=`。
2. `OPENHUMAN_WORKSPACE`，包括 workspace 风格路径。
3. `~/.openhuman/active_user.toml` 指向的 `~/.openhuman/users/<user_id>/config.toml`。
4. 登录前默认的 `~/.openhuman/users/local/config.toml`。

OpenHuman 主要通过 `[[cloud_providers]]` 和 `chat_provider` 等 workload 字段完成 provider routing，因此不需要额外新增 OpenHuman 专用 servlet。

### 3.3 安装 LinkMind 并选择接入框架

本节为四类客户端共用步骤。先确认 JDK 8 或以上版本可用，再运行 LinkMind 快速安装脚本：

```bash
java -version
```

Windows PowerShell：

```powershell
iwr -useb https://cdn.linkmind.top/install.ps1 | iex
```

macOS / Linux：

```bash
curl -fsSL https://cdn.linkmind.top/install.sh | bash
```

安装模式统一选择 `Agent Mate`；不同客户端只在 `Inject Agent Framework` 处选择不同框架：

| 当前客户端 | Runtime Choice | Inject Agent Framework | 额外输入 |
| --- | --- | --- | --- |
| OpenClaw | `1) as Agent Mate` | `1) openclaw` | 无 |
| DeerFlow | `1) as Agent Mate` | `2) deer-flow` | 输入 DeerFlow 项目绝对路径，例如 `D:\workspace\code\deer-flow` |
| Hermes Agent | `1) as Agent Mate` | `3) hermes` | 无；若在 WSL 中使用，后续注意 `base_url` 地址 |
| OpenHuman | `1) as Agent Mate` | `4) openhuman` | 可输入 OpenHuman 配置目录或 `config.toml`；留空则自动发现 |

安装完成后可以选择立即启动 LinkMind；如果选择暂不启动，后续进入 LinkMind 目录执行：

```powershell
cd "$env:USERPROFILE\LinkMind"
java -jar LinkMind.jar --enable-sync=false

# PowerShell 中也可以另开进程启动
Start-Process java -ArgumentList "-jar LinkMind.jar --enable-sync=false"
Get-NetTCPConnection -LocalPort 8080 -State Listen
```

服务启动后浏览器访问 `http://localhost:8080`。

![图 5 LinkMind 控制台首页](images/linkmind_client_quickstart_05.png)

![图 6 LinkMind 快速安装命令与结果](images/linkmind_client_quickstart_06.png)

![图 7 DeerFlow 场景下安装最新版 LinkMind](images/linkmind_client_quickstart_07.png)

### 3.4 配置额度、API Key 与路由

四类客户端都通过 LinkMind 统一转发模型调用。先在 LinkMind 控制台确认额度，再在“设置 / API 密钥”中新建 provider 为 Landing 的配置并启用。启用后，LinkMind 会把可用 Key 写入配置；如手工维护配置，需要确认 `chat.route` 指向 `landing` 后端。

推荐检查流程：

1. 打开 `http://localhost:8080`，进入“设置 / 额度中心”，确认账户有可用余额。
2. 进入“设置 / API 密钥”，新增配置，模型提供商选择 Landing，填写便于识别的名称。
3. 新增后在 API 密钥列表中点击启用，必要时复制该 Key 给客户端配置使用。
4. 检查 LinkMind 的 `config/lagi.yml`，确认 `landing` 后端启用，`chat.route` 指向 `landing` 后端。

配置片段示例：

```yaml
functions:
  chat:
    route: "best(landing)"
    backends:
      - backend: "landing"
        enable: true
        model: "Alibaba/qwen-plus"
        api_key: "sk-******"
        stream: true
```

![图 8 在 LinkMind 控制台创建 Landing API Key](images/linkmind_client_quickstart_08.png)

![图 9 API Key 启用后可用于客户端接入](images/linkmind_client_quickstart_09.png)

![图 10 LinkMind landing 路由配置确认](images/linkmind_client_quickstart_10.png)

### 3.5 在客户端接入 LinkMind

#### OpenClaw 客户端操作

1. 打开 OpenClaw Web UI，默认地址为 `127.0.0.1:18789`。
2. 在 OpenClaw 控制台中选择中间件为 LinkMind，完成两端对接。
3. 回到对话页面，将对话模型切换为 `linkmind-Pro` 或安装器同步出的 LinkMind 模型。
4. 发送一条测试消息，确认 OpenClaw 页面正常返回。

![图 11 OpenClaw 使用 LinkMind 模型完成对话](images/linkmind_client_quickstart_11.png)

#### Hermes Agent 客户端操作

Hermes 通过 `config.yaml` 的 custom provider 指向 LinkMind。Windows 本机运行 Hermes 时可使用 `http://localhost:8080/v1`；Hermes 在 WSL 中运行时，通常要改成 Windows 主机 IP。

```yaml
model:
  default: Alibaba/qwen-plus
  provider: custom
  base_url: http://localhost:8080/v1
  api_key: sk-******
  api_mode: chat_completions
  context_length: 64000
```

保存后按 `Ctrl+C` 退出当前 Hermes 会话，再执行：

![图 12 Hermes 模型供应商指向 LinkMind](images/linkmind_client_quickstart_12.png)

```powershell
hermes
```

在 Hermes 控制台直接输入测试消息。如果出现回复，说明 Hermes 已通过 LinkMind 完成一次真实调用。

![图 13 Hermes 控制台直接对话成功](images/linkmind_client_quickstart_13.png)

#### DeerFlow 客户端操作

DeerFlow 可以通过配置文件把模型供应商改为 LinkMind，也可以在界面中选择 LinkMind 中间件。示例链路中 Gateway 监听 `8001`，前端监听 `3000`，前端页面打开 `/workspace/chats/new`。

`config.yaml` 示例：

```yaml
models:
  - name: "linkmind-qwen-plus"
    display_name: "LinkMind Qwen Plus"
    use: "deerflow.models.linkmind_provider:LinkMindChatModel"
    model: "Alibaba/qwen-plus"
    api_key: "$LINKMIND_API_KEY"
    base_url: "http://localhost:8080/v1"
    request_timeout: 600.0
    max_retries: 2
    max_tokens: 4096
    temperature: 0.7
    supports_vision: false
    supports_thinking: false
    disable_streaming: true
```

![图 14 DeerFlow 模型供应商指向 LinkMind](images/linkmind_client_quickstart_14.png)

`.env` 示例：

```env
LINKMIND_API_KEY=********
CORS_ORIGINS=http://localhost:3000,http://127.0.0.1:3000
GATEWAY_CORS_ORIGINS=http://localhost:3000,http://127.0.0.1:3000
DEER_FLOW_INTERNAL_GATEWAY_BASE_URL=http://127.0.0.1:8001
```

启动 DeerFlow Gateway 和前端：

```powershell
cd D:\workspace\deer-flow\backend
$env:PYTHONPATH = "."
uv run uvicorn app.gateway.app:app --host 0.0.0.0 --port 8001

cd D:\workspace\deer-flow\frontend
corepack pnpm run dev
```

![图 15 DeerFlow Gateway 与前端启动](images/linkmind_client_quickstart_15.png)

浏览器打开：

- `http://localhost:3000/workspace/chats/new`

模型选择 `LinkMind Qwen Plus`，发送测试消息并确认页面返回。

![图 16 DeerFlow 新对话页面](images/linkmind_client_quickstart_16.png)

![图 17 DeerFlow 控制台收到 LinkMind 模型回复](images/linkmind_client_quickstart_17.png)

#### OpenHuman 客户端操作

选择 OpenHuman 安装分支后，安装器会在 OpenHuman `config.toml` 中写入 `linkmind` provider：

```toml
chat_provider = "linkmind:Alibaba/qwen3.6-plus"
reasoning_provider = "linkmind:Alibaba/qwen3.6-plus"
agentic_provider = "linkmind:Alibaba/qwen3.6-plus"
coding_provider = "linkmind:Alibaba/qwen3.6-plus"
memory_provider = "linkmind:Alibaba/qwen3.6-plus"
heartbeat_provider = "linkmind:Alibaba/qwen3.6-plus"
learning_provider = "linkmind:Alibaba/qwen3.6-plus"
subconscious_provider = "linkmind:Alibaba/qwen3.6-plus"

[[cloud_providers]]
id = "p_linkmind_linkmind"
slug = "linkmind"
label = "LinkMind"
endpoint = "http://127.0.0.1:8080/v1"
auth_style = "bearer"
default_model = "Alibaba/qwen3.6-plus"
```

运行安装器或启动 LinkMind 前设置 `LINKMIND_API_KEY`，安装器会在 OpenHuman `auth-profiles.json` 中激活 `provider:linkmind` profile，并在 OpenHuman 桌面端 keychain 文件存在时把同一个 token 写入 keychain。

LinkMind 启动后，如果 OpenHuman 已经打开，请重启 OpenHuman 让新 provider 生效；随后在 OpenHuman 中发送一条消息，并确认 LinkMind 收到 `/v1/chat/completions` 请求。

### 3.6 对话验证与用量闭环

客户端完成一次对话后，统一回到 LinkMind 控制台验证。验证口径不随客户端变化：看客户端侧返回、LinkMind 调用日志、用量概览、额度中心余额是否出现本次请求对应的变化。

| 验证项 | OpenClaw | Hermes Agent | DeerFlow | OpenHuman |
| --- | --- | --- | --- | --- |
| 客户端侧 | OpenClaw 对话页返回模型回复 | Hermes 控制台不再停留在 Initializing agent，并返回回复 | DeerFlow Chat 页面显示助手回复 | OpenHuman 对话返回模型回复 |
| LinkMind 调用日志 | 出现来自 OpenClaw / `linkmind-Pro` 的调用记录 | 出现 Hermes 对话对应的 Token 记录 | 出现 DeerFlow 对话对应的新增记录 | 出现来自 OpenHuman 的 `/v1/chat/completions` 调用 |
| 额度中心 | 余额发生扣减或交易记录更新 | 余额从对话前基线减少 | 余额与 Records / Total Tokens 均更新 | 余额或用量记录更新 |

![图 18 OpenClaw 对话后额度中心余额变化](images/linkmind_client_quickstart_18.png)

![图 19 Hermes 对话后用量概览出现新增记录](images/linkmind_client_quickstart_19.png)

![图 20 DeerFlow 对话后用量概览记录更新](images/linkmind_client_quickstart_20.png)

闭环判定：

- LinkMind 服务可访问，`http://localhost:8080` 能打开控制台。
- 当前客户端已把模型请求指向 `http://localhost:8080/v1` 或可访问的 Windows 主机地址。
- 客户端页面或控制台能返回模型回复。
- LinkMind 的调用日志、用量概览、额度中心至少有一项随本次请求更新。

### 3.7 后续复刻启动命令

首次安装和配置完成后，日常复刻只需要按“先 LinkMind、后智能体客户端”的顺序启动。

#### 通用：启动 LinkMind

```powershell
cd "$env:USERPROFILE\LinkMind"
java -jar "LinkMind.jar" --host=0.0.0.0 --port=8080 --enable-sync=false
```

#### OpenClaw

```powershell
openclaw gateway start
# 浏览器打开 127.0.0.1:18789
```

#### Hermes Agent

```powershell
hermes status
hermes
```

#### DeerFlow

```powershell
cd D:\workspace\deer-flow\backend
$env:PYTHONPATH = "."
uv run uvicorn app.gateway.app:app --host 0.0.0.0 --port 8001

cd D:\workspace\deer-flow\frontend
corepack pnpm run dev
```

#### OpenHuman

先启动 LinkMind，再打开 OpenHuman 桌面应用。如果安装器修改 `config.toml` 时 OpenHuman 已经在运行，请重启 OpenHuman 以重新加载 `linkmind` provider。

## 第四部分：AI Agents Project Best Practice 延伸阅读

完成基础接入和客户端验证后，可以继续阅读下面这组 Best Practice。它们从单 Agent 可靠调用开始，逐步覆盖私有知识注入、Skills / Tools / MCP、Guardrails 与审计、路由编排、多 Agent 协作，以及 Agentic Social 场景，适合在 POC 之后规划生产级 Agent 项目时参考。

1. [Part 01：From Model Call to Reliable Single Agent](BestPractice/Best-Practice-for-AI-Agents-Project_Part-01_From-Model-Call-to-Reliable-Single-Agent.pdf)
2. [Part 02：Injecting Private Knowledge without Retraining](BestPractice/Best-Practice-for-AI-Agents-Project_Part-02_Injecting-Private-Knowledge-without-Retraining.pdf)
3. [Part 03：Injecting Private Capabilities with Skills, Tools and MCP](BestPractice/Best-Practice-for-AI-Agents-Project_Part-03_Injecting-Private-Capabilities-with-Skills-Tools-and-MCP.pdf)
4. [Part 04：Safety by Design, Guardrails, Permissions and Audit](BestPractice/Best-Practice-for-AI-Agents-Project_Part-04_Safety-by-Design-Guardrails-Permissions-and-Audit.pdf)
5. [Part 05：Routing, Orchestration and Multi-Agent Teamwork](BestPractice/Best-Practice-for-AI-Agents-Project_Part-05_Routing-Orchestration-and-Multi-Agent-Teamwork.pdf)
6. [Part 06：Agentic Social, Agents in Shared Human Contexts](BestPractice/Best-Practice-for-AI-Agents-Project_Part-06_Agentic-Social-Agents-in-Shared-Human-Contexts.pdf)
