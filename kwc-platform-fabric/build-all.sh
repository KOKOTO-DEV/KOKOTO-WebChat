#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
for mc in 1.18.2 1.19.2 1.19.4 1.20.1 1.20.2 1.20.4 1.20.6 1.21.1 1.21.3 1.21.4 1.21.5 1.21.8 1.21.10 1.21.11 26.1.2 26.2; do "$ROOT/build-target.sh" "$mc"; done
echo "[KWC Fabric] ALL 16 TARGETS BUILT SUCCESSFULLY."
