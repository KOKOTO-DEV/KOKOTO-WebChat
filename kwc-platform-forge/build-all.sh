#!/bin/sh
# KWC 파일 안내 / KWC file guide
# Linux/Unix 환경에서 loader build 또는 smoke/adapter 검증을 자동화하는 스크립트다.
# Automates loader builds or smoke/adapter validation in Linux/Unix environments.
# set -e/pipefail, 임시 디렉터리 정리, 정확한 target 선택을 유지해 실패를 성공으로 숨기지 않도록 한다.
# Preserve set -e/pipefail, temporary-directory cleanup, and exact target selection so failures cannot be hidden as success.

set -eu
printf '%s\n' '[KWC Forge] Preflight: locating JDK 17, 21 and 25...'
for v in 17 21 25; do
  home=$(./select-java.sh "$v")
  printf '[KWC Forge] JDK %s: %s\n' "$v" "$home"
done
printf '\n%s\n' '[KWC Forge] Building all exact targets...'
for mc in 1.18.2 1.19.2 1.19.4 1.20.1 1.20.2 1.20.4 1.20.6 1.21.1 1.21.3 1.21.4 1.21.5 1.21.8 1.21.10 1.21.11 26.1.2 26.2; do
  ./build-target.sh "$mc"
done
printf '\n%s\n' '[KWC Forge] ALL TARGETS BUILT SUCCESSFULLY.'
