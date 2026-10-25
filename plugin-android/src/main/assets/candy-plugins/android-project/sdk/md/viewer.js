// CandyIDE md 文档阅读器：纯 JS，无外部依赖；入口 main() 由模板容器调用
'use strict';
// 渲染模式标记：容器检测到后全屏显示 WebView（而不是黑底控制台）
window.CANDY_VIEWER = true;
var DOCS = __DOCS_JSON__;
var INDEX = __INDEX_JSON__;

function esc(s){
  return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
}

// 极简 Markdown → HTML（标题/段落/列表/代码/引用/链接/图片/粗斜体/分隔线）
function md(src){
  var lines = String(src).split('\n');
  var out = [];
  var i = 0;
  var inUl = false, inOl = false, inCode = false, inQuote = false;
  function closeLists(){ if(inUl){out.push('</ul>');inUl=false;} if(inOl){out.push('</ol>');inOl=false;} }
  function closeQuote(){ if(inQuote){out.push('</blockquote>');inQuote=false;} }
  while(i < lines.length){
    var ln = lines[i];
    var t = ln.trim();
    if(inCode){
      if(t.indexOf('```') === 0){ out.push('</code></pre>'); inCode = false; }
      else out.push(esc(ln));
      i++; continue;
    }
    if(t.indexOf('```') === 0){ closeLists(); closeQuote(); inCode = true; out.push('<pre><code>'); i++; continue; }
    if(t === ''){ closeLists(); closeQuote(); i++; continue; }
    var h = /^(#{1,6})\s+(.*)$/.exec(t);
    if(h){ closeLists(); closeQuote(); var n = h[1].length; out.push('<h'+n+'>'+inline(h[2])+'</h'+n+'>'); i++; continue; }
    if(/^(-{3,}|\*{3,}|_{3,})$/.test(t)){ closeLists(); closeQuote(); out.push('<hr>'); i++; continue; }
    var li = /^[-*+]\s+(.*)$/.exec(t);
    if(li){ closeQuote(); if(!inUl){ if(inOl){out.push('</ol>');inOl=false;} out.push('<ul>'); inUl = true; } out.push('<li>'+inline(li[1])+'</li>'); i++; continue; }
    var oi = /^\d+\.\s+(.*)$/.exec(t);
    if(oi){ closeQuote(); if(!inOl){ if(inUl){out.push('</ul>');inUl=false;} out.push('<ol>'); inOl = true; } out.push('<li>'+inline(oi[1])+'</li>'); i++; continue; }
    if(t.charAt(0) === '>'){ closeLists(); if(!inQuote){ out.push('<blockquote>'); inQuote = true; } out.push('<p>'+inline(t.slice(1).trim())+'</p>'); i++; continue; }
    closeLists(); closeQuote();
    out.push('<p>'+inline(t)+'</p>');
    i++;
  }
  if(inCode) out.push('</code></pre>');
  closeLists(); closeQuote();
  return out.join('\n');
}

function inline(s){
  s = esc(s);
  s = s.replace(/`([^`]+)`/g, '<code>$1</code>');
  s = s.replace(/!\[([^\]]*)\]\(([^)]+)\)/g, '<img alt="$1" src="$2">');
  s = s.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2">$1</a>');
  s = s.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
  s = s.replace(/__(.+?)__/g, '<strong>$1</strong>');
  s = s.replace(/\*(.+?)\*/g, '<em>$1</em>');
  return s;
}

function main(){
  document.body.innerHTML =
    '<div style="font-family:sans-serif;color:#222;background:#fff;min-height:100%">' +
    '<div id="back" style="display:none;position:sticky;top:0;background:#7c4dff;color:#fff;padding:10px 14px;font-size:15px" onclick="home()">‹ 返回文档列表</div>' +
    '<div id="list" style="max-width:720px;margin:0 auto;padding:8px"></div>' +
    '<div id="body" style="max-width:720px;margin:0 auto;padding:8px 16px 80px;line-height:1.7;font-size:15px"></div></div>';
  var box = document.getElementById('list');
  INDEX.forEach(function(n){
    var a = document.createElement('a');
    a.href = '#';
    a.textContent = n;
    a.style.cssText = 'display:block;padding:12px 14px;color:#7c4dff;text-decoration:none;border-bottom:1px solid #eee;font-size:16px';
    a.onclick = function(ev){ ev.preventDefault(); show(n); };
    box.appendChild(a);
  });
  // 容器约定：main() 返回 thenable（成功时 CandyBridge.done()）
  return { then: function(ok, bad){ try { ok && ok(); } catch(e){ bad && bad(e); } } };
}

// 单页阅读：进入正文时收起文档列表，返回时清空正文——两块互斥，不叠显
function show(name){
  document.getElementById('list').style.display = 'none';
  document.getElementById('body').innerHTML = md(DOCS[name] || ('（无此文档：' + name + ')'));
  document.getElementById('back').style.display = 'block';
  window.scrollTo(0, 0);
}

function home(){
  document.getElementById('body').innerHTML = '';
  document.getElementById('back').style.display = 'none';
  document.getElementById('list').style.display = 'block';
}