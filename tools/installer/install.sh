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
MEDUSA_MODEL_URL="https://${DOWNLOADS_HOST}/installer/medusa.model"
#DOWNLOAD_URL="http://localhost:8000/LinkMind.jar"
#MEDUSA_MODEL_URL="http://localhost:8000/medusa.model"
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
SKILLS_EXTRACT_DIR=""
cleanup() {
    rm -f "$TEMP_FILE"
    if [ -n "${SKILLS_EXTRACT_DIR:-}" ] && [ -d "$SKILLS_EXTRACT_DIR" ]; then
        rm -rf "$SKILLS_EXTRACT_DIR"
    fi
}
trap cleanup EXIT

echo "Downloading $DOWNLOAD_URL ..."

if command -v curl >/dev/null 2>&1; then
    if ! curl -kfL --progress-bar -o "$TEMP_FILE" "$DOWNLOAD_URL"; then
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
    default_answer="${2:-no}"
    if [ "$default_answer" = "yes" ]; then
        default_label="yes"
    else
        default_label="no"
        default_answer="no"
    fi
    printf "%s (yes/no) [%s]: " "$prompt" "$default_label"
    read -r answer < /dev/tty
    answer=$(echo "$answer" | tr '[:upper:]' '[:lower:]' | xargs)
    if [ -z "$answer" ]; then
        [ "$default_answer" = "yes" ]
        return $?
    elif [ "$answer" = "yes" ] || [ "$answer" = "y" ]; then
        return 0
    else
        return 1
    fi
}

update_jar_entry_from_file() {
    jar_file="$1"
    entry_name="$2"
    source_file="$3"

    if [ ! -f "$jar_file" ]; then
        echo "Error: Jar file does not exist: $jar_file"
        return 1
    fi
    if [ ! -f "$source_file" ]; then
        echo "Error: Source file does not exist: $source_file"
        return 1
    fi

    jar_dir=$(dirname "$jar_file")
    staged_jar="$(mktemp "$jar_dir/.LinkMindUpdated_XXXXXXXXXX")"
    rm -f "$staged_jar"

    if command -v zip >/dev/null 2>&1 && command -v unzip >/dev/null 2>&1; then
        update_dir="$(mktemp -d "${TMPDIR:-/tmp}/LinkMindJar_XXXXXXXXXX")"
        entry_dir=$(dirname "$entry_name")
        if ! cp -f "$jar_file" "$staged_jar"; then
            rm -f "$staged_jar"
            rm -rf "$update_dir"
            echo "Error: Failed to stage $jar_file"
            return 1
        fi
        if [ "$entry_dir" != "." ]; then
            mkdir -p "$update_dir/$entry_dir"
        fi
        if ! cp -f "$source_file" "$update_dir/$entry_name"; then
            rm -f "$staged_jar"
            rm -rf "$update_dir"
            echo "Error: Failed to stage $source_file"
            return 1
        fi
        if ! (
            cd "$update_dir"
            zip -q "$staged_jar" "$entry_name"
        ); then
            rm -f "$staged_jar"
            rm -rf "$update_dir"
            echo "Error: Failed to update $jar_file with $entry_name"
            return 1
        fi
        rm -rf "$update_dir"

        expected_size=$(wc -c < "$source_file" | tr -d '[:space:]')
        actual_size=$(unzip -l "$staged_jar" "$entry_name" 2>/dev/null | awk -v name="$entry_name" '$NF == name { print $1 }')
        if [ -z "$actual_size" ] || [ "$actual_size" != "$expected_size" ]; then
            rm -f "$staged_jar"
            echo "Error: Updated $entry_name size mismatch in $jar_file"
            return 1
        fi
    else
        python_bin=""
        if command -v python3 >/dev/null 2>&1; then
            python_bin="python3"
        elif command -v python >/dev/null 2>&1 && python -c 'import sys; sys.exit(0 if sys.version_info[0] >= 3 else 1)' >/dev/null 2>&1; then
            python_bin="python"
        fi

        if [ -z "$python_bin" ]; then
            rm -f "$staged_jar"
            echo "Error: python3 or zip+unzip is required to install Medusa Accelerator."
            return 1
        fi

        if ! "$python_bin" - "$jar_file" "$staged_jar" "$entry_name" "$source_file" <<'PY'
import os
import sys
import zipfile

jar_file, staged_jar, entry_name, source_file = sys.argv[1:]
expected_size = os.path.getsize(source_file)

with zipfile.ZipFile(jar_file, "r") as source_zip:
    with zipfile.ZipFile(staged_jar, "w", allowZip64=True) as target_zip:
        target_zip.comment = source_zip.comment
        for item in source_zip.infolist():
            if item.filename == entry_name:
                continue
            target_zip.writestr(item, source_zip.read(item))
        target_zip.write(source_file, entry_name, compress_type=zipfile.ZIP_DEFLATED)

with zipfile.ZipFile(staged_jar, "r") as check_zip:
    info = check_zip.getinfo(entry_name)
    if info.file_size != expected_size:
        raise RuntimeError(
            "updated %s size mismatch: expected %s bytes but got %s bytes"
            % (entry_name, expected_size, info.file_size)
        )
PY
        then
            rm -f "$staged_jar"
            echo "Error: Failed to update $jar_file with $entry_name"
            return 1
        fi
    fi

    if ! mv -f "$staged_jar" "$jar_file"; then
        rm -f "$staged_jar"
        echo "Error: Failed to replace $jar_file after updating $entry_name"
        return 1
    fi
    return 0
}

