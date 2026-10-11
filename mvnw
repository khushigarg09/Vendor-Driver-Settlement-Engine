#!/bin/sh
# ----------------------------------------------------------------------------
# Maven Wrapper Shell Script for MoveInSync Settlement Engine
# ----------------------------------------------------------------------------

set -e

# If native maven is available on PATH, use it directly
if command -v mvn >/dev/null 2>&1; then
    exec mvn "$@"
fi

MAVEN_VERSION="3.9.9"
MAVEN_HOME="$HOME/.m2/wrapper/dists/apache-maven-$MAVEN_VERSION"
MAVEN_BIN="$MAVEN_HOME/bin/mvn"

if [ ! -f "$MAVEN_BIN" ]; then
    echo "[INFO] Downloading Apache Maven $MAVEN_VERSION..."
    mkdir -p "$(dirname "$MAVEN_HOME")"
    ZIP_FILE="$(dirname "$MAVEN_HOME")/maven.zip"
    if command -v curl >/dev/null 2>&1; then
        curl -sS -L -o "$ZIP_FILE" "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$MAVEN_VERSION/apache-maven-$MAVEN_VERSION-bin.zip"
    elif command -v wget >/dev/null 2>&1; then
        wget -qO "$ZIP_FILE" "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$MAVEN_VERSION/apache-maven-$MAVEN_VERSION-bin.zip"
    fi
    unzip -q -o "$ZIP_FILE" -d "$(dirname "$MAVEN_HOME")"
    rm -f "$ZIP_FILE"
    chmod +x "$MAVEN_BIN" || true
fi

exec "$MAVEN_BIN" "$@"
