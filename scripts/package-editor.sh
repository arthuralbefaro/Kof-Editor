#!/usr/bin/env bash
#
# package-editor.sh — empacota o Kof Editor como executável por SO.
#
# O pacote contém kof-editor.jar (38MB: runtime Kof + editor embutido) e
# install.sh/bat que cria o launcher, janela nativa e desktop entry.
# Se o sistema não tiver Java 21+, o instalador baixa a distro com JDK.
#
# Produz em dist/:
#   kof-editor-<version>-<os>-<arch>.tar.gz  (linux/macos)
#   kof-editor-<version>-<os>-<arch>.zip     (windows)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="0.1.0-beta"
OUT="${OUTPUT_DIR:-$ROOT/dist}"

OS="$(uname -s | tr '[:upper:]' '[:lower:]')"
case "$OS" in
    linux*) OS=linux; EXT=tar.gz ;;
    darwin*) OS=macos; EXT=tar.gz ;;
    mingw*|msys*|cygwin*) OS=windows; EXT=zip ;;
esac
ARCH="$(uname -m)"
case "$ARCH" in arm64|aarch64) ARCH=arm64 ;; *) ARCH=x86_64 ;; esac

TARGET="${OS}-${ARCH}"
BASE="kof-editor-$VERSION-$TARGET"
STAGE="$(mktemp -d)/$BASE"
trap 'rm -rf "$(dirname "$STAGE")"' EXIT
mkdir -p "$OUT" "$STAGE"

echo "== package-editor: executável ($TARGET) =="

# 1) kof-editor.jar — executável real: runtime Kof (kof.jar) + editor embutido
"$ROOT/scripts/web-combine.sh" >/dev/null
JAR="$STAGE/kof-editor.jar"
KOF_JAR="${KOF_JAR:-}"
if [ -z "$KOF_JAR" ]; then
    for c in "$HOME/.local/share/kof-editor/lib/kof.jar" \
             "$HOME/.local/share/kof-dev/lib/kof.jar" \
             "/home/mel/Documentos/Kof4j/kof-cli/target/kof-cli-0.2.5-beta.jar"; do
        if [ -f "$c" ]; then KOF_JAR="$c"; break; fi
    done
fi
if [ -z "${KOF_JAR:-}" ] || [ ! -f "$KOF_JAR" ]; then
    # CI: baixa o jar da distro
    case "$OS" in
        macos) JURL="$KOF_URL_MACOS" ;;
        *)     JURL="$KOF_URL_LINUX" ;;
    esac
    TMPJ="$(mktemp -d)"
    trap 'rm -rf "$TMPJ" "$(dirname "$STAGE")"' EXIT
    echo "  baixando kof.jar da distro..."
    curl -sL "$JURL" -o "$TMPJ/kof.tar.gz"
    tar xzf "$TMPJ/kof.tar.gz" -C "$TMPJ" lib/kof.jar
    KOF_JAR=$(find "$TMPJ" -name kof.jar | head -1)
fi
cp "$KOF_JAR" "$JAR"
mkdir -p "$STAGE/editor"
cp "$ROOT/build/editor-web.kf" "$STAGE/editor/src.kf"
cp "$ROOT/kof.png" "$STAGE/editor/kof.png" 2>/dev/null || true
(cd "$STAGE" && zip -q "$JAR" editor/src.kf editor/kof.png)
echo "  kof-editor.jar: $(du -h "$JAR" | cut -f1)"

# 2) instalador unix (linux/macos): cria launcher/janela/desktop; baixa JDK se faltar
cat > "$STAGE/install.sh" <<'INSTALL'
#!/usr/bin/env bash
# Kof Editor — instalador (linux/macos)
# Uso: ./install.sh
set -euo pipefail

DEST="${KOF_EDITOR_HOME:-$HOME/.local/share/kof-editor}"
BIN_DIR="$HOME/.local/bin"
HERE="$(cd "$(dirname "$0")" && pwd)"
JDK_URL_LINUX="https://github.com/KofLang/Kof4j/releases/download/kof-0.2.6-beta-linux-x86_64/kof-0.2.5-beta-linux-x86_64.tar.gz"
JDK_URL_MACOS="https://github.com/KofLang/Kof4j/releases/download/kof-0.2.6-beta-macos-arm64/kof-0.2.5-beta-macos-arm64.tar.gz"

echo "== Kof Editor: instalando em $DEST =="

