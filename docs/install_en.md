# LinkMind Installation Guide

Prerequisite: LinkMind requires **JDK 8 or later**. If Java is not installed yet, jump to [Appendix: Install JDK 8+](#appendix-install-jdk-8).

This page treats the four installation paths as alternatives, not sequential steps. Pick the one that best matches how you want to run LinkMind.

## Option 1. Official Installer

### Install

- Windows PowerShell

  ```powershell
  iwr -useb https://cdn.linkmind.top/install.ps1 | iex
  ```

- macOS / Linux

  ```bash
  curl -fsSL https://cdn.linkmind.top/install.sh | bash
  ```

### Choose a Runtime Mode

During installation, LinkMind asks you to choose a runtime mode:

| Mode | Choose this when |
| --- | --- |
| `Agent Mate` | OpenClaw, Hermes Agent, or DeerFlow is already part of your local workflow and you want LinkMind to sit in the middle as the shared AI layer |
| `Agent Server` | You want a standalone LinkMind service first, or you are evaluating the web console and API directly |

If you are only trying LinkMind for the first time, start with `Agent Server`.

### What the Installer Does

The current installer does more than just download one file:

1. Creates the LinkMind home directory, usually `%USERPROFILE%\\LinkMind` on Windows or `~/LinkMind` on macOS and Linux.
2. Downloads `LinkMind.jar` into that directory.
3. Asks whether LinkMind should run as `Agent Mate` or `Agent Server`.
4. If you choose `Agent Mate`, it asks which runtime should be injected or synchronized: OpenClaw, DeerFlow, or Hermes. DeerFlow also asks for its install directory.
5. If you choose `Agent Server`, it downloads the bundled popular skills package and expands it under `skills/popular_skills`.
6. Runs the runtime initializer so that the first startup already knows which runtime mode and skill settings to use.

### First Launch

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

### macOS Notes

- Run the installer from Terminal instead of double-clicking downloaded files.
- If `java -version` still fails after you install JDK, open a new Terminal window or run `source ~/.zshrc`.
- On Apple Silicon Macs, choose an `aarch64` or `arm64` JDK package in the appendix below.

### First-Time Console Steps

1. Open the web console.
2. Register or sign in.
3. Open the API key or provider settings page.
4. Fill in at least one real provider key.
5. Return to chat and send a first message.

If you are calling the REST API directly and auth is enabled, copy the LinkMind API key from the console and send it as:

```http
Authorization: Bearer <your-linkmind-api-key>
```

## Option 2. Download Packaged Jar

Use this flow when you want a ready-to-run package without building from source.

### Packaged Downloads

- Application package: `LinkMind.jar` ([Download](https://cdn.linkmind.top/installer/LinkMind.jar))
- Core library: `lagi-core-1.2.0-jar-with-dependencies.jar` ([Download](https://ai.linkmind.top/lagi/lib/lagi-core-1.2.0-jar-with-dependencies.jar))

### Prepare and Start

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

### What Happens on First Run

When you launch the packaged JAR without custom path arguments, LinkMind automatically:

- creates `config/`
- creates `data/`
- writes `config/lagi.yml`
- copies bundled local data assets into `data/`

Then open:

- `http://localhost:8080`

### Default Output Locations

If you run the JAR from `D:\LinkMind`, `~/Documents/LinkMind`, or `~/LinkMind`, LinkMind keeps the generated config and data directories next to that JAR by default.

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

## Appendix: Install JDK 8+

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
