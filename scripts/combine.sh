#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build"
OUTFILE="$OUT/editor.kf"

mkdir -p "$OUT"
: > "$OUTFILE"

for f in token lexer json-lexer theme highlight language buffer demo main; do
    {
        echo "// ===== src/core/$f.kf ====="
        cat "$ROOT/src/core/$f.kf"
        echo ""
    } >> "$OUTFILE"
done

echo "gerado: $OUTFILE ($(wc -l < "$OUTFILE") linhas)"