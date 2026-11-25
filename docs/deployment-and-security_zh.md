# 部署、安全与级联组网

本页用于选择部署方式、设置运行参数、完成上线验证，并将模型、RAG、Agent 与级联节点放在可控网络边界内。

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

## 二、运行参数与配置目录

直接启动 JAR 时可控制端口、监听地址、配置和数据目录：

```bash
java -Dserver.port=8080 \
  -Dserver.address=127.0.0.1 \
  -Dlinkmind.config=/opt/linkmind/config/lagi.yml \
  -Dlinkmind.data=/var/lib/linkmind/data \
  -jar LinkMind.jar
```

仅在确有可信局域网客户端时将监听地址改为 `0.0.0.0`；公网场景让反向代理负责 TLS、访问控制和限流。`-Dlinkmind.config` 指定的文件是实际主配置，配置拆分通过 `include_models`、`include_stores`、`include_agents`、`include_mcps`、`include_pnps` 加载。

完成启动后，先访问 `http://localhost:8080`，登录或确认管理入口，再按[模型接入、路由与多模态](models-and-multimodal_zh.md)完成一条短聊天请求。响应、运行记录和日志三者都正常才算部署成功。

## 三、安全基线与运行排查

LinkMind 提供治理入口，但生产安全由部署方的网络、账号、密钥、数据与供应商策略共同决定。上线至少执行以下控制：

1. 对公网或共享网络启用 API 鉴权，控制台和管理接口只允许可信来源访问。
2. Provider Key、密码、对象存储凭据不进入代码库、镜像层、日志、截图或诊断包；按环境和供应商分 Key，并建立轮换与吊销流程。
3. 使用 HTTPS 和可信反向代理；限制防火墙来源，不直接暴露开发端口、MCP 服务或内部级联节点。
4. 对 `filters`、敏感词、策略检查、上传文件、缓存、日志、向量库和临时目录设定访问控制与保留周期。
5. Agent、MCP、Skill、Worker 只授予必要的网络、文件和工具权限；有副作用的工具应有审批、审计或沙箱。
6. 删除或更新 RAG 源文档时，同步处理向量、缓存、上传文件和追踪记录；多租户使用独立分类、路径和凭据。

出现异常时记录发生时间、请求 ID、模型/路由、脱敏错误和首包/末包时间。不要把原始 Key、用户数据或完整隐私文档复制到问题单。

## 四、互动级联与网络验证

互动频道和 API Key 管理请求的级联代理使用 `general.cascade_api_address`，它与 Provider 的 `models[].api_address` 完全不同：

```yaml
general:
  cascade_api_address: https://linkmind-node.example.com
```

留空表示由当前节点处理。只将级联连接到可信节点，并为每个节点独立管理本地数据、并发、权限与上游凭据；旧 `general.mode: mate/server` 仅为兼容读取，新配置不要使用。

上线或迁移后按顺序验证：DNS/TLS/防火墙、控制台登录、每个启用模型的一条短调用、鉴权拒绝无效 Key、RAG、Agent/MCP、级联目标以及备份回滚流程。
