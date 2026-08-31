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

  // ── webfs: backend no navegador via File System Access API ──
  // userDir != null → a demo opera na pasta REAL escolhida pelo usuário
  // (mesmo comportamento do kof serve local). null → modo demo embutida.
  var userDir = null;      // FileSystemDirectoryHandle raiz
  var userDirName = '';

  function okText(body) { return Promise.resolve(new Response(body, {headers: {'Content-Type':'text/plain'}})); }
  function okJson(body) { return Promise.resolve(new Response(body, {headers: {'Content-Type':'application/json'}})); }

  function resolveHandle(path) {
    // retorna {dir, name} do pai + nome final, ou Promise rejeitada
    var parts = path.split('/').filter(function(p){ return p && p !== '.'; });
    if (!userDir) return Promise.reject(new Error('no user dir'));
    var dir = Promise.resolve(userDir);
    for (var i = 0; i < parts.length - 1; i++) {
      (function(name){
        dir = dir.then(function(d){ return d.getDirectoryHandle(name); });
      })(parts[i]);
    }
    return dir.then(function(d){ return {dir: d, name: parts[parts.length-1]}; });
  }

  function readHandleFile(path) {
    return resolveHandle(path).then(function(r){
      return r.dir.getFileHandle(r.name).then(function(f){ return f.getFile(); });
    }).then(function(file){ return file.text(); });
  }

  function listHandleDir(path) {
    var parts = path.split('/').filter(function(p){ return p && p !== '.'; });
    var dirP = Promise.resolve(userDir);
    for (var i = 0; i < parts.length; i++) {
      (function(name){ dirP = dirP.then(function(d){ return d.getDirectoryHandle(name); }); })(parts[i]);
    }
    return dirP.then(function(d){
      var out = [];
      // for await não suportado em ES5 do shim — usar interface assíncrona manual
      var iter = d.entries();
      function step() {
        return iter.next().then(function(res){
          if (res.done) return out;
          var entry = res.value;
          out.push({name: entry[0], dir: entry[1].kind === 'directory'});
          return step();
        });
      }
      return step();
    });
  }

  function writeHandleFile(path, content) {
    return resolveHandle(path).then(function(r){
      return r.dir.getFileHandle(r.name, {create: true}).then(function(f){
        return f.createWritable().then(function(w){
          w.write(content);
          return w.close();
        });
      });
    }).then(function(){ return 'ok'; });
  }

  function walkHandle(dir, prefix, allFiles) {
    var iter = dir.entries();
    function step() {
      return iter.next().then(function(res){
        if (res.done) return allFiles;
        var name = res.value[0], h = res.value[1];
        var path = prefix ? prefix + '/' + name : name;
        if (h.kind === 'directory') return walkHandle(h, path, allFiles);
        allFiles.push(path);
        return step();
      });
    }
    return step();
  }

  async function pickRealFolder() {
    if (!window.showDirectoryPicker) return null;
    try {
      var h = await window.showDirectoryPicker({mode: 'readwrite'});
      userDir = h;
      userDirName = h.name;
      return h.name;
    } catch (e) { return null; }
  }

  // ── tokenizer JS (espelha o Lexer Kof) ──
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

  // ── fallback demo: filetree embutida (quando o usuário não abriu pasta real) ──
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
  function dirOf(path) {
    var out = [], seen = {};
    var prefix = path === '' || path === '/' ? '' : (path.replace(/\/+$/,'') + '/');
    for (var i = 0; i < BUNDLE.files.length; i++) {
      var f = BUNDLE.files[i];
      if (f.indexOf(prefix) !== 0) continue;
      var rest = f.slice(prefix.length).replace(/^\/+/, '');
      var top = rest.split('/')[0];
      if (!top || seen[top]) continue;
      seen[top] = 1;
      out.push({name: top, dir: rest.indexOf('/') >= 0});
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

  // ── roteamento: pasta real (FS Access API) com fallback demo ──
  window.fetch = function(url, opts) {
    var body = opts && opts.body || '';
    if (url === '/tokenize') return okJson(JSON.stringify(tokenize(String(body))));
    if (url === '/api/folderpick') {
      return pickRealFolder().then(function(name){
        if (name) return okText(name);
        return okText('');
      });
    }
    if (userDir) {
      // ── pasta REAL do usuário ──
      if (url === '/api/dir') return listHandleDir(String(body)).then(function(l){ return okJson(JSON.stringify(l)); })
        .catch(function(){ return okJson('[]'); });
      if (url === '/api/read') return readHandleFile(String(body)).then(function(t){ return okText(t); })
        .catch(function(){ return okText('not found: ' + body); });
      if (url === '/api/listall') return walkHandle(userDir, '', []).then(function(l){ return okJson(JSON.stringify(l)); });
      if (url === '/api/write') return writeHandleFile(String(body).split('\n')[0], String(body).split('\n').slice(1).join('\n'))
        .then(function(r){ return okText(r); }).catch(function(){ return okText('erro'); });
      if (url === '/api/search') {
        var sp = String(body).split('\n'), term = (sp[1]||'').toLowerCase();
        return walkHandle(userDir, '', []).then(function(files){
          var out = [], chain = Promise.resolve();
          files.slice(0, 200).forEach(function(f){
            chain = chain.then(function(){
              return readHandleFile(f).then(function(txt){
                var lines = txt.split('\n'), matches = [];
                for (var li = 0; li < lines.length; li++) {
                  if (lines[li].toLowerCase().indexOf(term) >= 0) matches.push({line: li+1, col: lines[li].indexOf(term)+1});
                }
                if (matches.length) out.push({file: f, matches: matches});
              }).catch(function(){});
            });
          });
          return chain.then(function(){ return okJson(JSON.stringify(out)); });
        });
      }
      if (url === '/api/check') {
        // sem compilador no navegador — valida sintaxe básica com o tokenizer
        var cp = String(body).split('\n'), csrc = cp.slice(1).join('\n');
        var toks = tokenize(csrc), errs = toks.filter(function(t){ return t.kind === 'error'; }).length;
        return okText('{"ok":' + (errs === 0) + ',"output":"' + (errs === 0 ? 'sem erros (tokenizer JS)' : errs + ' token(s) inválido(s)') + '"}');
      }
      if (url === '/api/git' || url === '/api/gitstatus' || url === '/api/gitcmd' || url === '/api/gitdiff' || url === '/api/gitcommit')
        return okText('{"stdout":"git não disponível no navegador","stderr":"","exitCode":1}');
      if (url === '/api/exec' || url === '/api/run' || url === '/api/spawn')
        return okText('{"stdout":"terminal não disponível no navegador","stderr":"","exitCode":1}');
      if (url === '/api/diags' || url === '/api/outline' || url === '/api/occurrences') return okJson('[]');
      if (url === '/api/theme') {
        var t = BUNDLE.themes[String(body || '0')] || BUNDLE.themes['0'];
        return okJson(t);
      }
      return okText('');
    }
    // ── modo demo embutida (sem pasta real) ──
    if (url === '/api/theme') {
      var t2 = BUNDLE.themes[String(body || '0')] || BUNDLE.themes['0'];
      return okJson(t2);
    }
    if (url === '/api/dir') return okJson(JSON.stringify(dirOf(String(body))));
    if (url === '/api/listall') return okJson(JSON.stringify(BUNDLE.files));
    if (url === '/api/read') {
      var p = String(body);
      var c = BUNDLE.contents[p];
      return okText(c === undefined ? 'not found: ' + p : c);
    }
    if (url === '/api/search') return okJson('[]');
    if (url === '/api/check') return okText('{"ok":true,"output":"modo demonstração: sem compilador"}');
    if (url === '/api/diags' || url === '/api/outline' || url === '/api/occurrences') return okJson('[]');
    if (url === '/api/git' || url === '/api/gitstatus' || url === '/api/gitcmd' || url === '/api/gitdiff' || url === '/api/gitcommit')
      return okText('{"stdout":"modo demonstração: sem git","stderr":"","exitCode":1}');
    if (url === '/api/exec' || url === '/api/run' || url === '/api/spawn')
      return okText('{"stdout":"modo demonstração: terminal desabilitado","stderr":"","exitCode":1}');
    if (url === '/api/write') return okText('{"ok":false}');
    if (url === '/api/theme-export') {
      var th = BUNDLE.themes[String(body || '0')] || BUNDLE.themes['0'];
      return okText(simpleJsonToYaml(JSON.parse(th)));
    }
    if (url === '/api/theme-import') return okJson(BUNDLE.themes['0']);
    return okText('');
  };
})();"""

shim = shim.replace('__BUNDLE__', bundle)
open(f"{dist}/editor.js", "w").write(shim + "\n" + js)
print("editor.js com shim:", len(shim), "bytes de shim")
PYPATCH

echo "snapshot: dist-pages/ pronto"
