#!/bin/sh
set -eu
MC="${1:?usage: ./build-target.sh <minecraft-version>}"
ROOT="$(cd "$(dirname "$0")" && pwd)"
KWC_ROOT="$(cd "$ROOT/.." && pwd)"
export GRADLE_USER_HOME="${KWC_GRADLE_USER_HOME:-${GRADLE_USER_HOME:-$KWC_ROOT/.build-cache/gradle}}"
export TMPDIR="${KWC_BUILD_TEMP:-$KWC_ROOT/.build-cache/tmp}/forge/$MC"
mkdir -p "$GRADLE_USER_HOME" "$TMPDIR"
GV=9.3.1
JV=21
case "$MC" in
  1.18.2|1.19.2|1.19.4) GV=7.6.4; JV=17 ;;
  1.20.1|1.20.2|1.20.4) GV=8.8; JV=17 ;;
  1.20.6) GV=8.8; JV=21 ;;
  1.21.1|1.21.3|1.21.4|1.21.5|1.21.8|1.21.10|1.21.11) GV=9.3.1; JV=21 ;;
  26.1.2|26.2) GV=9.3.1; JV=25 ;;
  *) echo "ERROR: Unknown Forge target: $MC" >&2; exit 2 ;;
esac
JAVA_HOME="$(./select-java.sh "$JV")"
export JAVA_HOME
PATH="$JAVA_HOME/bin:$PATH"; export PATH
printf '\n[KWC Forge] Minecraft %s\n[KWC Forge] Gradle   %s\n[KWC Forge] JDK      %s (%s)\n' "$MC" "$GV" "$JV" "$JAVA_HOME"
printf '[KWC Forge] Gradle user home: %s\n[KWC Forge] Temp: %s\n' "$GRADLE_USER_HOME" "$TMPDIR"
"$JAVA_HOME/bin/java" -version
KWC_GRADLE_VERSION="$GV" ./gradlew -p "targets/$MC" clean build
