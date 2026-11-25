# 部署、安全与级联组网

本页用于选择部署方式、控制运行时暴露面，并安全地连接 LinkMind 节点。

## 一、部署方式

| 方式 | 适用场景 | 启动入口 |
| --- | --- | --- |
| 官方安装器 | 工作站或盒子需要最快安装 | `iwr -useb https://cdn.linkmind.top/install.ps1 | iex` |
| 预打包 JAR | 直接管理 Java 和配置 | `java -jar LinkMind.jar` |
| Docker 镜像 | 容器化基础设施 | `docker run -d -p 8080:8080 landingbj/linkmind` |
| 源码 / WAR | 需要 IDE 调试或 Tomcat 部署 | `mvn clean package -pl lagi-web -am -DskipTests -U` |

源码构建产物为 `lagi-web/target/LinkMind.jar` 与 `lagi-web/target/ROOT.war`。

## 二、运行参数

JAR 部署优先使用 JVM 参数，而不是修改生成文件：

```bash
java -Dserver.port=8080 \
  -Dserver.address=0.0.0.0 \
  -Dlinkmind.config=/path/to/lagi.yml \
  -Dlinkmind.data=/path/to/data \
  -jar LinkMind.jar
```

只绑定实际客户端需要的网络接口。反向代理、TLS 终止、防火墙规则和公网 DNS 边界应放在服务前面，避免直接暴露无限制的开发端口。

## 三、安全基线

- Provider Key 与密码不进入源码仓库，也不出现在日志或支持包中。
- 共享部署或网络可达部署应启用 API 鉴权。
- 用户、API Key、MCP 工具和 Skill 仅授予必要权限。
- 文档、RAG collection 和检索轨迹属于私有数据，应遵守与源系统相同的访问与留存规则。
- 运维排查记录模型、路由、时间和脱敏错误信息。

页面浏览器会话不能替代后端 API 管理员凭证。Provider 访问、用户认证与额度服务可由部署分别决定。

## 四、互动级联

互动频道和 API Key 管理请求的代理使用 `general.cascade_api_address`：

```yaml
general:
  cascade_api_address: https://linkmind-node.example.com
```

留空表示在当前节点处理。这与 `models[].api_address` 不同，后者仅是模型 Provider 的上游地址。新配置不应使用旧的 `general.mode: mate/server`；该字段只保留兼容读取。

## 五、上线检查清单

1. 验证目标主机、端口、防火墙、DNS 和 TLS 链路。
2. 对每个启用模型和路由发起短请求验证。
3. 验证 API 鉴权、受限 Key 访问和密钥脱敏。
4. 分别验证 RAG、Agent 工具、MCP 服务与级联目标。
5. 保留可观测的重启、备份和回滚流程。

完整责任边界和策略说明见[安全与合规](security_zh.md)。
