#!/bin/sh
# KWC 파일 안내 / KWC file guide
# Linux/Unix 환경에서 loader build 또는 smoke/adapter 검증을 자동화하는 스크립트다.
# Automates loader builds or smoke/adapter validation in Linux/Unix environments.
# set -e/pipefail, 임시 디렉터리 정리, 정확한 target 선택을 유지해 실패를 성공으로 숨기지 않도록 한다.
# Preserve set -e/pipefail, temporary-directory cleanup, and exact target selection so failures cannot be hidden as success.

set -eu
major="${1:?usage: ./select-java.sh <17|21|25>}"
case "$major" in 17|21|25) ;; *) echo "Unsupported JDK major: $major" >&2; exit 2;; esac

detect_major() {
  home="$1"
  [ -x "$home/bin/java" ] && [ -x "$home/bin/javac" ] || return 1
  v=$("$home/bin/java" -XshowSettings:properties -version 2>&1 | sed -n 's/^[[:space:]]*java\.specification\.version[[:space:]]*=[[:space:]]*\([0-9][0-9]*\).*$/\1/p' | head -n1)
  [ "$v" = "$major" ]
}

for name in KWC_JAVA${major}_HOME JAVA${major}_HOME JDK${major}_HOME; do
  eval "value=\${$name-}"
  if [ -n "${value:-}" ]; then
    if detect_major "$value"; then printf '%s\n' "$value"; exit 0; fi
    echo "$name is set to '$value', but it is not a JDK $major installation." >&2
    exit 1
  fi
done

if [ -n "${JAVA_HOME:-}" ] && detect_major "$JAVA_HOME"; then printf '%s\n' "$JAVA_HOME"; exit 0; fi

for d in /usr/lib/jvm/* /opt/java/* "$HOME"/.jdks/*; do
  [ -d "$d" ] || continue
  if detect_major "$d"; then printf '%s\n' "$d"; exit 0; fi
done

echo "JDK $major was not found. Install it or set KWC_JAVA${major}_HOME." >&2
exit 1
