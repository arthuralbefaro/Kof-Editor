#!/usr/bin/env bash
#
# release.sh — empacota o Kof Editor para Linux.
#
#   release.sh            → tarballs em dist/ (jvm + native)
#   release.sh --jvm      → só o pacote JVM
#   release.sh --native   → só o pacote nativo (bloqueado: PROC001 — o Kof
#                           ainda não suporta process.run no target native;
#                           o pacote é gerado apenas com o webview + launcher)
#
# Requisitos: kof (jars instalados), gcc + libwebkit2gtk (para o webview com
# ícone de janela; sem gcc o webview instalado é reutilizado).
set -euo pipefail

KOF="${KOF:-kof}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DIST="$ROOT/dist"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT

echo "== web-combine =="
"$ROOT/scripts/web-combine.sh"
"$KOF" check "$ROOT/build/editor-web.kf" >/dev/null

mkdir -p "$STAGE/app"
cp "$ROOT/build/editor-web.kf" "$STAGE/app/src.kf"
cp "$ROOT/kof.png" "$STAGE/app/kof.png"

do_jvm() {
    echo "== pacote JVM =="
    mkdir -p "$STAGE/app/lib"
    cp "$HOME/.local/share/kof-dev/lib/"*.jar "$STAGE/app/lib/"
    cat > "$STAGE/app/kof-editor" <<'LAUNCH'
#!/usr/bin/env bash
KOF_DIR="$(cd "$(dirname "$0")" && pwd)"
exec java -Xss8m -cp "$KOF_DIR/lib/kof-cli-0.0.5-alpha.jar:$KOF_DIR/lib/kof-compiler-0.0.5-alpha.jar:$KOF_DIR/lib/kof-runtime-0.0.5-alpha.jar:$KOF_DIR/lib/asm.jar:$KOF_DIR/lib/flatlaf.jar" dev.kof.cli.Main serve "$KOF_DIR/src.kf" --port "${KOF_EDITOR_PORT:-8765}"
LAUNCH
    chmod +x "$STAGE/app/kof-editor"
    tar -czf "$DIST/kof-editor-linux-jvm.tar.gz" -C "$STAGE" app
    echo "  -> dist/kof-editor-linux-jvm.tar.gz"
}

do_native() {
    echo "== pacote native =="
    if timeout 240 "$KOF" build "$STAGE/app" --target native --output "$STAGE/native" >/dev/null 2>&1; then
        cp -r "$STAGE/native" "$STAGE/app/native-bin"
        tar -czf "$DIST/kof-editor-linux-native.tar.gz" -C "$STAGE" app
        echo "  -> dist/kof-editor-linux-native.tar.gz"
    else
        echo "  aviso: target native bloqueado (PROC001: process.run não suportado no native) —"
        echo "  pacote gerado sem o binário nativo. O JVM é o release atual."
        tar -czf "$DIST/kof-editor-linux-native.tar.gz" -C "$STAGE" app
    fi
}

mkdir -p "$DIST"
case "${1:-all}" in
    --jvm)    do_jvm ;;
    --native) do_native ;;
    *)        do_jvm; do_native ;;
esac

echo "== webview (com ícone) =="
if command -v gcc >/dev/null 2>&1 && pkg-config --exists webkit2gtk-4.1 2>/dev/null; then
    for W in "$ROOT/../Kof4j/scripts/build-webview.sh" "$ROOT/../../kof4j/scripts/build-webview.sh"; do
        if [ -f "$W" ]; then bash "$W" >/dev/null 2>&1 || true; fi
    done
fi
echo "== pronto: dist/ =="
ls -la "$DIST"