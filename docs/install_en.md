# LinkMind Installation Guide

This guide is intentionally optimized for first-time users. The four options below are alternatives, not sequential steps. Choose the one that best fits how you want to run LinkMind.

## Option 1. Official Installer

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

### Choose a Runtime Mode

During installation, LinkMind will ask you to choose a runtime mode:

| Mode | Choose this when |
| --- | --- |
| `Agent Mate` | OpenClaw, Hermes Agent, or DeerFlow is already part of your local workflow and you want LinkMind to sit in the middle as the shared AI layer |
| `Agent Server` | You want a standalone LinkMind service first, or you are evaluating the web console and API directly |

If you are only trying LinkMind for the first time, start with `Agent Server`.

### First Launch

After installation, the script can start LinkMind for you immediately. The success flow looks like this:

1. The installer prints `LinkMind installed successfully!`
2. It asks `Would you like to start LinkMind now?`
3. Enter `yes`
4. Wait for the server startup message
5. Open `http://localhost:8080`

### First-Time Console Steps

1. Open the web console
2. Register or sign in
3. Open the API key or provider settings page
4. Fill in at least one real provider key
5. Return to chat and send a first message

If you are calling the REST API directly and auth is enabled, copy the LinkMind API key from the console and send it as:

```http
Authorization: Bearer <your-linkmind-api-key>
```

## Option 2. Download Packaged Jar

Use this flow when you want a ready-to-run package without building from source.

### Packaged Downloads

- Application package: `LinkMind.jar` ([Download](https://downloads.landingbj.com/lagi/installer/LinkMind.jar))
- Core library: `lagi-core-1.2.0-jar-with-dependencies.jar` ([Download](https://downloads.landingbj.com/lagi/lib/lagi-core-1.2.0-jar-with-dependencies.jar))

### Start

- Windows

  ```powershell
  java -jar LinkMind.jar
  ```

- macOS / Linux

  ```bash
  java -jar LinkMind.jar
  ```

### What Happens on First Run

LinkMind automatically creates:

- `config/`
- `data/`
- `config/lagi.yml`

Then open:

- `http://localhost:8080`

### Default Output Locations

If you run the JAR from `D:\LinkMind` or `~/LinkMind`, LinkMind keeps the generated config and data directories next to that JAR by default.

## Option 3. With Docker Image

Use this flow when you want a prebuilt container image.

### Image

- Image name: `landingbj/linkmind`

### Pull

```bash
docker pull landingbj/linkmind
```

### Start the Container

```bash
docker run -d -p 8080:8080 landingbj/linkmind
```

Then open `http://localhost:8080`.

## Option 4. Build from Source

Use this path when you want the latest local code or need both the executable JAR and the WAR package.

### Package

```bash
mvn clean package -pl lagi-web -am -DskipTests -U
```

### Build Outputs

The current Maven build produces:

- `lagi-web/target/LinkMind.jar`
- `lagi-web/target/ROOT.war`

### Run the Packaged JAR

```bash
java -jar lagi-web/target/LinkMind.jar
```

### Deploy the WAR

If you prefer a servlet container, deploy:

- `lagi-web/target/ROOT.war`

The embedded JAR remains the recommended default for local evaluation and daily operation.

## Common Runtime Options For JAR Launches

These flags apply when you start `LinkMind.jar` directly, including builds produced from source.

### Change the Port

```bash
java -jar LinkMind.jar --port=8090
```

Then open `http://localhost:8090`.

### Bind to a Specific Host

```bash
java -jar LinkMind.jar --host=0.0.0.0
```

### Use Custom Config and Data Directories

- Windows

  ```powershell
  java -jar LinkMind.jar --config=D:\LinkMindConfig --data-dir=D:\LinkMindData
  ```

- macOS / Linux

  ```bash
  java -jar LinkMind.jar --config=/opt/linkmind/config --data-dir=/var/lib/linkmind/data
  ```

### Preselect the Runtime Mode

```bash
java -jar LinkMind.jar --runtime-choice=server
```

Valid values are `mate` and `server`.

### Point DeerFlow Sync at a Custom Path

```bash
java -jar LinkMind.jar --deer-flow-path=/path/to/deer-flow
```

## What To Configure Next

After the service is running, continue in this order:

1. [Configuration Reference](config_en.md)
2. [API Reference](API_en.md)
3. [Tutorial](tutor_en.md)
4. [Integration Guide](guide_en.md)

If you want RAG, local vector storage, or document ingestion, also read the [Annex](annex_en.md).