mkdir -p "$DEST/lib" "$DEST/bin" "$DEST/editor"
cp "$HERE/kof-editor.jar" "$DEST/lib/kof-editor.jar"

# java 21+ disponível?
JAROK=0
if command -v java >/dev/null 2>&1; then
    V=$(java -version 2>&1 | head -1 | grep -oE '[0-9]+' | head -1)
    [ "${V:-0}" -ge 21 ] && JAROK=1
fi

# sem java: baixa a distro (com JDK embutido) e usa o java dela
if [ "$JAROK" = "0" ]; then
    echo "  Java 21+ não encontrado — baixando runtime com JDK (~230MB, 1ª vez)..."
    case "$(uname -s)" in
        Darwin) KOF_URL="${KOF_DISTRO_URL:-__KOF_URL_MACOS__}" ;;
        *)      KOF_URL="${KOF_DISTRO_URL:-__KOF_URL_LINUX__}" ;;
    esac
    TMP="$(mktemp -d)"
    trap 'rm -rf "$TMP"' EXIT
    curl -L "$KOF_URL" -o "$TMP/kof.tar.gz"
    tar xzf "$TMP/kof.tar.gz" -C "$TMP"
    KOFDIR=$(find "$TMP" -maxdepth 1 -type d ! -name "$(basename "$TMP")" | tail -1)
    cp -r "$KOFDIR/jdk" "$DEST/jdk"
    JAVA_BIN="$DEST/jdk/bin/java"
    [ -f "$JAVA_BIN" ] || JAVA_BIN="$DEST/jdk/bin/java"
else
    JAVA_BIN="$(command -v java)"
fi

# webview (janela nativa) da distro, se baixada; senão sem janela nativa
[ -f "$HERE/kof-webview" ] && cp "$HERE/kof-webview" "$DEST/bin/" 2>/dev/null || true

# launcher
mkdir -p "$BIN_DIR"
cat > "$BIN_DIR/kof-editor" <<LAUNCH
#!/usr/bin/env bash
# Kof Editor — jar executável + janela nativa
DEST="$DEST"
PORT="\${KOF_EDITOR_PORT:-8765}"
PID_DIR="$HOME/.config/kof-editor"
JAVA_BIN="$JAVA_BIN"
mkdir -p "$PID_DIR" "$DEST/editor"

if [ "\${1:-}" = "--stop" ]; then
    for f in server.pid webview.pid; do
        [ -f "$PID_DIR/\$f" ] && kill "\$(cat "$PID_DIR/\$f")" 2>/dev/null; rm -f "$PID_DIR/\$f"
    done
    echo "kof-editor parado"; exit 0
fi

# extrai o src.kf do jar (fonte do editor embutido no executável)
if [ ! -f "$DEST/editor/src.kf" ]; then
    cd "$DEST/editor" && "$JAVA_BIN" -jar "$DEST/lib/kof-editor.jar" --extract 2>/dev/null || unzip -o -q "$DEST/lib/kof-editor.jar" editor/src.kf
fi

if [ -f "$PID_DIR/server.pid" ] && kill -0 "\$(cat "$PID_DIR/server.pid")" 2>/dev/null; then
    echo "servidor já rodando"
else
    nohup "\$JAVA_BIN" -jar "$DEST/lib/kof-editor.jar" serve "$DEST/editor/src.kf" --port "\$PORT" >/dev/null 2>&1 &
    echo \$! > "$PID_DIR/server.pid"
    sleep 3
fi

URL="http://localhost:\$PORT"
if [ -x "$DEST/bin/kof-webview" ]; then
    nohup "$DEST/bin/kof-webview" "\$URL" "Kof Editor" "$DEST/editor/kof.png" >/dev/null 2>&1 &
    echo \$! > "$PID_DIR/webview.pid"
    echo "Kof Editor aberto (janela nativa)"
else
    echo "Kof Editor: \$URL"
    (xdg-open "\$URL" 2>/dev/null || open "\$URL" 2>/dev/null) &
fi
LAUNCH
chmod +x "$BIN_DIR/kof-editor"

# desktop entry (linux)
if command -v xdg-mime >/dev/null 2>&1; then
    DESK_DIR="$HOME/.local/share/applications"
    mkdir -p "$DESK_DIR"
    cat > "$DESK_DIR/kof-editor.desktop" <<DESKTOP
