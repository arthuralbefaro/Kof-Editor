#!/usr/bin/env bash
set -euo pipefail

KOF="${KOF:-kof}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

"$ROOT/scripts/ui-combine.sh"

echo "== compilando o front em Kof (núcleo + DOM declarativo) =="
"$KOF" build "$ROOT/build/editor-ui.kf" --target jvm --output "$ROOT/build/ui-out" 2>&1

echo "== compilando o host da janela nativa (interpreta o DOM Kof) =="
mkdir -p "$ROOT/build/ui-host"
javac -cp "$ROOT/build/ui-out:$HOME/.local/share/kof-dev/lib/flatlaf.jar" -d "$ROOT/build/ui-host" "$ROOT/src/ui/jvm/KofDomHost.java"

echo "build ok: scripts/gui-run.sh"