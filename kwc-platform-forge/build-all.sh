#!/bin/sh
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
