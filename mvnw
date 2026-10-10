#!/bin/sh
# ----------------------------------------------------------------------------
# Maven Wrapper Shell Script for MoveInSync Settlement Engine
# ----------------------------------------------------------------------------

MAVEN_VERSION="3.9.9"
MAVEN_HOME="$HOME/.m2/wrapper/dists/apache-maven-$MAVEN_VERSION"
MAVEN_BIN="$MAVEN_HOME/bin/mvn"

if [ ! -f "$MAVEN_BIN" ]; then
    echo "[INFO] Downloading Apache Maven $MAVEN_VERSION..."
    mkdir -p "$(dirname "$MAVEN_HOME")"
    ZIP_FILE="$(dirname "$MAVEN_HOME")/maven.zip"
    curl -sS -L -o "$ZIP_FILE" "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$MAVEN_VERSION/apache-maven-$MAVEN_VERSION-bin.zip"
    unzip -q -o "$ZIP_FILE" -d "$(dirname "$MAVEN_HOME")"
    rm -f "$ZIP_FILE"
fi

exec "$MAVEN_BIN" "$@"
