#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/editor-ui.kf"

mkdir -p "$(dirname "$OUT")"
: > "$OUT"

for f in token lexer json-lexer language theme front; do
    src="$ROOT/src/core/$f.kf"
    if [[ ! -f "$src" ]]; then
        src="$ROOT/src/ui/$f.kf"
    fi
    {
        echo "// ===== $(basename "$src") ====="
        cat "$src"
        echo ""
    } >> "$OUT"
done

echo "gerado: $OUT ($(wc -l < "$OUT") linhas)"