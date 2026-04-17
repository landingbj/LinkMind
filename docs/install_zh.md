# LinkMind 安装指南

本文优先服务第一次接触 LinkMind 的用户，所以安装顺序按“最快跑起来”来写。只想尽快体验时，直接走安装脚本；只有在需要更强控制权时，再使用 JAR 或源码构建方式。

## 一、最快路径：官方安装脚本

### 前置条件

- 已安装 JDK 8 或以上版本
- 终端具备联网能力

如果本机还没有 Java，建议优先安装 Temurin OpenJDK。

### 执行安装

- Windows PowerShell

  ```powershell
  iwr -useb https://downloads.landingbj.com/install.ps1 | iex
  ```

- macOS / Linux

  ```bash
  curl -fsSL https://downloads.landingbj.com/install.sh | bash
  ```

### 选择运行模式

安装过程中，LinkMind 会提示你选择运行模式：

| 模式 | 适用场景 |
| --- | --- |
| `Agent Mate` | 本机已经在使用 OpenClaw、Hermes Agent 或 DeerFlow，希望 LinkMind 作为统一 AI 中间层接入 |
| `Agent Server` | 先独立启动 LinkMind，直接体验控制台和 API，或做基础部署评估 |

如果你是第一次试用，建议先选 `Agent Server`。

### 首次启动

安装完成后的成功流程通常是：

1. 安装器输出 `LinkMind installed successfully!`
2. 询问 `Would you like to start LinkMind now?`
3. 输入 `yes`
4. 等待服务启动完成
5. 浏览器打开 `http://localhost:8080`

### 首次进入控制台建议做的事

1. 打开 Web 控制台
2. 注册或登录
3. 进入 API Key / Provider 设置页
4. 至少配置一个真实可用的模型密钥
5. 回到聊天页发送第一条消息

如果你准备直接调用 REST API，且系统启用了鉴权，请先在控制台复制 LinkMind API Key，并在请求头中带上：

```http
Authorization: Bearer <你的-linkmind-api-key>
```

## 二、直接运行 `LinkMind.jar`

当你已经拿到打包好的 JAR 时，最简单的方式就是直接运行它。

### 启动命令

- Windows

  ```powershell
  java -jar LinkMind.jar
  ```

- macOS / Linux

  ```bash
  java -jar LinkMind.jar
  ```

### 首次启动会自动生成

- `config/`
- `data/`
- `config/lagi.yml`

随后访问：

- `http://localhost:8080`

### 默认输出位置

如果你把 JAR 放在 `D:\LinkMind` 或 `~/LinkMind` 目录下运行，默认生成的配置和数据目录也会落在该目录旁边。

## 三、从源码构建

当你需要跟随本地最新代码，或同时拿到可执行 JAR 与 WAR 包时，使用源码构建方式。

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

但对本地体验和日常运行来说，仍然更推荐直接使用内嵌式 JAR。

## 四、常用运行参数

LinkMind 提供了几组非常实用的启动参数。

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

## 五、启动后下一步看什么

建议按下面顺序继续：

1. [配置参考](config_zh.md)
2. [API 参考](API_zh.md)
3. [教学演示](tutor_zh.md)
4. [开发集成指南](guide_zh.md)

如果你准备开启 RAG、本地向量库或文档处理，也请继续阅读 [附件](annex_zh.md)。
