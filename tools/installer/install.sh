#!/bin/sh
set -eu

# Check for JDK 8
java_found=false
if command -v java >/dev/null 2>&1; then
    java_version_output=$(java -version 2>&1)
    if echo "$java_version_output" | grep -qE '"1\.8\.|version "1\.8'; then
        java_found=true
    fi
fi

if [ "$java_found" = false ]; then
    echo "Error: JDK 8 is required but was not found."
    echo "Please install JDK 8 and make sure 'java' is available in your PATH."
    exit 1
fi

LINKMIND_DIR="$HOME/LinkMind"
JAR_NAME="LinkMind.jar"
DOWNLOADS_HOST="cdn.linkmind.top"
DOWNLOAD_URL="https://${DOWNLOADS_HOST}/installer/LinkMind.jar"
POPULAR_SKILLS_URL="https://${DOWNLOADS_HOST}/installer/popular_skills.zip"
#DOWNLOAD_URL="http://localhost:8000/LinkMind.jar"
JAR_PATH="$LINKMIND_DIR/$JAR_NAME"
SKILLS_ROOT=""

# 1. Ensure LinkMind directory exists
if [ ! -d "$LINKMIND_DIR" ]; then
    mkdir -p "$LINKMIND_DIR"
    echo "Created directory: $LINKMIND_DIR"
else
    echo "Directory already exists: $LINKMIND_DIR"
fi

# 2-3. Download jar to a temp file with progress, then move to target
TEMP_FILE="$(mktemp "${TMPDIR:-/tmp}/LinkMind_XXXXXXXXXX.jar")"
cleanup() { rm -f "$TEMP_FILE"; }
trap cleanup EXIT

echo "Downloading $DOWNLOAD_URL ..."

if command -v curl >/dev/null 2>&1; then
    if ! curl -fL --progress-bar -o "$TEMP_FILE" "$DOWNLOAD_URL"; then
        echo "Error: Failed to download $DOWNLOAD_URL"
        exit 1
    fi
elif command -v wget >/dev/null 2>&1; then
    if ! wget --show-progress -q -O "$TEMP_FILE" "$DOWNLOAD_URL"; then
        echo "Error: Failed to download $DOWNLOAD_URL"
        exit 1
    fi
else
    echo "Error: Neither curl nor wget is available. Please install one of them."
    exit 1
fi

cp -f "$TEMP_FILE" "$JAR_PATH"
echo "Download complete: $JAR_PATH"

# 4-5. Ask user questions and run InstallerUtil
read_yes_no() {
    prompt="$1"
    printf "%s (yes/no) [no]: " "$prompt"
    read -r answer < /dev/tty
    answer=$(echo "$answer" | tr '[:upper:]' '[:lower:]' | xargs)
    if [ "$answer" = "yes" ] || [ "$answer" = "y" ]; then
        return 0
    else
        return 1
    fi
}

# export_val="false"
# import_val="false"
runtime_choice="mate"
inject_agent=0
deer_flow_path=""

read_runtime_choice() {
    while true; do
        echo "Runtime Choice:"
        echo "  1) as Agent Mate"
        echo "  2) as Agent Server"
        printf "Please choose [1]: "
        read -r answer < /dev/tty
        answer=$(echo "$answer" | tr '[:upper:]' '[:lower:]' | xargs)
        if [ -z "$answer" ] || [ "$answer" = "1" ] || [ "$answer" = "mate" ]; then
            runtime_choice="mate"
            return 0
        elif [ "$answer" = "2" ] || [ "$answer" = "server" ]; then
            runtime_choice="server"
            return 0
        fi
        echo "Invalid choice. Please enter 1 or 2."
    done
}

read_runtime_choice

read_deer_flow_path() {
    while true; do
        printf "Please enter deer-flow install directory: "
        read -r answer < /dev/tty
        answer=$(echo "$answer" | xargs)
        if [ -z "$answer" ]; then
            echo "deer-flow install directory is required."
            continue
        fi
        if [ ! -d "$answer" ]; then
            echo "Directory does not exist: $answer"
            continue
        fi
        deer_flow_path="$answer"
        return 0
    done
}