# export_val="false"
# import_val="false"
runtime_choice="mate"
install_medusa="false"
install_skills="false"
inject_agent=0
deer_flow_path=""
openhuman_path=""

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

read_openhuman_path() {
    while true; do
        printf "Please enter OpenHuman config directory or config.toml path [auto-detect]: "
        read -r answer < /dev/tty
        answer=$(printf "%s" "$answer" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
        if [ -z "$answer" ]; then
            openhuman_path=""
            return 0
        fi
        if [ -d "$answer" ] || [ -f "$answer" ]; then
            openhuman_path="$answer"
            return 0
        fi
        echo "Path does not exist: $answer"
    done
}

read_inject_agent_choice() {
    while true; do
        echo "Inject Agent Framework:"
        echo "  1) openclaw"
        echo "  2) deer-flow"
        echo "  3) hermes"
        echo "  4) openhuman"
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
        elif [ "$answer" = "4" ] || [ "$answer" = "openhuman" ]; then
            inject_agent=$((1 << 3))
            return 0
        fi
        echo "Invalid choice. Please enter 1, 2, 3 or 4."
    done
}

if [ "$runtime_choice" = "mate" ]; then
    read_inject_agent_choice
    if [ "$inject_agent" -eq $((1 << 1)) ]; then
        read_deer_flow_path
    fi
    if [ "$inject_agent" -eq $((1 << 3)) ]; then
        read_openhuman_path
        if [ -z "${LINKMIND_API_KEY:-}" ]; then
            echo "Tip: set LINKMIND_API_KEY before running this installer so OpenHuman can authenticate to LinkMind."
        fi
    fi
fi

if [ "$runtime_choice" = "server" ]; then
    if read_yes_no "Would you like to install popular skills? This downloads the popular skills package" "yes"; then
        install_skills="true"
    fi

    if read_yes_no "Would you like to install Medusa Accelerator? This downloads a large model file (100+ MB)"; then
        install_medusa="true"
    fi

    if [ "$install_skills" = "true" ]; then
        POPULAR_SKILLS_ZIP="$LINKMIND_DIR/popular_skills.zip"
        SKILLS_ROOT="$LINKMIND_DIR/skills/popular_skills"
        SKILLS_EXTRACT_DIR="$(mktemp -d "${TMPDIR:-/tmp}/LinkMindSkills_XXXXXXXXXX")"
        echo "Downloading $POPULAR_SKILLS_URL ..."
        if command -v curl >/dev/null 2>&1; then
            if ! curl -kfL --progress-bar -o "$POPULAR_SKILLS_ZIP" "$POPULAR_SKILLS_URL"; then
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

        if ! command -v unzip >/dev/null 2>&1; then
            echo "Error: unzip is required but was not found."
            exit 1
        fi
        if ! unzip -o "$POPULAR_SKILLS_ZIP" -d "$SKILLS_EXTRACT_DIR" >/dev/null; then
            echo "Error: Failed to unzip $POPULAR_SKILLS_ZIP"
            exit 1
        fi

        EXPECTED_SKILLS_ROOT="$LINKMIND_DIR/skills/popular_skills"
        if [ -z "$SKILLS_ROOT" ] || [ "$SKILLS_ROOT" != "$EXPECTED_SKILLS_ROOT" ]; then
            echo "Error: Refusing to replace unexpected skills directory: $SKILLS_ROOT"
            exit 1
        fi
        rm -rf "$SKILLS_ROOT"
        mkdir -p "$SKILLS_ROOT"
        SKILLS_SOURCE_ROOT="$SKILLS_EXTRACT_DIR"
        if [ -d "$SKILLS_EXTRACT_DIR/popular_skills" ]; then
            SKILLS_SOURCE_ROOT="$SKILLS_EXTRACT_DIR/popular_skills"
        fi
        cp -R "$SKILLS_SOURCE_ROOT/." "$SKILLS_ROOT/"
        rm -rf "$SKILLS_EXTRACT_DIR"
        SKILLS_EXTRACT_DIR=""
        echo "Popular skills installed into: $SKILLS_ROOT"
    else
        echo "Skipping popular skills installation."
    fi

    if [ "$install_medusa" = "true" ]; then
        MEDUSA_MODEL_PATH="$LINKMIND_DIR/medusa.model"
        echo "Downloading $MEDUSA_MODEL_URL ..."
        if command -v curl >/dev/null 2>&1; then
            if ! curl -kfL --progress-bar -o "$MEDUSA_MODEL_PATH" "$MEDUSA_MODEL_URL"; then
                echo "Error: Failed to download $MEDUSA_MODEL_URL"
                exit 1
            fi
        elif command -v wget >/dev/null 2>&1; then
            if ! wget --show-progress -q -O "$MEDUSA_MODEL_PATH" "$MEDUSA_MODEL_URL"; then
                echo "Error: Failed to download $MEDUSA_MODEL_URL"
                exit 1
            fi
        else
            echo "Error: Neither curl nor wget is available. Please install one of them."
            exit 1
        fi

        if ! update_jar_entry_from_file "$JAR_PATH" "medusa.model" "$MEDUSA_MODEL_PATH"; then
            exit 1
        fi
        echo "Medusa Accelerator model installed into: $JAR_PATH"
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
    "--install-medusa=$install_medusa" \
    "--inject-agent=$inject_agent" \
    "--deer-flow-path=$deer_flow_path" \
    "--openhuman-path=$openhuman_path" || {
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
