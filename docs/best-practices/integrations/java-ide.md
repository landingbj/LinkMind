# Java IDE Integration

Use `lagi-core` only when a Java application needs to embed LinkMind objects or extension points. A separately deployable service should normally use LinkMind's HTTP APIs instead.

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

## 2. Run and verify

Initialize the context once during application startup, then create the relevant service request. Keep Provider keys outside the project and inject configuration for each environment. Run the existing examples in `lagi-core/src/test/java/ai/example/Demo.java`, then add one short chat request from the application and verify its model, route, and log record.

For a service that should remain independently deployable, prefer the HTTP API instead. Build outputs and deployment options are in [Deployment and Security](../../deployment-and-security_en.md) / [部署、安全与级联组网](../../deployment-and-security_zh.md); HTTP and secondary-development boundaries are in [System Integration](../../integration_en.md) / [系统集成与二次开发](../../integration_zh.md).
