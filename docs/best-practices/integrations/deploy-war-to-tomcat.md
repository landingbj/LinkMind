# Deploy a WAR to Tomcat

[中文](#zh-cn) · [English](#english)

<a id="english"></a>

Use this recipe when an existing Tomcat estate requires a WAR. Keep the application artifact, external configuration, data, and Tomcat service lifecycle separate so a release never overwrites production secrets or persistent data.

## 1. Build the WAR

Build the web module from source:

```bash
mvn clean package -pl lagi-web -am -DskipTests -U
```

The result is `lagi-web/target/ROOT.war`. Record its build commit and checksum before copying it to a server. Do not package real Keys into the WAR.

## 2. Deploy and configure one Tomcat instance

Stop only the target Tomcat service, back up its current WAR and external `lagi.yml`, then deploy `ROOT.war` to that instance's `webapps` directory. Keep configuration and data in managed external locations and pass their paths through the service's JVM options, for example `-Dlinkmind.config=/path/to/lagi.yml` and `-Dai.data.dir=/path/to/data`.

Start the same instance and wait for the WAR to unpack. Verify the deployment URL, API authentication, one short model call, and the application log before directing clients to it. If verification fails, restore the previous WAR and configuration backup rather than editing the unpacked webapp in place.

Use the same firewall, TLS, secret-management, and rollback controls as a JAR deployment. See [Deployment and Security](../../deployment-and-security_en.md) / [部署、安全与级联组网](../../deployment-and-security_zh.md).

<a id="zh-cn"></a>

## 中文

在源码仓库运行 `mvn clean package -pl lagi-web -am -DskipTests -U`。记下构建提交和 `lagi-web/target/ROOT.war` 的校验值。停止目标 Tomcat 实例，备份旧 WAR 和外部配置，将新 WAR 安装到该实例的 `webapps`。

在该实例的 JVM 参数中设置 `-Dlinkmind.config=/path/to/lagi.yml` 和 `-Dai.data.dir=/path/to/data`。启动后核对应用地址、鉴权、模型回复和服务日志。验证失败时使用备份恢复 WAR 和配置。[部署说明](../../deployment-and-security_zh.md#source-and-war)
