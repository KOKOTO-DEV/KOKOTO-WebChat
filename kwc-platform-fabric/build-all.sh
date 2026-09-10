#!/usr/bin/env bash
# KWC 파일 안내 / KWC file guide
# Linux/Unix 환경에서 loader build 또는 smoke/adapter 검증을 자동화하는 스크립트다.
# Automates loader builds or smoke/adapter validation in Linux/Unix environments.
# set -e/pipefail, 임시 디렉터리 정리, 정확한 target 선택을 유지해 실패를 성공으로 숨기지 않도록 한다.
# Preserve set -e/pipefail, temporary-directory cleanup, and exact target selection so failures cannot be hidden as success.

set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
for mc in 1.18.2 1.19.2 1.19.4 1.20.1 1.20.2 1.20.4 1.20.6 1.21.1 1.21.3 1.21.4 1.21.5 1.21.8 1.21.10 1.21.11 26.1.2 26.2; do "$ROOT/build-target.sh" "$mc"; done
echo "[KWC Fabric] ALL 16 TARGETS BUILT SUCCESSFULLY."
