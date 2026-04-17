# LinkMind Installation Guide

This guide is intentionally optimized for first-time users. Start with the installer if you want the fastest path. Use the JAR or source build flow only when you need more control.

## 1. Fastest Path: Official Installer

### Prerequisites

- JDK 8 or later
- A terminal with internet access

Temurin OpenJDK is a good default if you do not already have Java installed.

### Install

- Windows PowerShell

  ```powershell
  iwr -useb https://downloads.landingbj.com/install.ps1 | iex
  ```

- macOS / Linux

  ```bash
  curl -fsSL https://downloads.landingbj.com/install.sh | bash
  ```

### Choose a runtime mode

During installation, LinkMind will ask you to choose a runtime mode:

| Mode | Choose this when |
| --- | --- |
| `Agent Mate` | OpenClaw, Hermes Agent, or DeerFlow is already part of your local workflow and you want LinkMind to sit in the middle as the shared AI layer |
| `Agent Server` | You want a standalone LinkMind service first, or you are evaluating the web console and API directly |

If you are only trying LinkMind for the first time, start with `Agent Server`.

### First launch

After installation, the script can start LinkMind for you immediately. The success flow looks like this:

1. The installer prints `LinkMind installed successfully!`
2. It asks `Would you like to start LinkMind now?`
3. Enter `yes`
4. Wait for the server startup message
5. Open `http://localhost:8080`

### First-time console steps

1. Open the web console
2. Register or sign in
3. Open the API key or provider settings page
4. Fill in at least one real provider key
5. Return to chat and send a first message

If you are calling the REST API directly and auth is enabled, copy the LinkMind API key from the console and send it as:

```http
Authorization: Bearer <your-linkmind-api-key>
```

## 2. Run `LinkMind.jar` Directly

Use this flow when you already have the packaged JAR.

### Start

- Windows

  ```powershell
  java -jar LinkMind.jar
  ```

- macOS / Linux

  ```bash
  java -jar LinkMind.jar
  ```

### What happens on first run

LinkMind automatically creates:

- `config/`
- `data/`
- `config/lagi.yml`

Then open:

- `http://localhost:8080`

### Default output locations

If you run the JAR from `D:\LinkMind` or `~/LinkMind`, LinkMind keeps the generated config and data directories next to that JAR by default.

## 3. Build From Source

Use this path when you want the latest local code or need both the executable JAR and the WAR package.

### Package

```bash
mvn clean package -pl lagi-web -am -DskipTests -U
```

### Build outputs

The current Maven build produces:

- `lagi-web/target/LinkMind.jar`
- `lagi-web/target/ROOT.war`

### Run the packaged JAR

```bash
java -jar lagi-web/target/LinkMind.jar
```

### Deploy the WAR

If you prefer a servlet container, deploy:

- `lagi-web/target/ROOT.war`

The embedded JAR remains the recommended default for local evaluation and daily operation.

## 4. Common Runtime Options

LinkMind supports several practical startup flags.

### Change the port

```bash
java -jar LinkMind.jar --port=8090
```

Then open `http://localhost:8090`.

### Bind to a specific host

```bash
java -jar LinkMind.jar --host=0.0.0.0
```

### Use custom config and data directories

- Windows

  ```powershell
  java -jar LinkMind.jar --config=D:\LinkMindConfig --data-dir=D:\LinkMindData
  ```

- macOS / Linux

  ```bash
  java -jar LinkMind.jar --config=/opt/linkmind/config --data-dir=/var/lib/linkmind/data
  ```

### Preselect the runtime mode

```bash
java -jar LinkMind.jar --runtime-choice=server
```

Valid values are `mate` and `server`.

### Point DeerFlow sync at a custom path

```bash
java -jar LinkMind.jar --deer-flow-path=/path/to/deer-flow
```

## 5. What To Configure Next

After the service is running, continue in this order:

1. [Configuration Reference](config_en.md)
2. [API Reference](API_en.md)
3. [Tutorial](tutor_en.md)
4. [Integration Guide](guide_en.md)

If you want RAG, local vector storage, or document ingestion, also read the [Annex](annex_en.md).