[Desktop Entry]
Name=Kof Editor
Comment=Editor de codigo escrito inteiramente em Kof
Exec=$BIN_DIR/kof-editor %F
Icon=$DEST/editor/kof.png
Terminal=false
Type=Application
Categories=Development;IDE;
MimeType=text/plain;text/x-kof;application/json;text/markdown;
DESKTOP
fi

echo "== pronto =="
echo "  rode:     kof-editor          (janela nativa, se WebKitGTK disponível)"
echo "  navegador http://localhost:8765"
echo "  parar:    kof-editor --stop"
INSTALL

# preencher URLs por SO (awk: portável entre GNU/BSD)
case "$OS" in
    macos) DEF_KOF_URL="$KOF_URL_MACOS" ;;
    *)     DEF_KOF_URL="$KOF_URL_LINUX" ;;
esac
KOF_URL_FINAL="${KOF_DISTRO_URL:-$DEF_KOF_URL}"
awk -v url="$KOF_URL_FINAL" '{ gsub(/__KOF_URL_MACOS__/, url); gsub(/__KOF_URL_LINUX__/, url); print }' "$STAGE/install.sh" > "$STAGE/install.sh.tmp" && mv "$STAGE/install.sh.tmp" "$STAGE/install.sh"
chmod +x "$STAGE/install.sh"

# 3) instalador windows
cat > "$STAGE/install.bat" <<'INSTALL'
@echo off
REM Kof Editor — instalador (windows)
setlocal
set HERE=%~dp0
set DEST=%LOCALAPPDATA%\kof-editor

echo == Kof Editor: instalando em %DEST% ==

where java >nul 2>&1
if errorlevel 1 (
    echo   Java 21+ nao encontrado — baixando runtime com JDK...
    powershell -Command "Invoke-WebRequest -Uri '__KOF_URL_WINDOWS__' -OutFile '%TEMP%\kof.zip'"
    powershell -Command "Expand-Archive -Force '%TEMP%\kof.zip' '%TEMP%\kof-distro'"
    for /d %%D in ("%TEMP%\kof-distro\*") do (
        if not exist "%DEST%\jdk" robocopy "%%D\jdk" "%DEST%\jdk" /E >nul
        if exist "%%D\bin\kof-webview.exe" copy /Y "%%D\bin\kof-webview.exe" "%DEST%\bin\" >nul
    )
    set JAVA_BIN=%DEST%\jdk\bin\java.exe
) else (
    set JAVA_BIN=java
)

mkdir "%DEST%\lib" 2>nul
mkdir "%DEST%\editor" 2>nul
copy /Y "%HERE%kof-editor.jar" "%DEST%\lib\kof-editor.jar" >nul
copy /Y "%HERE%editor\src.kf" "%DEST%\editor\src.kf" >nul 2>&1
copy /Y "%HERE%editor\kof.png" "%DEST%\editor\" >nul 2>&1

set LAUNCHER=%USERPROFILE%\kof-editor.bat
(
    echo @echo off
    echo set PORT=%%KOF_EDITOR_PORT%%
    if "%%PORT%%"=="" set PORT=8765
    echo cd /d "%DEST%\editor"
    echo start "" "%JAVA_BIN%" -jar "%DEST%\lib\kof-editor.jar" serve "%DEST%\editor\src.kf" --port %%PORT%%
    echo timeout /t 3 /nobreak ^>nul
    echo start http://localhost:%%PORT%%
) > "%LAUNCHER%"

echo == pronto ==
echo   launcher: %LAUNCHER%
echo   rode kof-editor e o navegador abre em http://localhost:8765
INSTALL
awk -v u="$KOF_URL_WINDOWS" '{ gsub(/__KOF_URL_WINDOWS__/, u); print }' "$STAGE/install.bat" > "$STAGE/install.bat.tmp" && mv "$STAGE/install.bat.tmp" "$STAGE/install.bat"

# 4) arquivar
cd "$(dirname "$STAGE")"
if [ "$EXT" = "zip" ]; then
    python3 -m zipfile -c "$OUT/$BASE.zip" "$BASE"
    echo "  -> $OUT/$BASE.zip"
else
    tar -czf "$OUT/$BASE.tar.gz" "$BASE"
    echo "  -> $OUT/$BASE.tar.gz"
fi

cd "$OUT"
if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$BASE.$EXT" > "$OUT/SHA256SUMS" 2>/dev/null || true
fi
echo "== pronto: $OUT =="
ls -la "$OUT"
