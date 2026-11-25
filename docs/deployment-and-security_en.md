# Deployment, Security, and Cascade Networking

Use this page to choose a deployment form, control the runtime surface, and connect LinkMind nodes safely.

## 1. Deployment options

| Option | Use when | Start point |
| --- | --- | --- |
| Official installer | A workstation or appliance needs the fastest setup | `iwr -useb https://cdn.linkmind.top/install.ps1 | iex` |
| Packaged JAR | You manage Java and configuration directly | `java -jar LinkMind.jar` |
| Docker image | You run containerized infrastructure | `docker run -d -p 8080:8080 landingbj/linkmind` |
| Source / WAR | You need IDE debugging or a Tomcat deployment | `mvn clean package -pl lagi-web -am -DskipTests -U` |

The source build produces `lagi-web/target/LinkMind.jar` and `lagi-web/target/ROOT.war`.

## 2. Runtime controls

For a JAR deployment, use JVM properties rather than editing generated artifacts:

```bash
java -Dserver.port=8080 \
  -Dserver.address=0.0.0.0 \
  -Dlinkmind.config=/path/to/lagi.yml \
  -Dlinkmind.data=/path/to/data \
  -jar LinkMind.jar
```

Bind only the interfaces that clients need. Place reverse proxies, TLS termination, firewall rules, and any public DNS boundary in front of the service rather than exposing an unrestricted development port.

## 3. Security baseline

- Store provider keys and passwords outside source control; redact them from logs and support bundles.
- Require API authentication for shared or network-exposed deployments.
- Grant users, API keys, MCP tools, and Skills only the permissions they need.
- Treat documents, RAG collections, and retrieval traces as private data and apply the same retention and access rules as the source system.
- Record model, route, time, and a redacted error for operational troubleshooting.

The UI browser session is not an administrator credential for backend APIs. Provider access, user authentication, and credits can each use deployment-specific services.

## 4. Interaction cascade

Interaction-channel and API-key management proxying uses `general.cascade_api_address`:

```yaml
general:
  cascade_api_address: https://linkmind-node.example.com
```

Leave it empty to process these operations locally. This is distinct from `models[].api_address`, which is only the upstream address for a model provider. Do not use legacy `general.mode: mate/server` in new configuration; it remains readable only for compatibility.

## 5. Go-live checklist

1. Verify the target host, port, firewall, DNS, and TLS path.
2. Verify each enabled model and route with a short request.
3. Verify API authentication, restricted key access, and secret redaction.
4. Verify RAG, Agent tools, MCP servers, and cascade targets individually.
5. Keep an observable restart, backup, and rollback procedure.

See [Security and Safety](security_en.md) for the complete responsibility and policy guidance.
