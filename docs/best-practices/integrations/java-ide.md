# Java IDE Integration

[中文](#zh-cn) · [English](#english)

<a id="english"></a>

Use `lagi-core` only when a Java application needs to embed LinkMind objects or extension points. A separately deployable service should normally use LinkMind's HTTP APIs instead.

<a id="java-ide-setup"></a>

## 1. Build and import the module

Install the local module or publish it to the team's Maven repository:

```bash
mvn clean install -pl lagi-core -am -DskipTests
```

In IntelliJ IDEA or Eclipse, add the matching Maven dependency and place a non-secret `lagi.yml` on the classpath, or provide its managed path through `-Dlinkmind.config=/path/to/lagi.yml`.

```xml
<dependency>
  <groupId>com.landingbj</groupId>
  <artifactId>lagi-core</artifactId>
  <version>1.2.4</version>
</dependency>
```

### Directly Import the JAR

If you only want to reuse LinkMind capabilities in an existing Java project:

1. Download `lagi-core-*-jar-with-dependencies.jar`.
2. Put it into your project's `lib` directory.
3. Provide an external `lagi.yml` or place one in your runtime `resources`.
4. Build and run your project as usual.

External configuration overrides the default embedded configuration.

### In Eclipse

1. Open `File > Import...`.
2. Choose `General > Existing Projects into Workspace`.
3. Select your project directory and finish the import.
4. Open project `Properties > Java Build Path > Libraries`.
5. Add the required LinkMind JARs or the generated `lagi-core` dependency.
6. Run `Build Project`.

### In IntelliJ IDEA

1. Open `File > Open`.
2. Choose your project directory.
3. Add LinkMind through Maven dependency or local JARs.
4. Reimport or sync the project.
5. Build the project to verify all classes resolve correctly.

### Common IDE Issues

- If the JAR cannot be resolved, check whether the file really exists and was added to the correct module.
- If classes still appear missing, refresh Maven or rebuild the project index.
- If runtime behavior does not match expectation, verify which `lagi.yml` is actually being loaded.

## 2. Run and verify

Initialize the context once during application startup, then create the relevant service request. Keep Provider keys outside the project and inject configuration for each environment. Run the existing examples in `lagi-core/src/test/java/ai/example/Demo.java`, then add one short chat request from the application and verify its model, route, and log record.

For a service that should remain independently deployable, prefer the HTTP API instead. Build outputs and deployment options are in [Deployment and Security](../../deployment-and-security_en.md) / [部署、安全与级联组网](../../deployment-and-security_zh.md); HTTP and secondary-development boundaries are in [System Integration](../../integration_en.md) / [系统集成与二次开发](../../integration_zh.md).

<a id="zh-cn"></a>

## 中文

在源码仓库执行 `mvn clean install -pl lagi-core -am -DskipTests`。将匹配版本的 Maven 依赖加入应用：

```xml
<dependency>
  <groupId>com.landingbj</groupId>
  <artifactId>lagi-core</artifactId>
  <version>1.2.4</version>
</dependency>
```

### 直接导入 JAR

如果你只是想在现有 Java 项目里复用 LinkMind 的能力：

1. 下载 `lagi-core-*-jar-with-dependencies.jar`。
2. 放进项目的 `lib` 目录。
3. 提供外部 `lagi.yml`，或放到运行时 `resources` 中。
4. 按原有方式构建并启动项目。

运行时外部配置会覆盖 JAR 内置的默认配置。

### 在 Eclipse 中接入

1. 打开 `File > Import...`。
2. 选择 `General > Existing Projects into Workspace`。
3. 选择项目目录并完成导入。
4. 打开项目 `Properties > Java Build Path > Libraries`。
5. 加入 LinkMind 相关 JAR，或加入打好的 `lagi-core` 依赖。
6. 执行 `Build Project`。

### 在 IntelliJ IDEA 中接入

1. 打开 `File > Open`。
2. 选择你的项目目录。
3. 通过 Maven 依赖或本地 JAR 的方式加入 LinkMind。
4. 重新同步项目。
5. 执行一次构建，确认类都能正确解析。

### IDE 常见问题

- 如果 JAR 无法识别，先确认文件真实存在，且加入到了正确的模块。
- 如果类仍然提示缺失，尝试刷新 Maven 或重建 IDE 索引。
- 如果运行行为和预期不一致，优先确认当前到底加载的是哪一份 `lagi.yml`。

启动应用时调用一次 `ContextLoader.loadContext()`，发一条短消息并核对模型、路由和服务日志。在源码中可运行 `lagi-core/src/test/java/ai/example/Demo.java`。外部配置示例为 `-Dlinkmind.config=/path/to/lagi.yml`。[Java 集成与示例](../../integration_zh.md#sdk-config)
