#!/usr/bin/env bash
# KWC 파일 안내 / KWC file guide
# Linux/Unix 환경에서 loader build 또는 smoke/adapter 검증을 자동화하는 스크립트다.
# Automates loader builds or smoke/adapter validation in Linux/Unix environments.
# set -e/pipefail, 임시 디렉터리 정리, 정확한 target 선택을 유지해 실패를 성공으로 숨기지 않도록 한다.
# Preserve set -e/pipefail, temporary-directory cleanup, and exact target selection so failures cannot be hidden as success.

set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"; MC="${1:-}"
KWC_ROOT="$(cd "$ROOT/.." && pwd)"
export GRADLE_USER_HOME="${KWC_GRADLE_USER_HOME:-${GRADLE_USER_HOME:-$KWC_ROOT/.build-cache/gradle}}"
export TMPDIR="${KWC_BUILD_TEMP:-$KWC_ROOT/.build-cache/tmp}/neoforge/${MC:-unknown}"
mkdir -p "$GRADLE_USER_HOME" "$TMPDIR"
[[ -n "$MC" && -f "$ROOT/targets/$MC/build.gradle" ]] || { echo "Usage: build-target.sh <minecraft-version>" >&2; exit 2; }
# 준비 타깃은 명시적으로 활성화하기 전까지 수동 빌드도 차단한다.
# Disabled preparation targets are blocked even from manual builds until explicitly activated.
PREP_ENABLED="$(sed -n 's/^kwc.prep.enabled=//p' "$ROOT/targets/$MC/gradle.properties" 2>/dev/null || true)"
if [[ "$PREP_ENABLED" == "false" ]]; then
  echo "ERROR: NeoForge $MC is a disabled PREP target and is not part of the release matrix." >&2
  echo "       Finalize exact pins and set kwc.prep.enabled=true before a manual compatibility build." >&2
  exit 3
fi
JV="$(sed -n 's/^kwc.java=//p' "$ROOT/targets/$MC/gradle.properties")"; GV="$(sed -n 's/^kwc.gradle=//p' "$ROOT/targets/$MC/gradle.properties")"
JAVA_HOME="$($ROOT/select-java.sh "$JV")"; export JAVA_HOME PATH="$JAVA_HOME/bin:$PATH" KWC_GRADLE_VERSION="$GV"
echo "[KWC NeoForge] Minecraft $MC / Gradle $GV / JDK $JV"
echo "[KWC NeoForge] Gradle user home: $GRADLE_USER_HOME"
echo "[KWC NeoForge] Temp: $TMPDIR"
"$ROOT/gradlew" -p "$ROOT/targets/$MC" clean build