read_inject_agent_choice() {
    while true; do
        echo "Inject Agent Framework:"
        echo "  1) openclaw"
        echo "  2) deer-flow"
        echo "  3) hermes"
        printf "Please choose [1]: "
        read -r answer < /dev/tty
        answer=$(echo "$answer" | tr '[:upper:]' '[:lower:]' | xargs)
        if [ -z "$answer" ] || [ "$answer" = "1" ] || [ "$answer" = "openclaw" ]; then
            inject_agent=1
            return 0
        elif [ "$answer" = "2" ] || [ "$answer" = "deer-flow" ] || [ "$answer" = "deerflow" ]; then
            inject_agent=$((1 << 1))
            return 0
        elif [ "$answer" = "3" ] || [ "$answer" = "hermes" ]; then
            inject_agent=$((1 << 2))
            return 0
        fi
        echo "Invalid choice. Please enter 1, 2 or 3."
    done
}

if [ "$runtime_choice" = "mate" ]; then
    read_inject_agent_choice
    if [ "$inject_agent" -eq $((1 << 1)) ]; then
        read_deer_flow_path
    fi
fi

if [ "$runtime_choice" = "server" ]; then
    POPULAR_SKILLS_ZIP="$LINKMIND_DIR/popular_skills.zip"
    SKILLS_ROOT="$LINKMIND_DIR/skills/popular_skills"
    mkdir -p "$SKILLS_ROOT"
    echo "Downloading $POPULAR_SKILLS_URL ..."
    if command -v curl >/dev/null 2>&1; then
        if ! curl -fL --progress-bar -o "$POPULAR_SKILLS_ZIP" "$POPULAR_SKILLS_URL"; then
            echo "Error: Failed to download $POPULAR_SKILLS_URL"
            exit 1
        fi
    elif command -v wget >/dev/null 2>&1; then
        if ! wget --show-progress -q -O "$POPULAR_SKILLS_ZIP" "$POPULAR_SKILLS_URL"; then
            echo "Error: Failed to download $POPULAR_SKILLS_URL"
            exit 1
        fi
    else
        echo "Error: Neither curl nor wget is available. Please install one of them."
        exit 1
    fi

    rm -rf "$SKILLS_ROOT"
    mkdir -p "$SKILLS_ROOT"
    if command -v unzip >/dev/null 2>&1; then
        if ! unzip -o "$POPULAR_SKILLS_ZIP" -d "$SKILLS_ROOT" >/dev/null; then
            echo "Error: Failed to unzip $POPULAR_SKILLS_ZIP"
            exit 1
        fi
    else
        echo "Error: unzip is required but was not found."
        exit 1
    fi
fi

# if read_yes_no "Would you like to inject LinkMind into OpenClaw?"; then
#     export_val="true"
# fi
#
# if read_yes_no "Would you like to import OpenClaw configurations into LinkMind?"; then
#     import_val="true"
# fi

echo "Running installer..."
# "--export-to-openclaw=$export_val" \
# "--import-from-openclaw=$import_val" \
java -cp "$JAR_PATH" ai.starter.InstallerUtil \
    "--runtime-choice=$runtime_choice" \
    "--skills-root=$SKILLS_ROOT" \
    "--inject-agent=$inject_agent" \
    "--deer-flow-path=$deer_flow_path" || {
    rc=$?
    echo "Error: Installer exited with code $rc"
    exit $rc
}

# 6. Success message
echo ""
echo "LinkMind installed successfully!"
echo ""

# 7. Optionally start LinkMind
if read_yes_no "Would you like to start LinkMind now?"; then
    cd "$LINKMIND_DIR"
    java -jar "$JAR_NAME" --enable-sync=false
else
    echo "You can start LinkMind later by running:"
    echo "  cd $LINKMIND_DIR && java -jar $JAR_NAME --enable-sync=false"
fi
