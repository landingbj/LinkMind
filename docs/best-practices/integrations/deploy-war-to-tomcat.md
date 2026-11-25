# Deploy a WAR to Tomcat

## 构建与部署 / Build and deploy

Build the web module from source:

```bash
mvn clean package -pl lagi-web -am -DskipTests -U
```

Deploy `lagi-web/target/ROOT.war` to the selected Tomcat instance, keep configuration and data in managed external locations, then restart that one service instance. Verify the deployment URL, API authentication, one short model call, and the application log before directing clients to it.

Use the same firewall, TLS, secret-management, and rollback controls as a JAR deployment. See [Deployment and Security](../../deployment-and-security_en.md) / [部署、安全与级联组网](../../deployment-and-security_zh.md).
