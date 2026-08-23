#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

if [[ ! -f "$ROOT/build/ui-host/KofDomHost.class" ]]; then
    "$ROOT/scripts/gui-build.sh"
fi

cd "$ROOT"
exec java -cp "build/ui-out:build/ui-host:$HOME/.local/share/kof-dev/lib/flatlaf.jar" KofDomHost "${1:-}"