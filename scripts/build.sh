#!/usr/bin/env bash
set -euo pipefail

KOF="${KOF:-kof}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

"$ROOT/scripts/combine.sh"
"$KOF" build "$ROOT/build/editor.kf" --target jvm --output "$ROOT/build/out"
echo "build ok: build/out"