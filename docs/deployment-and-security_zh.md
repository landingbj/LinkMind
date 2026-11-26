# 部署、安全与级联组网

本页用于选择部署方式、设置运行参数、完成上线验证，并将模型、RAG、Agent 与级联节点放在可控网络边界内。

[安装器](#installer-steps) · [安装 JDK](#install-jdk) · [独立 JAR](#packaged-jar) · [源码与 WAR](#source-and-war) · [启动参数](#jar-options) · [客户端同步](#runtime-sync) · [安全](#security-baseline) · [级联](#interaction-proxy)

## 一、选择并完成部署

| 方式 | 适用场景 | 最小启动方式 |
| --- | --- | --- |
| 官方安装器 | 工作站、个人电脑或盒子快速安装 | `iwr -useb https://cdn.linkmind.top/install.ps1 \| iex` |
| 预打包 JAR | 自行管理 Java、配置和数据目录 | `java -jar LinkMind.jar` |
| Docker | 容器基础设施 | `docker run -d -p 8080:8080 landingbj/linkmind` |
| 源码 / WAR | IDE 调试、二开或 Tomcat | `mvn clean package -pl lagi-web -am -DskipTests -U` |

运行 JAR 或源码构建需要 JDK 8 或更高版本。安装器会在默认目录准备 `LinkMind.jar`、`config/`、`config/lagi.yml` 与 `data/`；JAR 第一次启动也会生成缺失的默认目录。源码构建产物为 `lagi-web/target/LinkMind.jar` 和 `lagi-web/target/ROOT.war`。

Docker 示例应将配置和数据持久化到宿主机，而不是只依赖容器可写层：

```bash
docker run -d --name linkmind \
  -p 8080:8080 \
  -v /srv/linkmind/config:/app/config \
  -v /srv/linkmind/data:/app/data \
  landingbj/linkmind
```

生产升级前备份 `lagi.yml`、拆分配置文件、数据目录、SQLite 向量库及外置服务配置；先在同版本测试环境验证，保留上一份 JAR/WAR 或镜像用于回滚。

<a id="installer-steps"></a>

### 用安装器选择组件并首次启动

<a id="installer-options"></a>

#### 可选安装项

LinkMind 只有一种统一运行形态。安装器会按需提供三个独立选项：接入本机已有的 OpenClaw、Hermes Agent、DeerFlow 或 OpenHuman；安装 popular skills；下载 Medusa 加速资源。它们可以任意组合，不再需要选择产品版本。

<a id="installer-actions"></a>

#### 安装器会自动做什么

当前安装器不只是下载一个 JAR，它还会顺带完成初始化工作：

1. 创建 LinkMind 主目录，通常是 Windows 下的 `%USERPROFILE%\LinkMind`，或者 macOS / Linux 下的 `~/LinkMind`。
2. 将 `LinkMind.jar` 下载到该目录。
3. 可选询问是否接入本机已有的 OpenClaw、DeerFlow、Hermes 或 OpenHuman。选择 DeerFlow 时会要求输入安装目录；选择 OpenHuman 时可以留空自动发现，也可以输入配置目录或 `config.toml` 文件。
4. 可选询问是否下载 popular skills，并解压到 `skills/popular_skills`。
5. 可选询问是否下载 Medusa 加速资源。
6. 调用运行时初始化逻辑，写入所选 Skills、Medusa 与 Agent 集成设置。

<a id="installer-first-launch"></a>

#### 首次启动

安装完成后，脚本可以直接帮你启动 LinkMind。正常成功流程通常是：

1. 安装器输出 `LinkMind installed successfully!`
2. 提示 `Would you like to start LinkMind now?`
3. 输入 `yes`
4. 等待服务日志启动完成
5. 浏览器打开 `http://localhost:8080`

第一次真正启动服务时，LinkMind 还会自动准备：

- `config/`
- `config/lagi.yml`
- `data/`
- 在使用默认路径时，把随包附带的本地数据文件复制到 `data/`

<a id="installer-macos"></a>

#### macOS 说明

- 请从 Terminal 执行安装脚本，不要靠 Finder 双击文件启动。
- 如果装完 JDK 后 `java -version` 仍然不可用，请打开一个新的 Terminal 窗口，或执行 `source ~/.zshrc`。
- Apple Silicon 芯片的 Mac，请在文末附录中选择 `aarch64` 或 `arm64` 的 JDK 安装包。

<a id="packaged-jar"></a>

### 按操作系统启动下载的 JAR

先把下载的 `LinkMind.jar` 放到所选目录，再从该目录运行下面的命令。

- Windows

  ```powershell
  mkdir D:\LinkMind
  cd D:\LinkMind
  java -jar LinkMind.jar
  ```

- macOS

  ```bash
  mkdir -p ~/Documents/LinkMind
  cd ~/Documents/LinkMind
  java -jar LinkMind.jar
  ```

- Linux

  ```bash
  mkdir -p ~/LinkMind
  cd ~/LinkMind
  java -jar LinkMind.jar
  ```

<a id="jar-first-run"></a>

#### 首次启动会自动做什么

当你不额外传入自定义目录参数时，LinkMind 会自动：

- 创建 `config/`
- 创建 `data/`
- 生成 `config/lagi.yml`
- 将随包附带的本地数据文件复制到 `data/`

随后访问：

- `http://localhost:8080`

#### 默认输出位置

如果你的 JAR 放在 `D:\LinkMind`、`~/Documents/LinkMind` 或 `~/LinkMind` 下运行，默认生成的配置和数据目录也会落在该 JAR 同级位置。

<a id="source-and-war"></a>

### 从源码构建、在 IDE 中运行或部署 WAR

如果你需要更典型的开发者工作流，这些方式依然有效。

#### Maven 打包

```bash
git clone https://github.com/landingbj/LinkMind.git
cd LinkMind
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

[Java IDE 导入和排查](best-practices/integrations/java-ide.md#zh-cn) · [部署到 Tomcat](best-practices/integrations/deploy-war-to-tomcat.md#zh-cn)

<a id="jar-options"></a>

## 二、运行参数与配置目录

独立 JAR 接受下列参数。`--config` 指向存放 `lagi.yml` 和拆分文件的目录，`--data-dir` 指向持久化数据目录。

下面这些参数适用于直接启动 `LinkMind.jar`，也适用于源码打包后的 JAR。

#### 修改端口

```bash
java -jar LinkMind.jar --port=8090
```

然后访问 `http://localhost:8090`。

#### 绑定监听地址

```bash
java -jar LinkMind.jar --host=0.0.0.0
```

#### 自定义配置与数据目录

- Windows

  ```powershell
  java -jar LinkMind.jar --config=D:\LinkMindConfig --data-dir=D:\LinkMindData
  ```

- macOS / Linux

  ```bash
  java -jar LinkMind.jar --config=/opt/linkmind/config --data-dir=/var/lib/linkmind/data
  ```

命令行选项优先于 JVM 属性 `server.port`、`server.address`、`config.dir`、`data.dir`，对应的环境变量为 `SERVER_PORT`、`SERVER_ADDRESS`、`CONFIG_DIR`、`DATA_DIR`。在可信局域网提供服务前确认监听地址和防火墙规则，对外服务用 HTTPS 网关。嵌入 `lagi-core` 或使用外部 servlet 容器时，`-Dlinkmind.config=/path/to/lagi.yml` 选文件，`-Dai.data.dir=/path/to/data` 设置本地数据库目录。

完成启动后，先访问 `http://localhost:8080`，登录或确认管理入口，再按[模型接入、路由与多模态](models-and-multimodal_zh.md)完成一条短聊天请求。响应、运行记录和日志三者都正常才算部署成功。

<a id="runtime-sync"></a>

### 同步 DeerFlow 和 OpenHuman 配置

<a id="deerflow-sync"></a>

#### 指定 DeerFlow 同步目录

```bash
java -jar LinkMind.jar --deer-flow-path=/path/to/deer-flow
```

<a id="openhuman-sync"></a>

#### 指定 OpenHuman 同步目录

```bash
java -jar LinkMind.jar --openhuman-path=/path/to/openhuman/config.toml
```

OpenHuman 接入前建议先设置 `LINKMIND_API_KEY`；LinkMind 会把 `linkmind` provider 写成 Bearer 鉴权，绑定 `provider:linkmind` 鉴权 profile，并在 OpenHuman 桌面端 keychain 文件存在时把 token 保存进去。

如果该参数为空，LinkMind 会先检查 `OPENHUMAN_WORKSPACE`，再回落到默认的 `~/.openhuman/users/<user_id>/config.toml` 或 `~/.openhuman/users/local/config.toml`。

要在这次启动中跳过客户端同步，可运行 `java -jar LinkMind.jar --enable-sync=false`。

<a id="security-baseline"></a>

## 三、安全基线与运行排查

本节说明 LinkMind 提供的控制措施与部署方负责的配置。组织仍需按数据用途、行业和地区完成适用的安全、隐私和合规评估。需要合规认证或法律意见时，由相应认证机构或法律专业人士针对实际部署出具。

出现异常时记录发生时间、请求 ID、模型/路由、脱敏错误和首包/末包时间。不要把原始 Key、用户数据或完整隐私文档复制到问题单。

<a id="security-3"></a>

### 责任边界

LinkMind 提供中间件层面的安全控制与治理入口，但生产安全仍然依赖部署方的环境配置、权限模型、密钥管理、网络策略、数据分类制度和模型供应商选择。

部署方应特别确认：

- 是否启用 `functions.chat.enable_auth`、`functions.chat.enable_policy` 和 `filters.enable`。
- `functions.chat.filter` 是否引用了需要的过滤器，例如 `sensitive`、`priority`、`stopping`、`continue`。
- 模型、向量库、MCP 服务、Skills、Workers 与外部 Agent 运行时是否只开放给可信来源。
- 日志、缓存、上传文件、RAG 索引和临时工作区是否符合组织的数据保留与访问策略。

<a id="security-4"></a>

### 安全围栏

LinkMind 的安全围栏通过配置组合生效，而不是绑定到单一模型或单一接口。核心控制包括：

- 敏感词过滤：通过 `filters.items[].groups` 配置 `mask`、`erase`、`block` 等处理级别。
- 优先级关键词：通过 `priority` 规则影响对话调度或业务优先级。
- 停止词：通过 `stopping` 规则提前终止不需要继续的对话路径。
- 续聊词：通过 `continue` 规则识别需要延续上下文的会话。
- 内置策略检查：通过 `functions.chat.enable_policy` 启用运行时策略判断。

<a id="filter-rules"></a>

#### 配置敏感词、优先、终止和延续规则

敏感词过滤示例：

```yaml
filters:
  enable: true
  items:
    - name: sensitive
      groups:
        - level: mask
          rules: >-
            example-name,internal-code
        - level: erase
          rules: >-
            your context
        - level: block
          rules: >-
            blocked-term,restricted-term
```

优先级过滤示例：

```yaml
filters:
  items:
    - name: priority
      rules: >-
        car,weather,
        social*security
```

停止词过滤示例：

```yaml
filters:
  items:
    - name: stopping
      rules: >-
        bye,
        start*
```

续聊过滤示例：

```yaml
filters:
  items:
    - name: continue
      rules: >-
        about,next,then
```

常见字段：

| 字段 | 说明 |
| --- | --- |
| `enable` | 全局过滤器开关 |
| `items[].name` | 在 `functions.chat.filter` 中引用的过滤器名 |
| `items[].groups[].level` | 敏感词分组动作级别，如 `mask`、`erase`、`block` |
| `items[].groups[].rules` | 分组敏感词规则，可写逗号分隔关键词或正则风格规则 |
| `items[].rules` | 非分组过滤器的关键词列表，如优先级、停止词、续聊词 |

`functions.chat.filter` 里填写的应该就是这些过滤器名称，多个值用逗号分隔。

<a id="security-5"></a>

### 数据与隐私

LinkMind 可以连接业务系统、私有知识库、向量数据库、模型供应商和 Agent 运行时，因此部署方应按数据敏感度分层治理：

- 不要在公开演示、测试配置或提交记录中放入真实 API Key、Secret、个人信息或客户数据。
- 对 RAG 文档、向量索引、缓存、日志和上传文件应用与源数据一致的访问控制。
- 在接入第三方模型或云端 Agent 平台前，确认数据传输、训练使用、日志保留和跨境处理条款。
- 对 OCR、ASR/TTS、图像、视频、Text-to-SQL 和文档处理等多模态能力执行同等的数据分类与脱敏要求。
- 对包含个人信息、凭据、商业秘密或受监管数据的输入输出建立审计与删除流程。

<a id="security-6"></a>

### 访问控制与密钥

LinkMind 支持 API Key、Key 池、鉴权开关和计费相关接口。生产部署应遵循最小权限原则：

- 使用独立的服务账号和最小权限 Key，避免复用个人开发 Key。
- 将密钥放在安全的配置管理或密钥管理系统中，不要提交到仓库。
- 为不同环境、租户和供应商使用可区分的 Key，并建立轮换与吊销流程。
- 对 `/apiKey/*`、`/credit/*` 等管理接口配置外层访问控制。
- 对反向代理、控制台、管理 API 和内部级联节点使用网络隔离或可信网络策略。

<a id="security-7"></a>

### RAG、文档与检索

RAG 和文档流水线会把源文件转换为可检索内容，安全重点不只在原始文件，也包括派生数据：

- 向量库、全文索引、图谱增强数据和缓存都应按原始文档的敏感级别管理。
- 删除或更新源文档时，应同步处理派生索引、缓存和追踪记录。
- 多租户部署应隔离不同租户的知识库、索引命名空间、文件存储路径和访问凭据。
- 对 Text-to-SQL 场景，应限制数据库账号权限，并对生成 SQL 的执行范围做额外校验。

<a id="security-8"></a>

### Agent、MCP 与 Skills

Agent、MCP、Skills、Workers 和 Pnps 能扩展系统能力，也会扩大执行面。生产使用前应完成能力审查：

- 只启用可信来源的 MCP 服务、Skills、Workers 和 Agent 运行时。
- 为外部工具配置最小权限凭据，并限制文件系统、网络和命令执行范围。
- 对可能产生副作用的工具调用建立审批、审计或沙箱策略。
- 定期检查运行时同步配置，避免本地 Agent 配置被意外覆盖或漂移。

<a id="security-9"></a>

### 级联组网

级联组网适合把多个 LinkMind 节点组合为更大的运行网络。部署时应确保：

- 每个节点独立管理本地数据、并发、权限和后端凭据。
- 级联链路只连接可信节点，并通过网络、鉴权或反向代理策略保护。
- 不同业务域、租户或安全级别的数据在物理或逻辑上隔离。
- 节点之间只传递完成任务所需的最小上下文。

<a id="security-10"></a>

### 生产部署检查项

- 启用鉴权、策略检查和必要过滤器。
- 替换所有示例 Key、默认 Secret 和测试地址。
- 使用 HTTPS、可信反向代理和受控管理入口。
- 限制控制台、管理 API、MCP 服务和级联节点的访问来源。
- 为日志、缓存、上传文件、向量库和临时目录设置访问控制、保留周期和清理流程。
- 对供应商模型、云端 Agent 平台和第三方服务完成数据处理条款评估。
- 建立配置变更审计、密钥轮换、漏洞响应和异常调用告警机制。

<a id="security-11"></a>

### 漏洞与安全问题

如果你发现安全问题，请不要在公开 Issue 中披露可利用细节。建议通过项目维护者指定的私有渠道提交复现步骤、影响范围、版本信息和缓解建议。维护者确认后，应优先发布修复、升级说明或临时缓解方案。

<a id="interaction-proxy"></a>

## 四、互动级联与网络验证

互动频道和 API Key 管理请求的级联代理使用 `general.cascade_api_address`，它与 Provider 的 `models[].api_address` 完全不同：

```yaml
general:
  cascade_api_address: https://linkmind-node.example.com
```

留空表示由当前节点处理。只将级联连接到可信节点，并为每个节点独立管理本地数据、并发、权限与上游凭据；旧 `general.mode: mate/server` 仅为兼容读取，新配置不要使用。

上线或迁移后按顺序验证：DNS/TLS/防火墙、控制台登录、每个启用模型的一条短调用、鉴权拒绝无效 Key、RAG、Agent/MCP、级联目标以及备份回滚流程。

在控制台打开 **互动 → 级联** 可查看和修改对端地址。保存后从 `/socialChannel/runningMode` 读取 `cascadeProxyEnabled`，再以同一个频道核对转发结果。旧 `general.mode` 的值不再选择运行行为。

<a id="install-jdk"></a>

## 附录：安装并验证 JDK

按系统和处理器架构选择 JDK 8 或更高版本。Linux 发行版的软件源可能不提供 JDK 8，遇到这种情况可使用系统支持的较新 JDK 或下方的 Temurin 安装方式。

JDK 是运行和开发 LinkMind 的前置条件。若你还没有 Java 环境，最推荐直接安装 Temurin OpenJDK。

### Windows

1. 从 [Eclipse Temurin](https://adoptium.net/temurin/releases/) 或 [Oracle Java](https://www.oracle.com/java/technologies/downloads/#java8) 下载 JDK 8。
2. 选择 Windows x64 安装包。
3. 按安装向导完成安装。Temurin 的 `.msi` 一般可以顺带配置 `PATH`。
4. 如有需要，手动配置：

   - `JAVA_HOME=C:\Development_tools\jdk1.8.0_xxx`
   - 在 `Path` 中加入 `%JAVA_HOME%\bin`

5. 验证：

   ```powershell
   java -version
   javac -version
   ```

### macOS

#### Temurin OpenJDK

1. 打开 [Eclipse Temurin Releases](https://adoptium.net/temurin/releases/)。
2. 选择 macOS 的 JDK 8。
3. Apple Silicon 选择 `aarch64`，Intel 机器选择 `x64`。
4. 安装 `.pkg`。

如果 `JAVA_HOME` 没有自动配置，可以手动追加：

```bash
# 现代 macOS 默认使用 zsh
nano ~/.zshrc
```

然后加入：

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH
```

重新加载：

```bash
source ~/.zshrc
```

验证：

```bash
java -version
javac -version
echo $JAVA_HOME
```

#### Oracle JDK

你也可以安装 Oracle JDK 8，安装完成后按同样方式设置 `JAVA_HOME`。

### Linux

#### 包管理器安装

- Ubuntu / Debian

  ```bash
  sudo apt update
  sudo apt install openjdk-8-jdk
  sudo update-alternatives --config java
  ```

- CentOS / RHEL / Fedora

  ```bash
  sudo yum install java-1.8.0-openjdk-devel
  # 或者在较新的 Fedora 上
  sudo dnf install java-1.8.0-openjdk-devel
  ```

- Arch Linux

  ```bash
  sudo pacman -S jdk8-openjdk
  ```

#### 手动安装 Temurin

1. 从 Temurin 下载 Linux 的 JDK 8 `.tar.gz`。
2. 解压到例如 `/opt/java`。
3. 在 `~/.bashrc`、`~/.profile` 或 `~/.zshrc` 中配置 `JAVA_HOME` 和 `PATH`。

示例：

```bash
export JAVA_HOME=/opt/java/jdk8u-xxx
export PATH=$JAVA_HOME/bin:$PATH
```

重新加载 shell 后验证：

```bash
java -version
javac -version
echo $JAVA_HOME
```
