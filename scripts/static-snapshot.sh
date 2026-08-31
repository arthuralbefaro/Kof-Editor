#!/usr/bin/env bash
# snapshot estático do editor web para GitHub Pages:
# - roda kof serve local, captura /, /editor.css, /editor.js
# - embute filetree (fonte do próprio editor) + 6 temas num bundle.json
# - injeta shim fetch() no editor.js: /tokenize (JS), /api/theme (bundled),
#   /api/dir|read|listall (filetree embutida), restantes viram stub
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DIST="$ROOT/dist-pages"
PORT="${SNAPSHOT_PORT:-8765}"
KOF="${KOF:-kof}"

mkdir -p "$DIST"
if curl -sf "http://localhost:$PORT/" >/dev/null 2>&1; then
    SRV=""
else
    $KOF serve "$ROOT/build/editor-web.kf" --port "$PORT" >/dev/null 2>&1 &
    SRV=$!
    for i in $(seq 1 90); do
        curl -sf "http://localhost:$PORT/" >/dev/null 2>&1 && break
        sleep 1
    done
fi
trap '[ -n "$SRV" ] && kill $SRV 2>/dev/null || true' EXIT

curl -sf "http://localhost:$PORT/"          -o "$DIST/index.html"
curl -sf "http://localhost:$PORT/editor.css" -o "$DIST/editor.css"
curl -sf "http://localhost:$PORT/editor.js"  -o "$DIST/editor.js"

# Pages serve em subpath: caminhos absolutos → relativos
sed -i 's|href="/editor.css"|href="editor.css"|; s|src="/editor.js"|src="editor.js"|' "$DIST/index.html"

# ── bundle: filetree (fonte do editor) + 6 temas ──
python3 - "$ROOT" "$PORT" "$DIST" <<'PYJSON'
import json, os, sys, urllib.request
root, port, dist = sys.argv[1], sys.argv[2], sys.argv[3]
files = []
for dp, dn, fn in os.walk(os.path.join(root, 'src')):
    for f in fn:
        if f.endswith('.kf'):
            files.append(os.path.relpath(os.path.join(dp, f), root))
for extra in ('README.md', 'CHANGELOG.md'):
    if os.path.exists(os.path.join(root, extra)):
        files.append(extra)
contents = {}
for p in files:
    contents[p] = open(os.path.join(root, p), encoding='utf-8').read()
themes = {}
for i in range(6):
    req = urllib.request.Request(f"http://localhost:{port}/api/theme", data=str(i).encode(), method='POST')
    themes[str(i)] = urllib.request.urlopen(req, timeout=30).read().decode()
bundle = {"files": files, "contents": contents, "themes": themes}
open(f"{dist}/bundle.json", "w").write(json.dumps(bundle, ensure_ascii=False))
print("bundle:", len(files), "arquivos,", sum(len(v) for v in contents.values()), "bytes de conteúdo")
PYJSON

# ── shim fetch no editor.js ──
python3 - "$DIST" <<'PYPATCH'
import sys
dist = sys.argv[1]
js = open(f"{dist}/editor.js").read()
bundle = open(f"{dist}/bundle.json").read()

