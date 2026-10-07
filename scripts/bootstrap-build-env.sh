#!/bin/sh
# Restores the Gradle toolchain pointer after an environment reset (the
# container's system JDK is JRE-only and ~/.gradle is wiped). The JDK itself
# lives in the persistent tools/ directory, so this is instant.
#
# Usage: sh scripts/bootstrap-build-env.sh
set -e
JDK_DIR="$(cd "$(dirname "$0")/.." && pwd)/tools/jdk21"
if [ ! -x "$JDK_DIR/bin/javac" ]; then
    echo "JDK missing at $JDK_DIR - run:" >&2
    echo "  curl -sL 'https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse' -o /tmp/jdk.tgz" >&2
    echo "  mkdir -p $JDK_DIR && tar xzf /tmp/jdk.tgz -C $JDK_DIR --strip-components=1" >&2
    exit 1
fi
mkdir -p "$HOME/.gradle"
cat > "$HOME/.gradle/gradle.properties" <<EOF
org.gradle.java.installations.paths=$JDK_DIR
org.gradle.java.installations.auto-detect=false
EOF
echo "Gradle toolchain pointer restored ($JDK_DIR)"
