#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"; MC="${1:-}"
KWC_ROOT="$(cd "$ROOT/.." && pwd)"
export GRADLE_USER_HOME="${KWC_GRADLE_USER_HOME:-${GRADLE_USER_HOME:-$KWC_ROOT/.build-cache/gradle}}"
export TMPDIR="${KWC_BUILD_TEMP:-$KWC_ROOT/.build-cache/tmp}/fabric/${MC:-unknown}"
mkdir -p "$GRADLE_USER_HOME" "$TMPDIR"
[[ -n "$MC" && -f "$ROOT/targets/$MC/build.gradle" ]] || { echo "Usage: build-target.sh <minecraft-version>" >&2; exit 2; }
TARGET_JV="$(sed -n 's/^kwc.java=//p' "$ROOT/targets/$MC/gradle.properties")"; BUILD_JV="$(sed -n 's/^kwc.build-java=//p' "$ROOT/targets/$MC/gradle.properties")"; GV="$(sed -n 's/^kwc.gradle=//p' "$ROOT/targets/$MC/gradle.properties")"
[[ -n "$BUILD_JV" ]] || BUILD_JV="$TARGET_JV"
JAVA_HOME="$($ROOT/select-java.sh "$BUILD_JV")"; export JAVA_HOME PATH="$JAVA_HOME/bin:$PATH" KWC_GRADLE_VERSION="$GV"
echo "[KWC Fabric] Minecraft $MC / Gradle $GV / build JDK $BUILD_JV / target Java $TARGET_JV"
echo "[KWC Fabric] Gradle user home: $GRADLE_USER_HOME"
echo "[KWC Fabric] Temp: $TMPDIR"
"$ROOT/gradlew" -p "$ROOT/targets/$MC" clean build
