#!/bin/sh
# KWC 파일 안내 / KWC file guide
# Linux/Unix 환경에서 loader build 또는 smoke/adapter 검증을 자동화하는 스크립트다.
# Automates loader builds or smoke/adapter validation in Linux/Unix environments.
# set -e/pipefail, 임시 디렉터리 정리, 정확한 target 선택을 유지해 실패를 성공으로 숨기지 않도록 한다.
# Preserve set -e/pipefail, temporary-directory cleanup, and exact target selection so failures cannot be hidden as success.

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
  26.1.2|26.2|26.3) GV=9.3.1; JV=25 ;;
  *) echo "ERROR: Unknown Forge target: $MC" >&2; exit 2 ;;
esac
# 준비 타깃은 명시적으로 활성화하기 전까지 수동 빌드도 차단한다.
# Disabled preparation targets are blocked even from manual builds until explicitly activated.
PREP_ENABLED="$(sed -n 's/^kwc.prep.enabled=//p' "$ROOT/targets/$MC/gradle.properties" 2>/dev/null || true)"
if [ "$PREP_ENABLED" = "false" ]; then
  echo "ERROR: Forge $MC is a disabled PREP target and is not part of the release matrix." >&2
  echo "       Finalize exact pins and set kwc.prep.enabled=true before a manual compatibility build." >&2
  exit 3
fi
JAVA_HOME="$(./select-java.sh "$JV")"
export JAVA_HOME
PATH="$JAVA_HOME/bin:$PATH"; export PATH
printf '\n[KWC Forge] Minecraft %s\n[KWC Forge] Gradle   %s\n[KWC Forge] JDK      %s (%s)\n' "$MC" "$GV" "$JV" "$JAVA_HOME"
printf '[KWC Forge] Gradle user home: %s\n[KWC Forge] Temp: %s\n' "$GRADLE_USER_HOME" "$TMPDIR"
"$JAVA_HOME/bin/java" -version
KWC_GRADLE_VERSION="$GV" ./gradlew -p "targets/$MC" clean build