shim = r"""(function(){
  var BUNDLE = __BUNDLE__;
  var realFetch = window.fetch ? window.fetch.bind(window) : null;
  var TREE = {};
  (function build() {
    var files = BUNDLE.files;
    for (var i = 0; i < files.length; i++) {
      var parts = files[i].split('/');
      var cur = TREE;
      for (var d = 0; d < parts.length - 1; d++) {
        var name = d === 0 ? parts[0] : parts.slice(0, d + 1).join('/');
        if (!cur[parts[d]]) cur[parts[d]] = { __dir: true, __path: parts.slice(0, d + 1).join('/') };
        cur = cur[parts[d]];
      }
      cur[parts[parts.length - 1]] = { __dir: false, __path: files[i] };
    }
  })();
  function okText(body) { return Promise.resolve(new Response(body, {headers: {'Content-Type':'text/plain'}})); }
  function okJson(body) { return Promise.resolve(new Response(body, {headers: {'Content-Type':'application/json'}})); }
  // tokenizer JS (espelha o Lexer Kof)
  var KW = 'var|func|class|if|else|while|for|in|return|new|this|super|extends|implements|null|true|false|enum|interface|break|continue|try|catch|throw|throws|constructor|static|final|package|import';
  var TYPES = 'Int|String|Bool|Float|Double|List|Map|Set|Byte|Char|Void|Any|Long';
  function tokenize(src) {
    var toks = [], i = 0, n = src.length;
    while (i < n) {
      var c = src[i];
      if (c === '#' || (c === '/' && src[i+1] === '/')) {
        var j = src.indexOf('\n', i); if (j < 0) j = n;
        toks.push({kind:'comment', text:src.slice(i,j), start:i, end:j}); i = j; continue;
      }
      if (c === '"' || c === "'") {
        var j = i + 1;
        while (j < n && src[j] !== c) { if (src[j] === '\\') j++; j++; }
        j = Math.min(j + 1, n);
        toks.push({kind:'string', text:src.slice(i,j), start:i, end:j}); i = j; continue;
      }
      if (/[0-9]/.test(c)) {
        var j = i;
        while (j < n && /[0-9a-fA-FxX._]/.test(src[j])) j++;
        toks.push({kind:'number', text:src.slice(i,j), start:i, end:j}); i = j; continue;
      }
      if (/[A-Za-z_$]/.test(c)) {
        var j = i;
        while (j < n && /[A-Za-z0-9_$]/.test(src[j])) j++;
        var w = src.slice(i, j), kind = 'identifier';
        if (new RegExp('^(' + KW + ')$').test(w)) kind = 'keyword';
        else if (new RegExp('^(' + TYPES + ')$').test(w)) kind = 'type';
        toks.push({kind:kind, text:w, start:i, end:j}); i = j; continue;
      }
      if (/[{}()[\].,;:]/.test(c)) { toks.push({kind:'punctuation', text:c, start:i, end:i+1}); i++; continue; }
      if (/[+\-*/%=<>!&|^~?]/.test(c)) { toks.push({kind:'operator', text:c, start:i, end:i+1}); i++; continue; }
      i++;
    }
    return toks;
  }
  function dirOf(path) {
    var out = [], seen = {};
    var prefix = path === '' || path === '/' ? '' : (path.replace(/\/+$/,'') + '/');
    var names = {};
    for (var i = 0; i < BUNDLE.files.length; i++) {
      var f = BUNDLE.files[i];
      if (f.indexOf(prefix) !== 0) continue;
      var rest = f.slice(prefix.length).replace(/^\/+/, '');
      var top = rest.split('/')[0];
      if (!top || seen[top]) continue;
      seen[top] = 1;
      var isDir = rest.indexOf('/') >= 0;
      out.push({name: top, dir: isDir});
    }
    return out;
  }
  function simpleJsonToYaml(o) {
    var L = ['# tema do Kof Editor (chave: "rgb r;g;b")', 'name: "' + (o.name||'Tema') + '"'];
    var blocks = { 'interface:': [], 'editor:': [], 'tokens:': [] };
    Object.keys(o).forEach(function(k){
      if (k === 'name') return;
      var b = k.indexOf('if.') === 0 ? 'interface:' : (k.indexOf('ed.') === 0 ? 'editor:' : 'tokens:');
      blocks[b].push('  ' + k.replace(/^(if|ed|tk)\./, '') + ': "' + o[k] + '"');
    });
    Object.keys(blocks).forEach(function(b){ L.push(b); L = L.concat(blocks[b]); });
    return L.join('\n');
  }
  window.fetch = function(url, opts) {
    // páginas estáticas não têm backend: qualquer request que não seja o
    // próprio bundle é resolvida aqui (nada chega ao servidor do Pages)
    if (url === '/tokenize') return okJson(JSON.stringify(tokenize(String(opts && opts.body || ''))));
    if (url === '/api/theme') {
      var t = BUNDLE.themes[String(opts && opts.body || '0')] || BUNDLE.themes['0'];
      return okJson(t);
    }
    if (url === '/api/dir') {
      var dir = String(opts && opts.body || '');
      return okJson(JSON.stringify(dirOf(dir)));
    }
    if (url === '/api/listall') return okJson(JSON.stringify(BUNDLE.files));
    if (url === '/api/read') {
      var p = String(opts && opts.body || '');
      var c = BUNDLE.contents[p];
      return okText(c === undefined ? 'not found: ' + p : c);
    }
    if (url === '/api/git') return okText('pages (read-only)');
    if (url === '/api/gitstatus') return okText('modo demonstração: sem git');
    if (url === '/api/check') return okText('{"ok":true,"output":"modo demonstração: sem compilador"}');
    if (url === '/api/diags' || url === '/api/outline') return okJson('[]');
    if (url === '/api/gitcmd' || url === '/api/gitcommit') return okText('modo demonstração: sem git');
    if (url === '/api/occurrences') return okJson('[]');
    if (url === '/api/outline') return okJson('[]');
    if (url === '/api/search') return okJson('[]');
    if (url === '/api/folderpick') return okText('src');
    if (url === '/api/gitcmd') return okText('modo demonstração: sem git');
    if (url === '/api/write' || url === '/api/exec' || url === '/api/run' || url === '/api/spawn' || url === '/api/occurrences')
      return okText(url === '/api/write' ? '{"ok":false}' : '');
    if (url === '/api/gitdiff') return okText('modo demonstração: sem git');
    if (url === '/api/gitcommit') return okText('{"ok":false,"output":"modo demonstração: sem git"}');
    if (url === '/api/theme-export') {
      var th = BUNDLE.themes[String(opts && opts.body || '0')] || BUNDLE.themes['0'];
      return okText(simpleJsonToYaml(JSON.parse(th)));
    }
    if (url === '/api/theme-import') return okJson(BUNDLE.themes['0']);
    if (url === '/api/occurrences' || url === '/api/outline') return okJson('[]');
    if (url === '/api/run' || url === '/api/exec' || url === '/api/write') return okText('modo demonstração: desabilitado');
    return realFetch ? realFetch(url, opts) : Promise.reject(new Error('offline: ' + url));
  };
})();"""

shim = shim.replace('__BUNDLE__', bundle)
open(f"{dist}/editor.js", "w").write(shim + "\n" + js)
print("editor.js com shim:", len(shim), "bytes de shim")
PYPATCH

echo "snapshot: dist-pages/ pronto"
