#!/usr/bin/env bash
set -euo pipefail

KOF="${KOF:-kof}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PORT="${1:-8080}"

"$ROOT/scripts/web-combine.sh"
echo "abre http://localhost:$PORT no navegador"
exec "$KOF" serve "$ROOT/build/editor-web.kf" --port "$PORT"