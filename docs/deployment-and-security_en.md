# Deployment, Security, and Cascade Networking

Use this page to choose a deployment, set runtime options, verify production readiness, and keep models, RAG, Agents, and cascade nodes inside a controlled network boundary.

## 1. Choose and complete a deployment

| Method | Suitable for | Minimum start command |
| --- | --- | --- |
| Official installer | Quick workstation, desktop, or appliance installation | `iwr -useb https://cdn.linkmind.top/install.ps1 \| iex` |
| Packaged JAR | Managing Java, configuration, and data yourself | `java -jar LinkMind.jar` |
| Docker | Container infrastructure | `docker run -d -p 8080:8080 landingbj/linkmind` |
| Source / WAR | IDE debugging, extension work, or Tomcat | `mvn clean package -pl lagi-web -am -DskipTests -U` |

Running the JAR or building from source requires JDK 8 or later. The installer prepares `LinkMind.jar`, `config/`, `config/lagi.yml`, and `data/` in its default location; a first JAR start also creates missing default directories. Source builds produce `lagi-web/target/LinkMind.jar` and `lagi-web/target/ROOT.war`.

Persist configuration and data outside a Docker writable layer:

```bash
docker run -d --name linkmind \
  -p 8080:8080 \
  -v /srv/linkmind/config:/app/config \
  -v /srv/linkmind/data:/app/data \
  landingbj/linkmind
```

Before production upgrades, back up `lagi.yml`, split configuration files, data, SQLite vector databases, and external-service settings. Validate in a same-version test environment and retain the previous JAR/WAR or image for rollback.

## 2. Runtime options and configuration directory

Use JVM parameters to control port, bind address, configuration, and data:

```bash
java -Dserver.port=8080 \
  -Dserver.address=127.0.0.1 \
  -Dlinkmind.config=/opt/linkmind/config/lagi.yml \
  -Dlinkmind.data=/var/lib/linkmind/data \
  -jar LinkMind.jar
```

Change the bind address to `0.0.0.0` only when trusted LAN clients need it. For public access, put TLS, access controls, and rate limits at a reverse proxy. The file set by `-Dlinkmind.config` is the real main configuration; split settings are loaded by `include_models`, `include_stores`, `include_agents`, `include_mcps`, and `include_pnps`.

After starting, open `http://localhost:8080`, sign in or verify the management entry point, then make one short chat request using [Models, Routing, and Multimodal](models-and-multimodal_en.md). A valid response, matching control record, and healthy logs together prove deployment success.

## 3. Security baseline and runtime investigation

LinkMind provides governance controls, but production security is jointly determined by deployment networking, accounts, secrets, data, and provider policy. At minimum:

1. Enable API authentication on public or shared networks; restrict the console and management APIs to trusted sources.
2. Keep provider keys, passwords, and object-storage credentials out of source, image layers, logs, screenshots, and support bundles. Separate keys by environment and provider; rotate and revoke them.
3. Use HTTPS and a trusted reverse proxy; restrict firewall sources and do not directly expose development ports, MCP servers, or internal cascade nodes.
4. Set access controls and retention for `filters`, sensitive-content rules, policy checks, uploads, cache, logs, vector stores, and temporary directories.
5. Give Agents, MCP servers, Skills, and Workers only required network, file, and tool permissions; side-effecting tools need approval, audit, or sandboxing.
6. When deleting or updating a RAG source, also process its vectors, cache, uploaded files, and tracking records. Use separate categories, paths, and credentials for tenants.

For incidents, record time, request ID, model/route, redacted error, and first/last-event times. Never put raw keys, user data, or complete private documents into a ticket.

## 4. Interaction cascade and network verification

Use `general.cascade_api_address` for interaction-channel and API-key management proxying. It is different from a provider's `models[].api_address`:

```yaml
general:
  cascade_api_address: https://linkmind-node.example.com
```

An empty value keeps handling on the current node. Connect cascades only to trusted nodes, and manage local data, concurrency, permissions, and upstream credentials independently per node. The old `general.mode: mate/server` is compatibility-only and should not be used in new configuration.

After release or migration, verify in order: DNS/TLS/firewall, console sign-in, one short call for each enabled model, rejection of an invalid API key, RAG, Agent/MCP, cascade target, and backup/rollback.
