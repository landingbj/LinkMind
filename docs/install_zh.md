# LinkMind 安装指南

前置条件：LinkMind 需要 **JDK 8 或以上版本**。如果你的机器还没有安装 Java，请直接跳转到文末的[附录：安装 JDK 8+](#附录安装-jdk-8)。

本文把 4 种安装方式视为并列选项，而不是顺序步骤。请选择最适合你的部署方式。

## Option 1：官方安装脚本快速安装

### 执行安装

- Windows PowerShell

  ```powershell
  iwr -useb https://cdn.linkmind.top/install.ps1 | iex
  ```

- macOS / Linux

  ```bash
  curl -fsSL https://cdn.linkmind.top/install.sh | bash
  ```

### 选择运行模式

安装过程中，LinkMind 会提示你选择运行模式：

| 模式 | 适用场景 |
| --- | --- |
| `Agent Mate` | 本机已经在使用 OpenClaw、Hermes Agent 或 DeerFlow，希望 LinkMind 作为统一 AI 中间层接入 |
| `Agent Server` | 先独立启动 LinkMind，直接体验控制台和 API，或做基础部署评估 |

如果你是第一次试用，建议先选 `Agent Server`。

### 安装器会自动做什么

当前安装器不只是下载一个 JAR，它还会顺带完成初始化工作：

1. 创建 LinkMind 主目录，通常是 Windows 下的 `%USERPROFILE%\\LinkMind`，或者 macOS / Linux 下的 `~/LinkMind`。
2. 将 `LinkMind.jar` 下载到该目录。
3. 询问你运行模式是 `Agent Mate` 还是 `Agent Server`。
4. 如果选择 `Agent Mate`，继续询问要接入或同步哪个运行时：OpenClaw、DeerFlow、Hermes。若选择 DeerFlow，还会要求输入安装目录。
5. 如果选择 `Agent Server`，会自动下载 popular skills 压缩包，并解压到 `skills/popular_skills`。
6. 调用运行时初始化逻辑，把首启所需的模式、技能与同步设置写好。

### 首次启动

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

### macOS 说明

- 请从 Terminal 执行安装脚本，不要靠 Finder 双击文件启动。
- 如果装完 JDK 后 `java -version` 仍然不可用，请打开一个新的 Terminal 窗口，或执行 `source ~/.zshrc`。
- Apple Silicon 芯片的 Mac，请在文末附录中选择 `aarch64` 或 `arm64` 的 JDK 安装包。

### 首次进入控制台建议做的事

1. 打开 Web 控制台。
2. 注册或登录。
3. 进入 API Key / Provider 设置页。
4. 至少填入一个真实可用的模型密钥。
5. 回到聊天页发送第一条消息。

如果你准备直接调用 REST API，且系统启用了鉴权，请先在控制台复制 LinkMind API Key，并在请求头中带上：

```http
Authorization: Bearer <你的-linkmind-api-key>
```

## Option 2：下载并运行 `LinkMind.jar`

如果你希望直接使用现成封包，而不自己编译源码，可以选这个方式。

### 预打包下载资源

- 应用文件：`LinkMind.jar`，[点击这里下载](https://cdn.linkmind.top/installer/LinkMind.jar)
- 核心库文件：`lagi-core-1.2.0-jar-with-dependencies.jar`，[点击这里下载](https://ai.linkmind.top/lagi/lib/lagi-core-1.2.0-jar-with-dependencies.jar)

### 准备并启动

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

### 首次启动会自动做什么

当你不额外传入自定义目录参数时，LinkMind 会自动：

- 创建 `config/`
- 创建 `data/`
- 生成 `config/lagi.yml`
- 将随包附带的本地数据文件复制到 `data/`

随后访问：

- `http://localhost:8080`

### 默认输出位置

如果你的 JAR 放在 `D:\LinkMind`、`~/Documents/LinkMind` 或 `~/LinkMind` 下运行，默认生成的配置和数据目录也会落在该 JAR 同级位置。

## Option 3：使用 Docker 镜像

如果你想通过预构建容器快速启动，可以用这种方式。

### 镜像名称

- `landingbj/linkmind`

### 拉取镜像

```bash
docker pull landingbj/linkmind
```

### 启动容器

```bash
docker run -d -p 8080:8080 landingbj/linkmind
```

启动后访问 `http://localhost:8080`。

## Option 4：从源码编译

如果你要跟随本地最新代码，或者同时需要可执行 JAR 和 WAR 包，请使用源码构建方式。

### 打包命令

```bash
mvn clean package -pl lagi-web -am -DskipTests -U
```

### 当前构建产物

当前 Maven 打包会生成：

- `lagi-web/target/LinkMind.jar`
- `lagi-web/target/ROOT.war`

### 运行打包后的 JAR

```bash
java -jar lagi-web/target/LinkMind.jar
```

### 部署 WAR

如果你更偏向传统 Servlet 容器，也可以部署：

- `lagi-web/target/ROOT.war`

但对于本地体验和日常运行，仍然更推荐直接使用内嵌式 JAR。

## JAR 启动时常用运行参数

下面这些参数适用于直接启动 `LinkMind.jar`，也适用于源码打包后的 JAR。

### 修改端口

```bash
java -jar LinkMind.jar --port=8090
```

然后访问 `http://localhost:8090`。

### 绑定监听地址

```bash
java -jar LinkMind.jar --host=0.0.0.0
```

### 自定义配置与数据目录

- Windows

  ```powershell
  java -jar LinkMind.jar --config=D:\LinkMindConfig --data-dir=D:\LinkMindData
  ```

- macOS / Linux

  ```bash
  java -jar LinkMind.jar --config=/opt/linkmind/config --data-dir=/var/lib/linkmind/data
  ```

### 预设运行模式

```bash
java -jar LinkMind.jar --runtime-choice=server
```

可选值为 `mate` 和 `server`。

### 指定 DeerFlow 同步目录

```bash
java -jar LinkMind.jar --deer-flow-path=/path/to/deer-flow
```

## 启动后下一步看什么

建议按下面顺序继续：

1. [配置参考](config_zh.md)
2. [API 参考](API_zh.md)
3. [教学演示](tutor_zh.md)
4. [开发集成指南](guide_zh.md)

如果你准备开启 RAG、本地向量库或文档处理，也请继续阅读 [附件](annex_zh.md)。

## 附录：安装 JDK 8+

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
