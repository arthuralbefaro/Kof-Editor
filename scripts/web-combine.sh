#!/usr/bin/env bash
set -euo pipefail

KOF="${KOF:-kof}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/editor-web.kf"

mkdir -p "$(dirname "$OUT")"
# kof run/serve <file> compila o DIRETÓRIO inteiro (PKG002/B21): remove os
# irmãos combinados para manter um único .kf em build/ por vez.
rm -f "$ROOT/build/editor.kf" "$ROOT/build/editor-ui.kf"
: > "$OUT"

for f in token lexer json-lexer language theme front web; do
    src="$ROOT/src/core/$f.kf"
    if [[ ! -f "$src" ]]; then
        if [[ -f "$ROOT/src/ui/$f.kf" ]]; then
            src="$ROOT/src/ui/$f.kf"
        else
            src="$ROOT/src/ui/web/$f.kf"
        fi
    fi
    {
        echo "// ===== $(basename "$src") ====="
        cat "$src"
        echo ""
    } >> "$OUT"
done

echo "gerado: $OUT ($(wc -l < "$OUT") linhas)"