# Deploy a WAR to Tomcat

Use this recipe when an existing Tomcat estate requires a WAR. Keep the application artifact, external configuration, data, and Tomcat service lifecycle separate so a release never overwrites production secrets or persistent data.

## 1. Build the WAR

Build the web module from source:

```bash
mvn clean package -pl lagi-web -am -DskipTests -U
```

The result is `lagi-web/target/ROOT.war`. Record its build commit and checksum before copying it to a server. Do not package real Keys into the WAR.

## 2. Deploy and configure one Tomcat instance

Stop only the target Tomcat service, back up its current WAR and external `lagi.yml`, then deploy `ROOT.war` to that instance's `webapps` directory. Keep configuration and data in managed external locations and pass their paths through the service's JVM options, for example `-Dlinkmind.config=/path/to/lagi.yml` and `-Dlinkmind.data=/path/to/data`.

Start the same instance and wait for the WAR to unpack. Verify the deployment URL, API authentication, one short model call, and the application log before directing clients to it. If verification fails, restore the previous WAR and configuration backup rather than editing the unpacked webapp in place.

Use the same firewall, TLS, secret-management, and rollback controls as a JAR deployment. See [Deployment and Security](../../deployment-and-security_en.md) / [部署、安全与级联组网](../../deployment-and-security_zh.md).
