# Deployment, Security, and Cascade Networking

Use this page to choose a deployment, set runtime options, verify production readiness, and keep models, RAG, Agents, and cascade nodes inside a controlled network boundary.

[Installer](#installer-steps) · [Install a JDK](#install-jdk) · [Standalone JAR](#packaged-jar) · [Source and WAR](#source-and-war) · [Startup options](#jar-options) · [Client sync](#runtime-sync) · [Security](#security-baseline) · [Cascade](#interaction-proxy)

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

<a id="installer-steps"></a>

### Choose installer components and start LinkMind

<a id="installer-options"></a>

#### Optional Installation Items

LinkMind has one unified runtime. The installer independently offers optional Agent frontend integration for OpenClaw, Hermes Agent, DeerFlow, or OpenHuman; popular skills; and Medusa acceleration resources. These choices can be combined and no longer define a product version.

<a id="installer-actions"></a>

#### What the Installer Does

The current installer does more than just download one file:

1. Creates the LinkMind home directory, usually `%USERPROFILE%\LinkMind` on Windows or `~/LinkMind` on macOS and Linux.
2. Downloads `LinkMind.jar` into that directory.
3. Optionally asks which installed Agent frontend to connect: OpenClaw, DeerFlow, Hermes, or OpenHuman. DeerFlow asks for its install directory; OpenHuman accepts an optional config directory or `config.toml` path and can also auto-detect the default workspace.
4. Optionally downloads the bundled popular skills package and expands it under `skills/popular_skills`.
5. Optionally downloads Medusa acceleration resources.
6. Runs initialization for the selected Skills, Medusa, and Agent integration settings.

<a id="installer-first-launch"></a>

#### First Launch

After installation, the script can start LinkMind for you immediately. The normal success flow is:

1. The installer prints `LinkMind installed successfully!`
2. It asks `Would you like to start LinkMind now?`
3. Enter `yes`
4. Wait for the startup logs to finish
5. Open `http://localhost:8080`

On the first real launch, LinkMind automatically prepares:

- `config/`
- `config/lagi.yml`
- `data/`
- bundled local data files copied into `data/` when you are using the default runtime paths

<a id="installer-macos"></a>

#### macOS Notes

- Run the installer from Terminal instead of double-clicking downloaded files.
- If `java -version` still fails after you install JDK, open a new Terminal window or run `source ~/.zshrc`.
- On Apple Silicon Macs, choose an `aarch64` or `arm64` JDK package in the appendix below.

<a id="packaged-jar"></a>

### Start the downloaded JAR on your operating system

Place the downloaded `LinkMind.jar` in the chosen directory, then run the commands for your system.

- Windows

  ```powershell
  mkdir D:\LinkMind
  cd D:\LinkMind
  java -jar LinkMind.jar
  ```

- macOS

  ```bash
  mkdir -p ~/Documents/LinkMind
  cd ~/Documents/LinkMind
  java -jar LinkMind.jar
  ```

- Linux

  ```bash
  mkdir -p ~/LinkMind
  cd ~/LinkMind
  java -jar LinkMind.jar
  ```

<a id="jar-first-run"></a>

#### What Happens on First Run

When you launch the packaged JAR without custom path arguments, LinkMind automatically:

- creates `config/`
- creates `data/`
- writes `config/lagi.yml`
- copies bundled local data assets into `data/`

Then open:

- `http://localhost:8080`

#### Default Output Locations

If you run the JAR from `D:\LinkMind`, `~/Documents/LinkMind`, or `~/LinkMind`, LinkMind keeps the generated config and data directories next to that JAR by default.

<a id="source-and-war"></a>

### Build from source, run in an IDE, or deploy the WAR

If you need a more traditional developer workflow, these options are still valid.

#### Maven Packaging

```bash
git clone https://github.com/landingbj/LinkMind.git
cd LinkMind
mvn clean package -pl lagi-web -am -DskipTests -U
```

Build outputs:

- `lagi-web/target/LinkMind.jar`
- `lagi-web/target/ROOT.war`

#### IDE Workflow

You can still import the project into IntelliJ IDEA or Eclipse, build locally, and run with your own debug configuration.

#### WAR / Tomcat Deployment

If your team still prefers a servlet container:

1. Build `ROOT.war`.
2. Drop it into Tomcat `webapps`.
3. Keep `lagi.yml` aligned with the same models and stores you would use in the standalone JAR.

[Java IDE setup and troubleshooting](best-practices/integrations/java-ide.md#java-ide-setup) · [Deploy to Tomcat](best-practices/integrations/deploy-war-to-tomcat.md#english)

<a id="jar-options"></a>

## 2. Runtime options and configuration directory

The standalone JAR accepts the options below. Pass the directory containing `lagi.yml` and its included files to `--config`. Use `--data-dir` for persistent data.

These flags apply when you start `LinkMind.jar` directly, including builds produced from source.

#### Change the Port

```bash
java -jar LinkMind.jar --port=8090
```

Then open `http://localhost:8090`.

#### Bind to a Specific Host

```bash
java -jar LinkMind.jar --host=0.0.0.0
```

#### Use Custom Config and Data Directories

- Windows

  ```powershell
  java -jar LinkMind.jar --config=D:\LinkMindConfig --data-dir=D:\LinkMindData
  ```

- macOS / Linux

  ```bash
  java -jar LinkMind.jar --config=/opt/linkmind/config --data-dir=/var/lib/linkmind/data
  ```

Command-line options take precedence over the JVM properties `server.port`, `server.address`, `config.dir`, and `data.dir`. Their environment variables are `SERVER_PORT`, `SERVER_ADDRESS`, `CONFIG_DIR`, and `DATA_DIR`. Check the bind address and firewall for trusted LAN access. Use an HTTPS gateway for public access. When embedding `lagi-core` or using an external servlet container, select a file with `-Dlinkmind.config=/path/to/lagi.yml` and a local database directory with `-Dai.data.dir=/path/to/data`.

After starting, open `http://localhost:8080`, sign in or verify the management entry point, then make one short chat request using [Models, Routing, and Multimodal](models-and-multimodal_en.md). A valid response, matching control record, and healthy logs together prove deployment success.

<a id="runtime-sync"></a>

### Synchronize DeerFlow and OpenHuman settings

<a id="deerflow-sync"></a>

#### Point DeerFlow Sync at a Custom Path

```bash
java -jar LinkMind.jar --deer-flow-path=/path/to/deer-flow
```

<a id="openhuman-sync"></a>

#### Point OpenHuman Sync at a Custom Path

```bash
java -jar LinkMind.jar --openhuman-path=/path/to/openhuman/config.toml
```

If this flag is empty, LinkMind checks `OPENHUMAN_WORKSPACE` first and then the default OpenHuman user config under `~/.openhuman/users/<user_id>/config.toml` or `~/.openhuman/users/local/config.toml`.

For OpenHuman, set `LINKMIND_API_KEY` before running the installer or starting `LinkMind.jar`; LinkMind writes the `linkmind` provider as Bearer auth, binds the `provider:linkmind` auth profile, and stores the token in OpenHuman's desktop keychain file when that file is present.

To skip client synchronization for this launch, run `java -jar LinkMind.jar --enable-sync=false`.

<a id="security-baseline"></a>

## 3. Security baseline and runtime investigation

LinkMind provides the controls below. The operator remains responsible for the deployment and for any security, privacy, and compliance assessments applicable to the data, industry, and region.Obtain any required certification from the relevant assessor and legal advice from qualified counsel for the actual deployment.

For incidents, record time, request ID, model/route, redacted error, and first/last-event times. Never put raw keys, user data, or complete private documents into a ticket.

<a id="security-3"></a>

### Responsibility Boundary

LinkMind provides middleware-level security controls and governance entry points, but production security still depends on the deployer's environment configuration, permission model, secret management, network policy, data classification process, and model provider choices.

Deployers should verify:

- Whether `functions.chat.enable_auth`, `functions.chat.enable_policy`, and `filters.enable` are enabled.
- Whether `functions.chat.filter` references the required filters, such as `sensitive`, `priority`, `stopping`, and `continue`.
- Whether models, vector stores, MCP services, Skills, Workers, and external agent runtimes are exposed only to trusted sources.
- Whether logs, caches, uploaded files, RAG indexes, and temporary workspaces follow the organization's data retention and access policies.

<a id="security-4"></a>

### Safety Guardrails

LinkMind guardrails are composed through configuration rather than being tied to a single model or endpoint. Core controls include:

- Sensitive-word filtering: configure `mask`, `erase`, `block`, and related action levels through `filters.items[].groups`.
- Priority keywords: use `priority` rules to influence conversation scheduling or business priority.
- Stopping keywords: use `stopping` rules to terminate conversation paths that should not continue.
- Continuation keywords: use `continue` rules to recognize sessions that should preserve context.
- Built-in policy checks: enable runtime policy checks with `functions.chat.enable_policy`.

<a id="filter-rules"></a>

#### Configure sensitive, priority, stopping, and continuation rules

Sensitive-word example:

```yaml
filters:
  enable: true
  items:
    - name: sensitive
      groups:
        - level: mask
          rules: >-
            example-name,internal-code
        - level: erase
          rules: >-
            your context
        - level: block
          rules: >-
            blocked-term,restricted-term
```

Priority example:

```yaml
filters:
  items:
    - name: priority
      rules: >-
        car,weather,
        social*security
```

Stopping example:

```yaml
filters:
  items:
    - name: stopping
      rules: >-
        bye,
        start*
```

Continuation example:

```yaml
filters:
  items:
    - name: continue
      rules: >-
        about,next,then
```

Common fields:

| Field | Meaning |
| --- | --- |
| `enable` | Global filter switch |
| `items[].name` | Filter name referenced from `functions.chat.filter` |
| `items[].groups[].level` | Action level for sensitive-word groups, such as `mask`, `erase`, or `block` |
| `items[].groups[].rules` | Comma-separated or regex-style rules for grouped sensitive filters |
| `items[].rules` | Keyword list for non-grouped filters such as priority, stopping, or continuation |

`functions.chat.filter` should reference these filter names as a comma-separated list.

<a id="security-5"></a>

### Data And Privacy

LinkMind can connect business systems, private knowledge bases, vector databases, model providers, and agent runtimes. Deployers should govern data by sensitivity level:

- Do not place real API keys, secrets, personal data, or customer data in public demos, test configurations, or commits.
- Apply the same access controls to RAG documents, vector indexes, caches, logs, and uploaded files as to their source data.
- Before connecting third-party models or cloud agent platforms, review data transfer, training use, log retention, and cross-border processing terms.
- Apply the same classification and redaction requirements to OCR, ASR/TTS, image, video, Text-to-SQL, and document-processing workflows.
- Establish audit and deletion processes for inputs or outputs containing personal data, credentials, trade secrets, or regulated data.

<a id="security-6"></a>

### Access Control And Secrets

LinkMind supports API keys, key pools, authentication switches, and billing-related interfaces. Production deployments should follow least privilege:

- Use dedicated service accounts and least-privilege keys instead of personal development keys.
- Store secrets in secure configuration management or a secret manager, not in the repository.
- Use distinguishable keys for different environments, tenants, and providers, with rotation and revocation procedures.
- Protect management interfaces such as `/apiKey/*`, and `/credit/*` with outer access controls.
- Use network isolation or trusted network policies for reverse proxies, consoles, management APIs, and internal cascade nodes.

<a id="security-7"></a>

### RAG, Documents, And Retrieval

RAG and document pipelines transform source files into retrievable content, so derived data must be protected as well:

- Vector stores, full-text indexes, graph-enhanced data, and caches should inherit the sensitivity level of source documents.
- When source documents are deleted or updated, derived indexes, caches, and trace records should be handled accordingly.
- Multi-tenant deployments should isolate knowledge bases, index namespaces, file storage paths, and access credentials.
- For Text-to-SQL, restrict database account permissions and apply extra validation to the execution scope of generated SQL.

<a id="security-8"></a>

### Agents, MCP, And Skills

Agents, MCP, Skills, Workers, and Pnps expand system capabilities and the execution surface. Review them before production use:

- Enable only MCP services, Skills, Workers, and agent runtimes from trusted sources.
- Configure least-privilege credentials for external tools and limit filesystem, network, and command execution scope.
- Use approval, audit, or sandboxing policies for tool calls that may produce side effects.
- Review runtime sync settings regularly to avoid accidental local agent configuration overwrites or drift.

<a id="security-9"></a>

### Cascade Networking

Cascade networking combines multiple LinkMind nodes into a larger runtime network. Deployments should ensure:

- Each node independently manages local data, concurrency, permissions, and backend credentials.
- Cascade links connect only trusted nodes and are protected through network controls, authentication, or reverse proxy policy.
- Data from different business domains, tenants, or security levels is physically or logically isolated.
- Nodes pass only the minimum context required to complete a task.

<a id="security-10"></a>

### Production Checklist

- Enable authentication, policy checks, and required filters.
- Replace all sample keys, default secrets, and test endpoints.
- Use HTTPS, trusted reverse proxies, and controlled administration entry points.
- Restrict access sources for consoles, management APIs, MCP services, and cascade nodes.
- Define access controls, retention windows, and cleanup processes for logs, caches, uploads, vector stores, and temporary directories.
- Review data-processing terms for model providers, cloud agent platforms, and third-party services.
- Establish configuration-change auditing, key rotation, vulnerability response, and abnormal-call alerting.

<a id="security-11"></a>

### Vulnerabilities And Security Issues

If you discover a security issue, do not disclose exploitable details in a public issue. Use the private channel designated by the maintainers and include reproduction steps, impact scope, version information, and suggested mitigations. After confirmation, maintainers should prioritize fixes, upgrade notes, or temporary mitigation guidance.

<a id="interaction-proxy"></a>

## 4. Interaction cascade and network verification

Use `general.cascade_api_address` for interaction-channel and API-key management proxying. It is different from a provider's `models[].api_address`:

```yaml
general:
  cascade_api_address: https://linkmind-node.example.com
```

An empty value keeps handling on the current node. Connect cascades only to trusted nodes, and manage local data, concurrency, permissions, and upstream credentials independently per node. The old `general.mode: mate/server` is compatibility-only and should not be used in new configuration.

After release or migration, verify in order: DNS/TLS/firewall, console sign-in, one short call for each enabled model, rejection of an invalid API key, RAG, Agent/MCP, cascade target, and backup/rollback.

Use **Interactions → Cascade** to view or edit the upstream node. After saving, read `cascadeProxyEnabled` from `/socialChannel/runningMode` and verify forwarding with a known channel. The legacy `general.mode` value no longer selects runtime behavior.

<a id="install-jdk"></a>

## Appendix: Install and verify a JDK

Choose JDK 8 or later for your OS and CPU architecture. If the Linux package repository does not carry JDK 8, use a supported newer JDK or the Temurin archive instructions below.

JDK is required to run or develop LinkMind. Temurin OpenJDK is the safest default when you do not already have Java installed.

### Windows

1. Download JDK 8 from [Eclipse Temurin](https://adoptium.net/temurin/releases/) or [Oracle Java](https://www.oracle.com/java/technologies/downloads/#java8).
2. Choose the Windows x64 installer.
3. Install it with the default wizard. Temurin `.msi` can usually add `PATH` automatically.
4. If needed, set:

   - `JAVA_HOME=C:\Development_tools\jdk1.8.0_xxx`
   - add `%JAVA_HOME%\bin` to `Path`

5. Verify:

   ```powershell
   java -version
   javac -version
   ```

### macOS

#### Temurin OpenJDK

1. Open [Eclipse Temurin Releases](https://adoptium.net/temurin/releases/).
2. Choose JDK 8 for macOS.
3. Pick `aarch64` for Apple Silicon or `x64` for Intel Macs.
4. Install the `.pkg`.

If `JAVA_HOME` is not set automatically, add it manually:

```bash
# zsh (default on modern macOS)
nano ~/.zshrc
```

Then add:

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH
```

Reload the shell:

```bash
source ~/.zshrc
```

Verify:

```bash
java -version
javac -version
echo $JAVA_HOME
```

#### Oracle JDK

You can also install Oracle JDK 8 from the Oracle download page, then set `JAVA_HOME` the same way.

### Linux

#### Package Manager

- Ubuntu / Debian

  ```bash
  sudo apt update
  sudo apt install openjdk-8-jdk
  sudo update-alternatives --config java
  ```

- CentOS / RHEL / Fedora

  ```bash
  sudo yum install java-1.8.0-openjdk-devel
  # or on newer Fedora
  sudo dnf install java-1.8.0-openjdk-devel
  ```

- Arch Linux

  ```bash
  sudo pacman -S jdk8-openjdk
  ```

#### Manual Temurin Install

1. Download the Linux JDK 8 `.tar.gz` from Temurin.
2. Extract it, for example under `/opt/java`.
3. Add `JAVA_HOME` and `PATH` in `~/.bashrc`, `~/.profile`, or `~/.zshrc`.

Example:

```bash
export JAVA_HOME=/opt/java/jdk8u-xxx
export PATH=$JAVA_HOME/bin:$PATH
```

Reload your shell and verify:

```bash
java -version
javac -version
echo $JAVA_HOME
```
