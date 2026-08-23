#!/usr/bin/env bash
set -euo pipefail

KOF="${KOF:-kof}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

"$ROOT/scripts/combine.sh"
"$KOF" run "$ROOT/build/editor.kf"

if [[ "${1:-}" != "--no-pause" ]]; then
    echo ""
    read -r -p "Pressione ENTER para fechar o editor..."
fi