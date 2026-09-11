// Moid Client - HUD Editor + inline pickers, center fix, drag fix, no X/Y in card
const MOID_APP_VERSION='1.0.4-themeauto';
console.log('[MoidClient] app.js '+MOID_APP_VERSION);
const PRESETS = [
  { name: 'Electric Violet', hex: '#8B5CF6' },
  { name: 'Neon Mint', hex: '#10B981' },
  { name: 'Cyber Cyan', hex: '#06B6D4' },
  { name: 'Flame Crimson', hex: '#EF4444' },
  { name: 'Sunset Amber', hex: '#F59E0B' },
];
const ICONS = {
  fpsCounter:  `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M3 12h3l2-5 4 10 2-6h5"/></svg>`,
  ping:        `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="1.6" fill="currentColor" stroke="none"/><path d="M8.5 12a3.5 3.5 0 0 1 7 0" stroke-linecap="round"/><path d="M6 12a6 6 0 0 1 12 0" stroke-linecap="round" opacity="0.85"/><path d="M3.5 12a8.5 8.5 0 0 1 17 0" stroke-linecap="round" opacity="0.45"/></svg>`,
  cpsCounter:  `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="7" y="3" width="10" height="16" rx="3"/><path d="M12 7v4"/><circle cx="12" cy="12" r="1.5" fill="currentColor" stroke="none"/></svg>`,
  keystrokes:  `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="7" width="18" height="10" rx="1.5"/><path d="M8 11h.01M12 11h.01M16 11h.01M8 15h8"/></svg>`,
  fullbright:  `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><circle cx="12" cy="12" r="3.5"/><path d="M12 3v2M12 19v2M4.2 4.2l1.4 1.4M18.4 18.4l1.4 1.4M3 12h2M19 12h2M4.2 19.8l1.4-1.4M18.4 5.6l1.4-1.4"/></svg>`,
};
const MODULES_META = {
  ping:         { name: 'Ping Display', desc: 'Server latency in ms.', cat: 'hud' },
  fpsCounter:   { name: 'FPS Counter', desc: 'Shows current frames per second.', cat: 'hud' },
  cpsCounter:   { name: 'CPS Counter', desc: 'Clicks per second - left | right with burst fire.', cat: 'hud' },
  keystrokes:   { name: 'Keystrokes', desc: 'WASD + mouse overlay.', cat: 'hud' },
  fullbright:   { name: 'Fullbright', desc: 'Gamma boost for dark areas - no overlay.', cat: 'visuals' },
};
let ws=null, accent='#8B5CF6', config={accentColor:accent, modules:{}};
let focusedId=null, hasInitialRendered=false, isDraggingSlider=false;
let isDraggingHud=false;
let lastBgToggle=0;
let searchQuery="";
let windowSize={scaledWidth:640, scaledHeight:360, width:1920, height:1080, guiScale:3};
let editorSelectedId=null;
let livePing=null, liveFps=null, liveCpsLeft=null, liveCpsRight=null;
window.liveKeys={w:false,a:false,s:false,d:false,space:false,shift:false,lmb:false,rmb:false};
const $=s=>document.querySelector(s), $$=s=>document.querySelectorAll(s);
function hexToHsv(hex){
  hex=hex.replace('#',''); if(hex.length===3) hex=hex.split('').map(c=>c+c).join('');
  const r=parseInt(hex.substring(0,2),16)/255, g=parseInt(hex.substring(2,4),16)/255, b=parseInt(hex.substring(4,6),16)/255;
  const max=Math.max(r,g,b), min=Math.min(r,g,b), d=max-min;
  let h=0; if(d!==0){ if(max===r) h=((g-b)/d)%6; else if(max===g) h=(b-r)/d+2; else h=(r-g)/d+4; h*=60; if(h<0) h+=360; }
  const s=max===0?0:d/max; const v=max;
  return {h,s,v};
}
function hsvToHex(h,s,v){
  h=h%360; if(h<0) h+=360; const c=v*s, x=c*(1-Math.abs((h/60)%2-1)), m=v-c;
  let r=0,g=0,b=0; if(h<60){r=c;g=x;} else if(h<120){r=x;g=c;} else if(h<180){g=c;b=x;} else if(h<240){g=x;b=c;} else if(h<300){r=x;b=c;} else {r=c;b=x;}
  r=Math.round((r+m)*255); g=Math.round((g+m)*255); b=Math.round((b+m)*255);
  return '#'+[r,g,b].map(v=>v.toString(16).padStart(2,'0')).join('').toUpperCase();
}
let pickerHsv=hexToHsv(accent);
let themeText='#F9FAFB', textPickerHsv=hexToHsv('#F9FAFB');
function setThemeText(hex){
  if(!/^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$/.test(hex)) return;
  themeText=hex;
  document.documentElement.style.setProperty('--text-bright',hex);
  try{ localStorage.setItem('cc_text',hex); }catch(e){}
  const hsv=hexToHsv(hex);
  textPickerHsv=hsv;
  updateTextPickerUI(hex, hsv);
  const th=$('#textThemeHex'); if(th && document.activeElement!==th) th.value=hex;
  const tp=$('#textThemePreview'); if(tp){ tp.style.background=hex; tp.textContent=hex.toUpperCase(); tp.style.color=isLight(hex)?'#0f1115':'#F9FAFB'; }
}
function updateTextPickerUI(hex, hsv){
  if(!hsv) hsv=hexToHsv(hex); textPickerHsv=hsv;
  const sv=document.querySelector('#textThemeSv'); if(sv) sv.style.background=`hsl(${hsv.h} 100% 50%)`;
  const cur=document.querySelector('#textThemeSvCursor'); if(cur){ cur.style.left=(hsv.s*100)+'%'; cur.style.top=((1-hsv.v)*100)+'%'; }
  const hc=document.querySelector('#textThemeHueCursor'); if(hc) hc.style.top=(hsv.h/360*100)+'%';
}
function updatePickerUI(hex, hsv){
  if(!hsv) hsv=hexToHsv(hex); pickerHsv=hsv;
  const svs=['#themeSv','#onboardSv'], hues=['#themeHue','#onboardHue'], svCs=['#themeSvCursor','#onboardSvCursor'], hueCs=['#themeHueCursor','#onboardHueCursor'];
  svs.forEach(sel=>{
    const el=document.querySelector(sel); if(!el) return;
    el.style.background=`hsl(${hsv.h} 100% 50%)`;
  });
  svCs.forEach(sel=>{
    const cur=document.querySelector(sel); if(!cur) return;
    cur.style.left=(hsv.s*100)+'%';
    cur.style.top=((1-hsv.v)*100)+'%';
  });
  hueCs.forEach(sel=>{
    const cur=document.querySelector(sel); if(!cur) return;
    cur.style.top=(hsv.h/360*100)+'%';
  });
}
function setupPicker(svId,hueId,svCursorId,hueCursorId){
  const sv=document.querySelector(svId), hue=document.querySelector(hueId);
  if(!sv||!hue) return;
  let draggingSv=false, draggingHue=false;
  function setFromSv(e){
    const rect=sv.getBoundingClientRect(); const x=Math.max(0,Math.min(e.clientX-rect.left, rect.width)); const y=Math.max(0,Math.min(e.clientY-rect.top, rect.height));
    const s=x/rect.width; const v=1 - y/rect.height;
    pickerHsv.s=s; pickerHsv.v=v;
    const hex=hsvToHex(pickerHsv.h,pickerHsv.s,pickerHsv.v);
    setAccent(hex); send({type:'UPDATE_ACCENT_COLOR',color:hex});
    updatePickerUI(hex, pickerHsv);
  }
  function setFromHue(e){
    const rect=hue.getBoundingClientRect(); const y=Math.max(0,Math.min(e.clientY-rect.top, rect.height));
    const h=(y/rect.height)*360;
    pickerHsv.h=h;
    const hex=hsvToHex(pickerHsv.h,pickerHsv.s,pickerHsv.v);
    setAccent(hex); send({type:'UPDATE_ACCENT_COLOR',color:hex});
    updatePickerUI(hex, pickerHsv);
  }
  sv.addEventListener('pointerdown', e=>{ draggingSv=true; sv.setPointerCapture(e.pointerId); setFromSv(e); });
  sv.addEventListener('pointermove', e=>{ if(draggingSv) setFromSv(e); });
  sv.addEventListener('pointerup', ()=> draggingSv=false);
  hue.addEventListener('pointerdown', e=>{ draggingHue=true; hue.setPointerCapture(e.pointerId); setFromHue(e); });
  hue.addEventListener('pointermove', e=>{ if(draggingHue) setFromHue(e); });
  hue.addEventListener('pointerup', ()=> draggingHue=false);
}
function setupTextPicker(svId,hueId,svCursorId,hueCursorId){
  const sv=document.querySelector(svId), hue=document.querySelector(hueId);
  if(!sv||!hue) return;
  let draggingSv=false, draggingHue=false;
  function setFromSv(e){
    const rect=sv.getBoundingClientRect(); const x=Math.max(0,Math.min(e.clientX-rect.left, rect.width)); const y=Math.max(0,Math.min(e.clientY-rect.top, rect.height));
    const s=x/rect.width; const v=1 - y/rect.height;
    textPickerHsv.s=s; textPickerHsv.v=v;
    const hex=hsvToHex(textPickerHsv.h,textPickerHsv.s,textPickerHsv.v);
    setThemeText(hex); send({type:'UPDATE_THEME_TEXT_COLOR',color:hex});
    updateTextPickerUI(hex, textPickerHsv);
  }
  function setFromHue(e){
    const rect=hue.getBoundingClientRect(); const y=Math.max(0,Math.min(e.clientY-rect.top, rect.height));
    const h=(y/rect.height)*360;
    textPickerHsv.h=h;
    const hex=hsvToHex(textPickerHsv.h,textPickerHsv.s,textPickerHsv.v);
    setThemeText(hex); send({type:'UPDATE_THEME_TEXT_COLOR',color:hex});
    updateTextPickerUI(hex, textPickerHsv);
  }
  sv.addEventListener('pointerdown', e=>{ draggingSv=true; sv.setPointerCapture(e.pointerId); setFromSv(e); });
  sv.addEventListener('pointermove', e=>{ if(draggingSv) setFromSv(e); });
  sv.addEventListener('pointerup', ()=> draggingSv=false);
  hue.addEventListener('pointerdown', e=>{ draggingHue=true; hue.setPointerCapture(e.pointerId); setFromHue(e); });
  hue.addEventListener('pointermove', e=>{ if(draggingHue) setFromHue(e); });
  hue.addEventListener('pointerup', ()=> draggingHue=false);
}
function handleWindowSize(data){
  windowSize=data;
  const outer=document.querySelector('#hudPreviewOuter');
  if(outer){
    outer.style.aspectRatio = data.scaledWidth + ' / ' + data.scaledHeight;
  }
  const label=document.querySelector('#windowSizeLabel');
  if(label) label.textContent = data.width+'x'+data.height+' -> '+data.scaledWidth+'x'+data.scaledHeight+' @'+data.guiScale+'x';
  syncEditorItems();
}
function handleLiveStats(data){
  if(data.ping!=null) livePing=data.ping;
  if(data.fps!=null) liveFps=data.fps;
  if(data.cpsLeft!=null) liveCpsLeft=data.cpsLeft;
  if(data.cpsRight!=null) liveCpsRight=data.cpsRight;
  if(data.keys){
    window.liveKeys.w=!!data.keys.w; window.liveKeys.a=!!data.keys.a; window.liveKeys.s=!!data.keys.s; window.liveKeys.d=!!data.keys.d;
    window.liveKeys.space=!!data.keys.space; window.liveKeys.shift=!!data.keys.shift; window.liveKeys.lmb=!!data.keys.lmb; window.liveKeys.rmb=!!data.keys.rmb;
  }
  const el=document.querySelector('#liveStatsLabel');
  if(el) {
    let txt='Ping: '+(livePing??'-')+' ms - FPS: '+(liveFps??'-');
    if(liveCpsLeft!=null || liveCpsRight!=null) txt+=' - CPS: '+(liveCpsLeft??0)+'|'+(liveCpsRight??0);
    el.textContent=txt;
  }
  if(!isDraggingHud) syncEditorItems();
  else {
    document.querySelectorAll('.hud-preview-item').forEach(el=>{
      const id=el.dataset.id; if(!id) return;
      el.textContent=getEditorText(id);
    });
  }
}
function getEditorText(id){
  const mod=config.modules[id]||{};
  const meta=MODULES_META[id];
  if(id==='ping'){
    const fmt=mod.format||'Ping: {ping} ms';
    const v= livePing ?? 42;
    return fmt.replace('{ping}', String(v)).replace('{value}', String(v)).replace('{fps}', String(v));
  }
  if(id==='fpsCounter'){
    const fmt=mod.format||'FPS: {fps}';
    const v= liveFps ?? 144;
    return fmt.replace('{fps}', String(v)).replace('{value}', String(v)).replace('{ping}', String(v));
  }
  if(id==='cpsCounter'){
    const mode = mod.cpsMode || 'both';
    let fmt = mod.format || (mode==='left' ? 'CPS: {left}' : mode==='right' ? 'CPS: {right}' : 'CPS: {left} | {right}');
    if(fmt.includes('{ping}')) fmt = mode==='left' ? 'CPS: {left}' : mode==='right' ? 'CPS: {right}' : 'CPS: {left} | {right}';
    let displayFmt = fmt;
    if(mode==='left'){
      displayFmt = displayFmt.replace(/\s*\|\s*\{right\}/g, '').replace(/\{right\}\s*\|\s*/g, '').replace('{right}','').replace('{r}','');
    } else if(mode==='right'){
      displayFmt = displayFmt.replace(/\s*\|\s*\{left\}/g, '').replace(/\{left\}\s*\|\s*/g, '').replace('{left}','').replace('{l}','');
    }
    const left=8, right=12;
    let txt = displayFmt.replace('{left}',left).replace('{right}',right).replace('{cps}',Math.max(left,right)).replace('{l}',left).replace('{r}',right).replace('{value}',left).replace('{ping}',left);
    txt = txt.replace(/\s*\|\s*\|/g, ' | ').replace(/\s*\|\s*$/,'').trim();
    if((left>10||right>10) && (fmt==='CPS: {left} | {right}'||fmt==='CPS: {cps}')) txt+=' 🔥';
    return txt;
  }
  if(id==='keystrokes'){
    const showW = mod.keystrokesShowW!==false, showA = mod.keystrokesShowA!==false, showS = mod.keystrokesShowS!==false, showD = mod.keystrokesShowD!==false;
    const showL = mod.keystrokesShowMouse!==false, showR = mod.keystrokesShowMouse!==false;
    const showSpace = mod.keystrokesShowSpace!==false, showShift = mod.keystrokesShowShift!==false;
    let parts=[];
    if(showW) parts.push('W');
    if(showA) parts.push('A');
    if(showS) parts.push('S');
    if(showD) parts.push('D');
    let txt = parts.length ? parts.join(' ') : '-';
    if(showL || showR){
      const l = liveCpsLeft ?? 0, r = liveCpsRight ?? 0;
      if(mod.keystrokesShowCps){
        if(showL && showR) txt += `  L${l} R${r}`;
        else if(showL) txt += `  L${l}`;
        else if(showR) txt += `  R${r}`;
      } else {
        if(showL && showR) txt += '  L R';
        else if(showL) txt += '  L';
        else if(showR) txt += '  R';
      }
    }
    if(showSpace) txt += ' _';
    if(showShift) txt += ' ^';
    return txt;
  }
  return meta?meta.name:id;
}
function syncEditorItems(){
  try{
  const outer=document.querySelector('#hudPreviewOuter');
  if(!outer) return;
  outer.querySelectorAll('.hud-preview-item').forEach(e=>e.remove());
  const enabledIds=Object.keys(MODULES_META).filter(id=>{
    const m=config.modules[id]; const meta=MODULES_META[id]; return m && m.enabled && meta && meta.cat==='hud';
  });
  const hudKeys=Object.keys(MODULES_META).filter(k=>MODULES_META[k].cat==='hud');
  const idsToShow = enabledIds.length ? enabledIds : hudKeys.slice(0,2);
  const rect=outer.getBoundingClientRect();
  const sx= rect.width / windowSize.scaledWidth;
  const sy= rect.height / windowSize.scaledHeight;
  idsToShow.forEach(id=>{
    const mod=config.modules[id]||{x:10,y:10,scale:1,opacity:1, background:false, backgroundColor:'#1A1B20', textColor:null};
    const scale=mod.scale||1;
    const isKeystrokes = id==='keystrokes';
    let txt=getEditorText(id);
    if(isKeystrokes && mod.keystrokesShowCps){
      const l = liveCpsLeft ?? 8, r = liveCpsRight ?? 12;
      txt = `WASD\nL${l} R${r}`;
    }
    const el=document.createElement('div');
    el.className='hud-preview-item absolute select-none cursor-grab active:cursor-grabbing flex items-center justify-center text-xs font-medium whitespace-nowrap border';
    el.dataset.id=id;
    const bgEnabled = !!mod.background;
    if(isKeystrokes){
      const gap = Math.max(0, Math.min(10, mod.keystrokesGap ?? 2));
      const keySize=18;
      let estH = 0;
      if(mod.keystrokesShowW!==false) estH += keySize + gap;
      if(mod.keystrokesShowA!==false || mod.keystrokesShowS!==false || mod.keystrokesShowD!==false) estH += keySize + gap;
      if(mod.keystrokesShowMouse!==false) estH += keySize + gap;
      if(mod.keystrokesShowSpace!==false) estH += (keySize-4) + gap;
      if(mod.keystrokesShowShift!==false) estH += (keySize-6) + gap;
      if(estH===0) estH = keySize;
      const estW = 3*keySize + 2*gap;
      el.style.width = estW + 'px';
      el.style.height = estH + 'px';
      el.style.padding = '2px';
      el.style.display = 'flex';
      el.style.flexDirection = 'column';
      el.style.alignItems = 'center';
      el.style.justifyContent = 'center';
      el.style.gap = '2px';
      el.style.fontSize = '9px';
      el.style.lineHeight = '1';
      el.style.whiteSpace = 'pre';
      txt = mod.keystrokesShowCps ? `WASD\nL${liveCpsLeft??8} R${liveCpsRight??12}` : `WASD`;
      if(mod.keystrokesShowMouse===false) txt = 'WASD';
      if(bgEnabled){
        const bgCol = mod.backgroundColor || '#1A1B20';
        const bgOp = mod.backgroundOpacity ?? 0.85;
        let hex=bgCol.replace('#',''); if(hex.length===3) hex=hex.split('').map(c=>c+c).join('');
        const r=parseInt(hex.substr(0,2),16), g=parseInt(hex.substr(2,2),16), b=parseInt(hex.substr(4,2),16);
        el.style.background=`rgba(${r},${g},${b},${bgOp})`;
        el.style.borderColor = mod.keystrokesOutline===false ? 'transparent' : 'rgba(0,0,0,0.15)';
      } else {
        el.style.background='color-mix(in srgb, var(--accent) 18%, var(--card))'; el.style.borderColor='var(--accent)';
      }
      el.style.color = mod.textColor || 'var(--text-bright)';
      el.style.borderRadius='6px';
      if(window.liveKeys){
        const anyPressed = window.liveKeys.w||window.liveKeys.a||window.liveKeys.s||window.liveKeys.d||window.liveKeys.space||window.liveKeys.shift||window.liveKeys.lmb||window.liveKeys.rmb;
        if(anyPressed) el.style.background = mod.keystrokesPressedColor || 'var(--accent)';
      }
    } else if(bgEnabled){
      const bgCol = mod.backgroundColor || '#1A1B20';
      const bgOp = mod.backgroundOpacity ?? 0.85;
      let hex=bgCol.replace('#',''); if(hex.length===3) hex=hex.split('').map(c=>c+c).join('');
      const r=parseInt(hex.substr(0,2),16), g=parseInt(hex.substr(2,2),16), b=parseInt(hex.substr(4,2),16);
      el.style.background=`rgba(${r},${g},${b},${bgOp})`;
      el.style.borderColor='rgba(0,0,0,0.15)'; el.style.color= mod.textColor || 'var(--text-bright)';
      el.style.padding='3px 6px'; el.style.borderRadius='4px';
    } else {
      el.style.background='color-mix(in srgb, var(--accent) 18%, var(--card))'; el.style.borderColor='var(--accent)'; el.style.color='var(--text-bright)';
      el.style.padding='5px 10px'; el.style.borderRadius='999px';
    }
    el.style.boxShadow='0 4px 16px rgba(0,0,0,0.35)'; el.style.willChange='transform, left, top';
    el.style.transform=`scale(${scale})`; el.style.transformOrigin='top left';
    el.textContent=txt;
    el.style.opacity = mod.opacity ?? 1;
    if(isKeystrokes && mod.keystrokesOutline===false){
      el.style.borderColor='transparent';
    }
    let x=Math.max(0, Math.min(mod.x, windowSize.scaledWidth - 12));
    let y=Math.max(0, Math.min(mod.y, windowSize.scaledHeight - 12));
    el.style.left=(x * sx)+'px';
    el.style.top=(y * sy)+'px';
    if(editorSelectedId===id){ el.style.outline='2px solid var(--accent)'; el.style.outlineOffset='1px'; el.style.zIndex='2'; }
    outer.appendChild(el);
  });
  const selLabel=document.querySelector('#editorSelectedLabel');
  if(selLabel) selLabel.textContent = editorSelectedId ? (MODULES_META[editorSelectedId]?.name || editorSelectedId) : (enabledIds[0] ? (MODULES_META[enabledIds[0]]?.name||enabledIds[0]) : '-');
  const selMod = editorSelectedId ? config.modules[editorSelectedId] : (enabledIds[0] ? config.modules[enabledIds[0]] : null);
  const posEl=document.querySelector('#editorPos'); if(posEl) posEl.textContent = selMod ? `${selMod.x}, ${selMod.y} - ${(selMod.scale||1).toFixed(2)}x` : '-';
  const ex=document.querySelector('#editorX'); if(ex && document.activeElement!==ex) ex.value = selMod ? selMod.x : 10;
  const ey=document.querySelector('#editorY'); if(ey && document.activeElement!==ey) ey.value = selMod ? selMod.y : 10;
  }catch(e){ console.error('[MoidClient] syncEditorItems error', e); try{ fetch('/api/log', {method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({level:'error', msg: 'syncEditorItems '+String(e), stack: e.stack})}); }catch(e2){} }
}
function syncEditorItem(){ try{ syncEditorItems(); }catch(e){ console.error('[MoidClient] syncEditorItem error', e); } }
function setupEditorDrag(){
  const outer=document.querySelector('#hudPreviewOuter');
  if(!outer) return;
  let dragging=false, dragId=null, startX=0, startY=0, startModX=0, startModY=0, dragEl=null;
  let lastDragSend=0;
  outer.addEventListener('pointerdown', e=>{
    const item=e.target.closest('.hud-preview-item');
    if(!item) return;
    dragId=item.dataset.id; dragEl=item; editorSelectedId=dragId;
    dragging=true; isDraggingHud=true; item.setPointerCapture(e.pointerId); item.style.cursor='grabbing';
    startX=e.clientX; startY=e.clientY;
    const mod0=config.modules[dragId];
    startModX=mod0 ? (mod0.x||0) : 0;
    startModY=mod0 ? (mod0.y||0) : 0;
    const selLabel=document.querySelector('#editorSelectedLabel'); if(selLabel) selLabel.textContent=MODULES_META[dragId]?.name||dragId;
    document.querySelectorAll('.hud-preview-item').forEach(el=>{
      const isSel=el.dataset.id===dragId;
      el.style.outline = isSel ? '2px solid var(--accent)' : '';
      el.style.outlineOffset = isSel ? '1px' : '';
      el.style.zIndex = isSel ? '2' : '';
    });
    const mod=config.modules[dragId]; if(mod){
      const ex=document.querySelector('#editorX'); if(ex) ex.value=mod.x;
      const ey=document.querySelector('#editorY'); if(ey) ey.value=mod.y;
      const posEl=document.querySelector('#editorPos'); if(posEl) posEl.textContent=`${mod.x}, ${mod.y} - ${(mod.scale||1).toFixed(2)}x`;
    }
    e.preventDefault();
  });
  outer.addEventListener('pointermove', e=>{
    if(!dragging || !dragEl || !dragId) return;
    const rect=outer.getBoundingClientRect();
    const sx=rect.width / (windowSize.scaledWidth||640);
    const sy=rect.height / (windowSize.scaledHeight||360);
    const dx=(e.clientX-startX) / (sx||1);
    const dy=(e.clientY-startY) / (sy||1);
    const mod=config.modules[dragId];
    if(!mod) return;
    let nx=Math.round(startModX+dx);
    let ny=Math.round(startModY+dy);
    nx=Math.max(0, Math.min((windowSize.scaledWidth||640)-12, nx));
    ny=Math.max(0, Math.min((windowSize.scaledHeight||360)-12, ny));
    mod.x=nx; mod.y=ny;
    dragEl.style.left=(nx*sx)+'px';
    dragEl.style.top=(ny*sy)+'px';
    const ex=document.querySelector('#editorX'); if(ex && document.activeElement!==ex) ex.value=nx;
    const ey=document.querySelector('#editorY'); if(ey && document.activeElement!==ey) ey.value=ny;
    const posEl=document.querySelector('#editorPos'); if(posEl) posEl.textContent=`${nx}, ${ny} - ${(mod.scale||1).toFixed(2)}x`;
    const now=Date.now();
    if(now-lastDragSend>120){
      lastDragSend=now;
      send({type:'UPDATE_MODULE', id:dragId, data:{x:nx, y:ny}});
    }
    e.preventDefault();
  });
  function endEditorDrag(){
    if(!dragging) return;
    dragging=false; isDraggingHud=false;
    if(dragEl) dragEl.style.cursor='grab';
    const doneId=dragId;
    const mod=doneId ? config.modules[doneId] : null;
    dragEl=null; dragId=null;
    if(doneId && mod) send({type:'UPDATE_MODULE', id:doneId, data:{x:mod.x, y:mod.y}});
    syncEditorItems();
  }
  outer.addEventListener('pointerup', endEditorDrag);
  outer.addEventListener('pointercancel', endEditorDrag);
}
function setupModulePicker(id, field, hex, scopeCard){
  const isBg = field==='backgroundColor';
  const pickerSel = isBg ? `[data-bg-picker="${id}"]` : `[data-text-picker="${id}"]`;
  const picker = (scopeCard && scopeCard.querySelector(pickerSel)) || document.querySelector(pickerSel);
  if(!picker) return;
  picker.classList.add('open');
  // always sync hsv to current hex, and store on picker for drag handlers
  let hsv = hexToHsv(hex|| (isBg ? '#1A1B20' : '#F9FAFB'));
  picker._hsv = hsv;
  function hexToRgbLocal(h){
    try{
      let c=(h||'').replace('#',''); if(c.length===3) c=c.split('').map(x=>x+x).join('');
      return {r:parseInt(c.substr(0,2),16), g:parseInt(c.substr(2,2),16), b:parseInt(c.substr(4,2),16)};
    }catch(e){ return null; }
  }
  function currentAlphaVal(){
    const m=config.modules[id]||{};
    if(isBg) return Math.max(0, Math.min(1, m.backgroundOpacity ?? 0.85));
    return Math.max(0.2, Math.min(1, m.opacity ?? 1));
  }
  picker._alpha=currentAlphaVal();
  function paintAlphaLocal(){
    const aBar=picker.querySelector(isBg ? `[data-bg-alpha="${id}"]` : `[data-text-alpha="${id}"]`);
    const aCur=picker.querySelector(isBg ? `[data-bg-alphacur="${id}"]` : `[data-text-alphacur="${id}"]`);
    if(!aBar) return;
    const curHex=hsvToHex(picker._hsv.h, picker._hsv.s, picker._hsv.v);
    const rgb=hexToRgbLocal(curHex);
    if(rgb) aBar.style.background=`linear-gradient(to bottom, rgba(${rgb.r},${rgb.g},${rgb.b},1) 0%, rgba(${rgb.r},${rgb.g},${rgb.b},0) 100%), repeating-conic-gradient(#999 0% 25%, white 0% 50%) 50% / 8px 8px`;
    if(aCur) aCur.style.top=((1-picker._alpha)*100)+'%';
  }
  if(picker.dataset.inited){
    const sv2 = picker.querySelector(isBg ? `[data-bg-sv="${id}"]` : `[data-text-sv="${id}"]`);
    const svCur2 = picker.querySelector(isBg ? `[data-bg-svcur="${id}"]` : `[data-text-svcur="${id}"]`);
    const hueCur2 = picker.querySelector(isBg ? `[data-bg-huecur="${id}"]` : `[data-text-huecur="${id}"]`);
    if(sv2) sv2.style.background=`hsl(${hsv.h} 100% 50%)`;
    if(svCur2){ svCur2.style.left=(hsv.s*100)+'%'; svCur2.style.top=((1-hsv.v)*100)+'%'; }
    if(hueCur2) hueCur2.style.top=(hsv.h/360*100)+'%';
    paintAlphaLocal();
    const cardEl = picker.closest('.card') || scopeCard || document;
    const inp2=(cardEl.querySelector ? cardEl.querySelector(`input[data-field="${field}"][data-id="${id}"]`) : null) || document.querySelector(`input[data-field="${field}"][data-id="${id}"]`);
    const preview2=(cardEl.querySelector ? cardEl.querySelector(isBg ? `[data-bg-preview="${id}"]` : `[data-text-preview="${id}"]`) : null) || document.querySelector(isBg ? `[data-bg-preview="${id}"]` : `[data-text-preview="${id}"]`);
    if(inp2) inp2.value=hex||'';
    if(preview2){ if(field==='textColor' && !hex) preview2.style.background='repeating-conic-gradient(#999 0% 25%, white 0% 50%) 50% / 8px 8px'; else preview2.style.background=hex||'#1A1B20'; }
    return;
  }
  picker.dataset.inited='1';
  picker._hsv = hsv;
  const sv = picker.querySelector(isBg ? `[data-bg-sv="${id}"]` : `[data-text-sv="${id}"]`);
  const hue = picker.querySelector(isBg ? `[data-bg-hue="${id}"]` : `[data-text-hue="${id}"]`);
  const svCur = picker.querySelector(isBg ? `[data-bg-svcur="${id}"]` : `[data-text-svcur="${id}"]`);
  const hueCur = picker.querySelector(isBg ? `[data-bg-huecur="${id}"]` : `[data-text-huecur="${id}"]`);
  const alphaBar = picker.querySelector(isBg ? `[data-bg-alpha="${id}"]` : `[data-text-alpha="${id}"]`);
  const alphaCur = picker.querySelector(isBg ? `[data-bg-alphacur="${id}"]` : `[data-text-alphacur="${id}"]`);
  if(!sv||!hue) return;

  function syncAlphaSliderUI(a){
    const cardElx=picker.closest('.card')||scopeCard||document;
    if(isBg) return; // bg has no slider in card — alpha bar is the control
    const opInp=(cardElx.querySelector?cardElx.querySelector(`input[data-field="opacity"][data-id="${id}"]`):null);
    if(opInp){ opInp.value=a; updateSliderFill(opInp); }
    const lbl=(cardElx.querySelector?cardElx.querySelector(`[data-opacity-label="${id}"]`):null);
    if(lbl) lbl.textContent=parseFloat(a).toFixed(2);
  }
  
  function apply(h){
    sv.style.background=`hsl(${hsv.h} 100% 50%)`;
    if(svCur){ svCur.style.left=(picker._hsv.s*100)+'%'; svCur.style.top=((1-picker._hsv.v)*100)+'%'; }
    if(hueCur) hueCur.style.top=(picker._hsv.h/360*100)+'%';
    paintAlphaLocal();
    const cardEl = picker.closest('.card') || scopeCard || document;
    const inp=(cardEl.querySelector ? cardEl.querySelector(`input[data-field="${field}"][data-id="${id}"]`) : null) || document.querySelector(`input[data-field="${field}"][data-id="${id}"]`);
    const preview=(cardEl.querySelector ? cardEl.querySelector(isBg ? `[data-bg-preview="${id}"]` : `[data-text-preview="${id}"]`) : null) || document.querySelector(isBg ? `[data-bg-preview="${id}"]` : `[data-text-preview="${id}"]`);
    if(inp) { inp.value=h; if(preview){ if(field==='textColor' && !h) preview.style.background='repeating-conic-gradient(#999 0% 25%, white 0% 50%) 50% / 8px 8px'; else preview.style.background=h; } }
  }
  apply(hex, hsv);
  paintAlphaLocal();
  let dSv=false, dHue=false, dAlpha=false;
  function pushAlpha(a){
    a=isBg ? Math.max(0, Math.min(1, a)) : Math.max(0.2, Math.min(1, a));
    picker._alpha=a;
    if(alphaCur) alphaCur.style.top=((1-a)*100)+'%';
    const m=config.modules[id]=config.modules[id]||{x:10,y:10,scale:1,opacity:1,enabled:false};
    const patch={};
    if(isBg){ m.backgroundOpacity=a; patch.backgroundOpacity=a; }
    else{ m.opacity=a; patch.opacity=a; }
    send({type:'UPDATE_MODULE', id, data:patch});
    syncAlphaSliderUI(a);
  }
  function fromAlpha(e){
    if(!alphaBar) return;
    const r=alphaBar.getBoundingClientRect();
    const a=1-Math.max(0, Math.min((e.clientY-r.top)/r.height, 1));
    pushAlpha(Math.round(a*100)/100);
  }
  function fromSv(e){
    const r=sv.getBoundingClientRect(); const s=Math.max(0,Math.min((e.clientX-r.left)/r.width,1)); const v=1-Math.max(0,Math.min((e.clientY-r.top)/r.height,1));
    picker._hsv.s=s; picker._hsv.v=v; const h2=hsvToHex(picker._hsv.h,picker._hsv.s,picker._hsv.v); const cardEl2=picker.closest('.card')||scopeCard||document; const inp=(cardEl2.querySelector?cardEl2.querySelector(`input[data-field="${field}"][data-id="${id}"]`):null)||document.querySelector(`input[data-field="${field}"][data-id="${id}"]`); if(inp){ inp.value=h2; inp.dispatchEvent(new Event('change',{bubbles:true})); } apply(h2, hsv);
  }
  function fromHue(e){
    const r=hue.getBoundingClientRect(); const h=(Math.max(0,Math.min((e.clientY-r.top)/r.height,1))*360); picker._hsv.h=h; const hex2=hsvToHex(picker._hsv.h,picker._hsv.s,picker._hsv.v); const cardEl3=picker.closest('.card')||scopeCard||document; const inp2=(cardEl3.querySelector?cardEl3.querySelector(`input[data-field="${field}"][data-id="${id}"]`):null)||document.querySelector(`input[data-field="${field}"][data-id="${id}"]`); if(inp2){ inp2.value=hex2; inp2.dispatchEvent(new Event('change',{bubbles:true})); } apply(hex2, picker._hsv);
  }
  sv.addEventListener('pointerdown', e=>{ dSv=true; sv.setPointerCapture(e.pointerId); fromSv(e); });
  sv.addEventListener('pointermove', e=>{ if(dSv) fromSv(e); });
  sv.addEventListener('pointerup', ()=> dSv=false);
  hue.addEventListener('pointerdown', e=>{ dHue=true; hue.setPointerCapture(e.pointerId); fromHue(e); });
  hue.addEventListener('pointermove', e=>{ if(dHue) fromHue(e); });
  hue.addEventListener('pointerup', ()=> dHue=false);
  if(alphaBar){
    alphaBar.addEventListener('pointerdown', e=>{ dAlpha=true; try{alphaBar.setPointerCapture(e.pointerId);}catch(err){} fromAlpha(e); });
    alphaBar.addEventListener('pointermove', e=>{ if(dAlpha) fromAlpha(e); });
    alphaBar.addEventListener('pointerup', ()=> dAlpha=false);
    alphaBar.addEventListener('pointercancel', ()=> dAlpha=false);
  }
}
function setAccent(hex){
  if(!/^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$/.test(hex)) return;
  accent=hex;
  document.documentElement.style.setProperty('--accent-color',hex);
  document.documentElement.style.setProperty('--accent',hex);
  const ap=$('#accentPreview'); if(ap) ap.style.background=hex;
  localStorage.setItem('cc_accent',hex);
  const hsv=hexToHsv(hex);
  pickerHsv=hsv;
  updatePickerUI(hex, hsv);
  const ch=$('#customHex'); if(ch && document.activeElement!==ch) ch.value=hex;
  const oh=$('#onboardHex'); if(oh && document.activeElement!==oh) oh.value=hex;
  const tp=$('#themePreview'); if(tp){ tp.style.background=hex; tp.textContent=hex.toUpperCase(); tp.style.color= isLight(hex) ? '#0f1115' : 'white'; }
  const op=$('#onboardPreview'); if(op){ op.style.background=hex; op.textContent=hex.toUpperCase(); op.style.color= isLight(hex) ? '#0f1115' : 'white'; }
  $$('input[type="range"]').forEach(updateSliderFill);
}
function isLight(hex){
  const c=hex.replace('#',''); const r=parseInt(c.substring(0,2),16), g=parseInt(c.substring(2,4),16), b=parseInt(c.substring(4,6),16);
  const l=(0.299*r+0.587*g+0.114*b)/255; return l>0.6;
}
function renderPresets(containerId,onPick){
  const c=document.getElementById(containerId); if(!c) return; c.innerHTML='';
  PRESETS.forEach(p=>{
    const b=document.createElement('button');
    b.className='w-full h-14 rounded-xl border flex flex-col items-center justify-center gap-1 text-[11px] font-medium';
    b.style.cssText=`background:var(--bg);border-color:var(--border);transition: border-color 220ms, transform 220ms cubic-bezier(0.34,1.56,0.64,1)`;
    b.innerHTML=`<span class="w-6 h-6 rounded-full" style="background:${p.hex};box-shadow:0 0 10px ${p.hex}55"></span>${p.name}`;
    b.onclick=()=>onPick(p.hex);
    b.onmouseenter=()=>{b.style.borderColor=p.hex; b.style.transform='translateY(-1px) scale(1.02)'};
    b.onmouseleave=()=>{b.style.borderColor='var(--border)'; b.style.transform='none'};
    c.appendChild(b);
  });
}
function updateSliderFill(el){
  const min=parseFloat(el.min), max=parseFloat(el.max), val=parseFloat(el.value);
  const pct=((val-min)/(max-min))*100;
  el.style.background=`linear-gradient(to right, var(--accent) 0%, var(--accent) ${pct}%, var(--border) ${pct}%, var(--border) 100%)`;
}
function cardTemplate(id,meta,data,animate){
  const enabled=!!data.enabled;
  const icon=ICONS[id]||'';
  const isFocused=focusedId===id;
  const enterClass = animate ? 'enter' : '';
  return `<div class="card p-4 flex flex-col gap-3 ${isFocused?'focused':''} ${enterClass}" data-id="${id}" style="${animate?`animation-delay:${Math.random()*60}ms`:''}">
    <div class="card-header flex items-start justify-between gap-3" data-open="${id}">
      <div class="flex gap-3 flex-1 min-w-0">
        <div class="icon-box w-8 h-8 rounded-lg flex items-center justify-center shrink-0" style="background:var(--bg);border:1px solid var(--border)">${icon}</div>
        <div class="min-w-0">
          <div class="card-title font-medium text-[13.5px] leading-none truncate" style="color:var(--text-bright);transition: color 180ms">${meta.name}</div>
          <div class="text-xs mt-1 leading-snug" style="color:var(--text-muted)">${meta.desc}</div>
        </div>
      </div>
      <div class="toggle ${enabled?'active':''}" data-toggle="${id}" title="Toggle"><div class="toggle-dot"></div></div>
    </div>
    <div class="drawer ${isFocused?'open':''}" data-drawer="${id}">
      <div class="space-y-4 pt-2">
        <div class="focus-bar" style="${isFocused?'':'display:none'}">
          <span class="text-xs font-medium" style="color:var(--text-muted)">Configuring <span style="color:var(--text-bright)">${meta.name}</span></span>
          <button class="text-xs px-2.5 py-1 rounded-full border font-medium" style="border-color:var(--border);background:var(--card);color:var(--text-muted)" data-back="${id}">← Back</button>
        </div>
        <div class="text-[11px] px-3 py-2 rounded-full border flex items-center gap-2" style="border-color:var(--border);background:var(--bg);color:var(--text-muted)">Position edited in <button class="underline" style="color:var(--accent)" onclick="document.querySelector('[data-tab=editor]').click()">HUD Editor</button> • <span style="font-family:'JetBrains Mono',monospace; color:var(--text-bright)">${data.x}, ${data.y}</span></div>
        <div class="space-y-3 pt-3 border-t" style="border-color:var(--border)">
          <div class="flex items-center justify-between">
            <span class="text-xs font-medium" style="color:var(--text-muted)">Background</span>
            <div class="toggle ${data.background?'active':''}" data-bg-toggle="${id}"><div class="toggle-dot"></div></div>
          </div>
          <div class="${data.background?'':'hidden'} space-y-2" data-bg-section="${id}">
            <div class="flex gap-2 items-center">
              <div class="w-6 h-6 rounded-full border shrink-0" style="background:${data.backgroundColor||'#1A1B20'};border-color:var(--border)" data-bg-preview="${id}"></div>
              <input data-field="backgroundColor" data-id="${id}" value="${data.backgroundColor||'#1A1B20'}" placeholder="#1A1B20" spellcheck="false" class="field-input flex-1 px-2.5 py-1.5 rounded-full border text-xs font-mono" style="background:var(--bg);border-color:var(--border)"/>
              <button class="picker-icon-btn" data-bg-picker-toggle="${id}" title="Color picker"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2.7l5.66 5.66a8 8 0 1 1-11.31 0z"/><circle cx="12" cy="12" r="2.2"/></svg></button>
            </div>
            <div class="picker-wrap" data-bg-picker="${id}">
              <div class="sv-box" data-bg-sv="${id}" style="width:140px;height:90px"><div class="sv-cursor" data-bg-svcur="${id}"></div></div>
              <div class="hue-bar" data-bg-hue="${id}" style="height:90px"><div class="hue-cursor" data-bg-huecur="${id}"></div></div>
              <div class="alpha-bar" data-bg-alpha="${id}" style="height:90px" title="Transparency"><div class="alpha-cursor" data-bg-alphacur="${id}"></div></div>
            </div>
          </div>
          <div class="space-y-2">
            <span class="text-xs font-medium" style="color:var(--text-muted)">Text color <span class="text-[10px]">(empty = auto by ping)</span></span>
            <div class="flex gap-2 items-center">
              <div class="w-6 h-6 rounded-full border shrink-0" style="background:${data.textColor||'transparent'};border-color:var(--border); ${!data.textColor?'background: repeating-conic-gradient(#999 0% 25%, white 0% 50%) 50% / 8px 8px':''}" data-text-preview="${id}"></div>
              <input data-field="textColor" data-id="${id}" value="${data.textColor||''}" placeholder="auto" spellcheck="false" class="field-input flex-1 px-2.5 py-1.5 rounded-full border text-xs font-mono" style="background:var(--bg);border-color:var(--border)"/>
              <button class="text-xs px-2 py-1 rounded-full border" style="border-color:var(--border);background:var(--bg);color:var(--text-muted)" onclick="this.closest('[data-id]').querySelector('[data-field=textColor]').value=''; this.closest('[data-id]').querySelector('[data-field=textColor]').dispatchEvent(new Event('change',{bubbles:true}))">Clear</button>
              <button class="picker-icon-btn" data-text-picker-toggle="${id}" title="Color picker"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2.7l5.66 5.66a8 8 0 1 1-11.31 0z"/><circle cx="12" cy="12" r="2.2"/></svg></button>
            </div>
            <div class="picker-wrap" data-text-picker="${id}">
              <div class="sv-box" data-text-sv="${id}" style="width:140px;height:90px"><div class="sv-cursor" data-text-svcur="${id}"></div></div>
              <div class="hue-bar" data-text-hue="${id}" style="height:90px"><div class="hue-cursor" data-text-huecur="${id}"></div></div>
              <div class="alpha-bar" data-text-alpha="${id}" style="height:90px" title="Transparency"><div class="alpha-cursor" data-text-alphacur="${id}"></div></div>
            </div>
          </div>
          ${id==='ping'?`
          <div class="space-y-2">
            <label class="text-xs flex flex-col gap-1.5" style="color:var(--text-muted)">Format <span class="text-[10px]">placeholder {ping}, e.g. "{ping} ms" hides prefix, "Ping: {ping}" hides suffix</span>
              <input data-field="format" data-id="${id}" value="${(data.format||'Ping: {ping} ms').replace(/"/g,'&quot;')}" placeholder="Ping: {ping} ms" spellcheck="false" class="field-input w-full px-2.5 py-1.5 rounded-full border text-xs font-mono" style="background:var(--bg);border-color:var(--border)"/>
            </label>
            <label class="flex items-center gap-2 text-xs" style="color:var(--text-muted)"><input type="checkbox" ${data.shadow?'checked':''} data-field="shadow" data-id="${id}" class="rounded accent-[var(--accent)]"> Text shadow</label>
          </div>`:''}
        </div>
        <label class="text-xs flex flex-col gap-2" style="color:var(--text-muted)">Scale <span data-scale-label="${id}" style="font-family:'JetBrains Mono',monospace; color:var(--text-bright)">${(data.scale??1).toFixed(2)}x</span> <input data-field="scale" data-id="${id}" type="range" min="0.5" max="2" step="0.01" value="${data.scale??1}" class="range"/></label>
        <label class="text-xs flex flex-col gap-2" style="color:var(--text-muted)">Opacity <span data-opacity-label="${id}" style="font-family:'JetBrains Mono',monospace; color:var(--text-bright)">${(data.opacity??1).toFixed(2)}</span> <input data-field="opacity" data-id="${id}" type="range" min="0.2" max="1" step="0.01" value="${data.opacity??1}" class="range"/></label>
        <div class="flex items-center gap-2 pt-1">
          <button class="text-xs px-3 py-1.5 rounded-full border" style="border-color:var(--border);background:var(--bg);color:var(--text-muted)" data-reset="${id}">Reset</button>
          <span class="text-[11px]" style="color:var(--text-muted)">Click header to focus • Saves automatically</span>
        </div>
      </div>
    </div>
  </div>`;
}
function enterFocus(id){ focusedId=id; render(false); setTimeout(()=>{ const el=document.querySelector(`[data-id="${id}"]`); if(el) el.scrollIntoView({behavior:'smooth', block:'start'}); }, 60); }
function exitFocus(){ focusedId=null; render(false); }
function render(animate=false){
  const shouldAnimate = animate && !hasInitialRendered;
  const hudGrid=$('#hudGrid'), visualsGrid=$('#visualsGrid'), allGrid=$('#allGrid'); if(!hudGrid||!visualsGrid) return;
  hudGrid.innerHTML=''; visualsGrid.innerHTML=''; if(allGrid) allGrid.innerHTML='';
  const allIds=Object.keys(MODULES_META);
  for(const id of allIds){
    if(focusedId && focusedId!==id) continue;
    const meta=MODULES_META[id];
    const data=(config.modules&&config.modules[id])||{enabled:false,x:10,y:10,scale:1,opacity:1};
    const html=cardTemplate(id,meta,data,shouldAnimate);
    const target=meta.cat==='visuals'?visualsGrid:hudGrid;
    target.insertAdjacentHTML('beforeend',html);
  }
  // all tab - filtered by search
  if(allGrid){
    const allIdsAll = Object.keys(MODULES_META).filter(id=>{
      if(!searchQuery) return true;
      const m=MODULES_META[id];
      return m.name.toLowerCase().includes(searchQuery) || m.desc.toLowerCase().includes(searchQuery) || id.toLowerCase().includes(searchQuery) || m.cat.toLowerCase().includes(searchQuery);
    });
    const visibleAll = focusedId ? allIdsAll.filter(id=>id===focusedId) : allIdsAll;
    for(const id of visibleAll){
      const meta=MODULES_META[id]; const data=(config.modules&&config.modules[id])||{enabled:false,x:10,y:10,scale:1,opacity:1};
      const html=cardTemplate(id,meta,data,shouldAnimate);
      allGrid.insertAdjacentHTML('beforeend',html);
    }
    const allCountEl=document.querySelector('#allCount'); if(allCountEl) allCountEl.textContent=allIdsAll.length;
    const noRes=document.querySelector('#noResults'); if(noRes) noRes.classList.toggle('hidden', allIdsAll.length>0);
  }
  if(focusedId){
    const cat=MODULES_META[focusedId].cat;
    if(cat==='hud') visualsGrid.parentElement.style.display='none'; else hudGrid.parentElement.style.display='none';
  } else {
    visualsGrid.parentElement.style.display=''; hudGrid.parentElement.style.display='';
  }
  const hudCountEl=document.querySelector('#hudCount');
  if(hudCountEl) hudCountEl.textContent=Object.values(config.modules).filter(m=>m.enabled).length;
  $$('input[type="range"]').forEach(updateSliderFill);
  $$('[data-toggle]').forEach(el=>{
    el.onclick=(e)=>{
      e.stopPropagation();
      const id=el.getAttribute('data-toggle');
      const cur=config.modules[id]=config.modules[id]||{enabled:false,x:10,y:10,scale:1,opacity:1};
      const next=!cur.enabled;
      cur.enabled=next;
      el.classList.toggle('active',next);
      const card=document.querySelector(`[data-id="${id}"]`);
      if(card){ card.style.transform='scale(1.015)'; setTimeout(()=>card.style.transform='',160); }
      if(hudCountEl) hudCountEl.textContent=Object.values(config.modules).filter(m=>m.enabled).length;
      send({type:'UPDATE_MODULE',id,data:{enabled:next}});
    };
  });
  $$('[data-open]').forEach(el=>{
    el.onclick=()=>{
      const id=el.getAttribute('data-open');
      if(focusedId===id) exitFocus(); else enterFocus(id);
    };
  });
  $$('[data-back]').forEach(b=> b.onclick=(e)=>{ e.stopPropagation(); exitFocus(); });
  $$('[data-reset]').forEach(b=>{
    b.onclick=(e)=>{
      e.stopPropagation();
      const id=b.getAttribute('data-reset');
      send({type:'UPDATE_MODULE',id,data:{x:10,y:10,scale:1,opacity:1}});
      const m=config.modules[id]={...config.modules[id], x:10,y:10,scale:1,opacity:1};
      const card=document.querySelector(`[data-id="${id}"]`);
      if(card){
        card.querySelectorAll('input[data-field]').forEach(inp=>{
          const f=inp.getAttribute('data-field');
          if(f==='x') inp.value=m.x;
          if(f==='y') inp.value=m.y;
          if(f==='scale'){ inp.value=m.scale; updateSliderFill(inp); }
          if(f==='opacity'){ inp.value=m.opacity; updateSliderFill(inp); }
        });
        const s=card.querySelector(`[data-scale-label="${id}"]`); if(s) s.textContent=m.scale.toFixed(2)+'x';
        const o=card.querySelector(`[data-opacity-label="${id}"]`); if(o) o.textContent=m.opacity.toFixed(2);
      }
    };
  });
  // background toggle
  $$('[data-bg-toggle]').forEach(el=>{
    el.onclick=(e)=>{
      e.stopPropagation();
      const id=el.getAttribute('data-bg-toggle');
      const cur=config.modules[id]=config.modules[id]||{};
      const next=!cur.background;
      cur.background=next; el.classList.toggle('active', next);
      lastBgToggle=Date.now();
      const sec=document.querySelector(`[data-bg-section="${id}"]`); if(sec) sec.classList.toggle('hidden', !next);
      send({type:'UPDATE_MODULE', id, data:{background: next}});
    };
  });
  $$('[data-bg-picker-toggle]').forEach(btn=>{
    btn.onclick=(e)=>{
      e.stopPropagation();
      try{
        const id=btn.getAttribute('data-bg-picker-toggle');
        const card=btn.closest('.card');
        const picker=card ? card.querySelector(`[data-bg-picker="${id}"]`) : document.querySelector(`[data-bg-picker="${id}"]`);
        const target=picker || document.querySelector(`[data-bg-picker="${id}"]`);
        if(!target) return;
        const willOpen=!target.classList.contains('open');
        target.classList.toggle('open', willOpen);
        btn.classList.toggle('open', willOpen);
        if(willOpen){
          const mod=config.modules[id]||{}; const hex=mod.backgroundColor||'#1A1B20';
          setupModulePicker(id, 'backgroundColor', hex, card);
        }
      }catch(err){ console.error('[MoidClient] bg picker toggle failed', err); }
    };
  });
  $$('[data-text-picker-toggle]').forEach(btn=>{
    btn.onclick=(e)=>{
      e.stopPropagation();
      try{
        const id=btn.getAttribute('data-text-picker-toggle');
        const card=btn.closest('.card');
        const picker=card ? card.querySelector(`[data-text-picker="${id}"]`) : document.querySelector(`[data-text-picker="${id}"]`);
        const target=picker || document.querySelector(`[data-text-picker="${id}"]`);
        if(!target) return;
        const willOpen=!target.classList.contains('open');
        target.classList.toggle('open', willOpen);
        btn.classList.toggle('open', willOpen);
        if(willOpen){
          const mod=config.modules[id]||{}; const hex=mod.textColor||'#F9FAFB';
          setupModulePicker(id, 'textColor', hex||'#F9FAFB', card);
        }
      }catch(err){ console.error('[MoidClient] text picker toggle failed', err); }
    };
  });
  // clicking the color preview dot also toggles the picker (UX fallback)
  $$('[data-bg-preview]').forEach(dot=>{
    dot.style.cursor='pointer';
    dot.onclick=(e)=>{
      e.stopPropagation();
      const card=dot.closest('.card');
      const id=card ? card.getAttribute('data-id') : null;
      const tgl=card ? card.querySelector('[data-bg-picker-toggle]') : null;
      if(tgl) tgl.click();
      else if(id){ const b=document.querySelector(`[data-bg-picker-toggle="${id}"]`); if(b) b.click(); }
    };
  });
  $$('[data-text-preview]').forEach(dot=>{
    dot.style.cursor='pointer';
    dot.onclick=(e)=>{
      e.stopPropagation();
      const card=dot.closest('.card');
      const tgl=card ? card.querySelector('[data-text-picker-toggle]') : null;
      if(tgl) tgl.click();
    };
  });
  $$('input[data-field]').forEach(inp=>{
    const isRange = inp.type==='range';
    const handler=()=>{
      const id=inp.getAttribute('data-id'); const field=inp.getAttribute('data-field');
      let val;
      if(inp.type==='checkbox') val=inp.checked;
      else if(field==='x'||field==='y') val=parseInt(inp.value)||0;
      else if(field==='shadow' || field==='background') val=inp.checked;
      else if(field==='scale'||field==='opacity') val=parseFloat(inp.value);
      else val=inp.value;
      const patch={}; patch[field]=val; send({type:'UPDATE_MODULE',id,data:patch});
      const m=config.modules[id]=config.modules[id]||{x:10,y:10,scale:1,opacity:1,enabled:false}; m[field]=val;
      const card=document.querySelector(`[data-id="${id}"]`);
      if(card){
        if(isRange) updateSliderFill(inp);
        const s=card.querySelector(`[data-scale-label="${id}"]`); if(s && field==='scale') s.textContent=parseFloat(val).toFixed(2)+'x';
        const o=card.querySelector(`[data-opacity-label="${id}"]`); if(o && field==='opacity') o.textContent=parseFloat(val).toFixed(2);
        if(field==='backgroundColor'){
          const p=card.querySelector(`[data-bg-preview="${id}"]`); if(p) p.style.background=val||'#1A1B20';
        }
        if(field==='textColor'){
          const p=card.querySelector(`[data-text-preview="${id}"]`); if(p){ if(!val){ p.style.background='repeating-conic-gradient(#999 0% 25%, white 0% 50%) 50% / 8px 8px'; } else { p.style.background=val; } }
        }
      }
      if(field==='background'){
        const sec=card?.querySelector(`[data-bg-section="${id}"]`); if(sec) sec.classList.toggle('hidden', !val);
      }
    };
    if(isRange){
      inp.addEventListener('pointerdown',()=> isDraggingSlider=true);
      inp.addEventListener('pointerup',()=> { isDraggingSlider=false; handler(); });
      inp.addEventListener('input', ()=>{
        isDraggingSlider=true;
        handler();
        updateSliderFill(inp);
        const id=inp.getAttribute('data-id'); const field=inp.getAttribute('data-field');
        const card=document.querySelector(`[data-id="${id}"]`);
        if(card){
          const s=card.querySelector(`[data-scale-label="${id}"]`); if(s && field==='scale') s.textContent=parseFloat(inp.value).toFixed(2)+'x';
          const o=card.querySelector(`[data-opacity-label="${id}"]`); if(o && field==='opacity') o.textContent=parseFloat(inp.value).toFixed(2);
        }
      });
      inp.addEventListener('change', handler);
    } else if(inp.type==='checkbox'){
      inp.addEventListener('change', handler);
    } else {
      inp.addEventListener('change', handler);
      let t; inp.addEventListener('input', ()=>{ clearTimeout(t); t=setTimeout(handler, 300); });
    }
  });
  hasInitialRendered=true;
}
function patchFromSync(newData){
  config=newData; if(!config.modules) config.modules={};
  const hudCountEl=document.querySelector('#hudCount');
  if(hudCountEl) hudCountEl.textContent=Object.values(config.modules).filter(m=>m.enabled).length;
  const allCountEl=document.querySelector('#allCount');
  if(allCountEl){
    const filtered = Object.keys(MODULES_META).filter(id=>{
      if(!searchQuery) return true;
      const m=MODULES_META[id];
      return m.name.toLowerCase().includes(searchQuery) || m.desc.toLowerCase().includes(searchQuery) || id.toLowerCase().includes(searchQuery);
    });
    allCountEl.textContent=filtered.length;
  }
  if(searchQuery && document.querySelector('#tab-all:not(.hidden)')){
    render(false); return;
  }
  const bgRecentlyToggled = Date.now() - lastBgToggle < 800;
  document.querySelectorAll('.card[data-id]').forEach(card=>{
    const id=card.getAttribute('data-id');
    const data=config.modules[id]; if(!data) return;
    const tgl=card.querySelector('[data-toggle]');
    if(tgl) tgl.classList.toggle('active', !!data.enabled);
    card.querySelectorAll('input[data-field]').forEach(inp=>{
      if(document.activeElement===inp) return;
      if(isDraggingSlider && inp.type==='range' && inp.matches(':active')) return;
      const f=inp.getAttribute('data-field');
      if(f==='x') inp.value=data.x;
      else if(f==='y') inp.value=data.y;
      else if(f==='scale'){ inp.value=data.scale??1; updateSliderFill(inp); }
      else if(f==='opacity'){ inp.value=data.opacity??1; updateSliderFill(inp); }
      else if(f==='backgroundColor') inp.value=data.backgroundColor||'#1A1B20';
      else if(f==='textColor') inp.value=data.textColor||'';
      else if(f==='format') inp.value=data.format||'';
      else if(f==='shadow' || f==='background') inp.checked=!!data[f];
    });
    const s=card.querySelector(`[data-scale-label="${id}"]`); if(s) s.textContent=(data.scale??1).toFixed(2)+'x';
    const o=card.querySelector(`[data-opacity-label="${id}"]`); if(o) o.textContent=(data.opacity??1).toFixed(2);
    const bgPrev=card.querySelector(`[data-bg-preview="${id}"]`); if(bgPrev) bgPrev.style.background=data.backgroundColor||'#1A1B20';
    const txPrev=card.querySelector(`[data-text-preview="${id}"]`); if(txPrev){ if(!data.textColor) txPrev.style.background='repeating-conic-gradient(#999 0% 25%, white 0% 50%) 50% / 8px 8px'; else txPrev.style.background=data.textColor; }
    const bgTog=card.querySelector(`[data-bg-toggle="${id}"]`); if(bgTog && !bgRecentlyToggled) bgTog.classList.toggle('active', !!data.background);
    const bgSec=card.querySelector(`[data-bg-section="${id}"]`); if(bgSec && !bgRecentlyToggled) bgSec.classList.toggle('hidden', !data.background);
  });
  const existingIds=new Set([...document.querySelectorAll('.card[data-id]')].map(c=>c.getAttribute('data-id')));
  const neededIds=focusedId ? [focusedId] : Object.keys(MODULES_META);
  const needsFull = neededIds.some(id=> !existingIds.has(id)) || existingIds.size !== neededIds.length;
  if(needsFull) render(false);
}
function setConnection(state){
  const dot=$('#connDot'), txt=$('#connText');
  if(state){ dot.className='w-2 h-2 rounded-full bg-emerald-500'; dot.style.boxShadow='0 0 8px #10B981'; txt.textContent='Connected to Minecraft'; txt.style.color='#10B981'; }
  else{ dot.className='w-2 h-2 rounded-full bg-red-500 dot-pulse'; dot.style.boxShadow='none'; txt.textContent='Disconnected'; txt.style.color='var(--text-muted)'; }
}
function send(obj){ if(ws&&ws.readyState===1) ws.send(JSON.stringify(obj)); }
function handleSync(data){
  if(data.accentColor) setAccent(data.accentColor);
  if(data.themeTextColor) setThemeText(data.themeTextColor);
  if(!config.modules) config.modules={};
  if(!hasInitialRendered){ config=data; render(true); setTimeout(syncEditorItem, 80); return; }
  if(isDraggingSlider || isDraggingHud){ config=data; patchFromSync(data); return; }
  patchFromSync(data);
  setTimeout(syncEditorItem, 20);
  const onboard=$('#onboarding');
  if(!localStorage.getItem('cc_onboarded')){
    const hasSaved=!!localStorage.getItem('cc_accent');
    if(!hasSaved && data.accentColor==='#8B5CF6'){ onboard.classList.remove('hidden'); onboard.classList.add('flex'); }
  }
}
function connect(){
  const proto=location.protocol==='https:'?'wss:':'ws:';
  const url=proto+'//'+location.host+'/ws';
  $('#wsUrl').textContent=url.replace('wss://','ws://');
  ws=new WebSocket(url);
  ws.onopen=()=>setConnection(true);
  ws.onclose=()=>{ setConnection(false); setTimeout(connect,2000); };
  ws.onerror=()=>setConnection(false);
  ws.onmessage=ev=>{
    try{ const msg=JSON.parse(ev.data); if(msg.type==='SYNC_CONFIG') handleSync(msg.data); if(msg.type==='WINDOW_SIZE') handleWindowSize(msg.data); if(msg.type==='EXPORT_CONFIG') downloadJson(msg.data,'moid-client.json'); }catch(e){ console.error(e); }
  };
}
function switchTab(tab){
  const current=document.querySelector('.tab-panel:not(.hidden)');
  const next=document.getElementById('tab-'+tab);
  if(current && next && current!==next){
    current.style.animation='tabOut 200ms cubic-bezier(0.4,0,0.2,1) both';
    setTimeout(()=>{
      focusedId=null;
      $$('[id^="tab-"]').forEach(s=>{ s.classList.add('hidden'); s.style.animation=''; });
      next.classList.remove('hidden');
      next.style.animation='tabIn 340ms cubic-bezier(0.16,1,0.3,1) both';
      $$('.sidebar-item').forEach(i=>i.classList.toggle('active',i.getAttribute('data-tab')===tab));
      $$('.tab-m').forEach(i=>{
        const active=i.getAttribute('data-tab')===tab;
        i.classList.toggle('active',active);
        i.style.borderColor=active?'var(--accent)':'var(--border)';
        i.style.background=active?'color-mix(in srgb, var(--accent) 15%, transparent)':'transparent';
      });
      render(false); setTimeout(syncEditorItem, 50);
    }, 180);
    return;
  }
  focusedId=null;
  $$('[id^="tab-"]').forEach(s=>s.classList.add('hidden'));
  if(next){ next.classList.remove('hidden'); next.style.animation='tabIn 340ms cubic-bezier(0.16,1,0.3,1) both'; }
  $$('.sidebar-item').forEach(i=>i.classList.toggle('active',i.getAttribute('data-tab')===tab));
  $$('.tab-m').forEach(i=>{
    const active=i.getAttribute('data-tab')===tab;
    i.classList.toggle('active',active);
    i.style.borderColor=active?'var(--accent)':'var(--border)';
    i.style.background=active?'color-mix(in srgb, var(--accent) 15%, transparent)':'transparent';
  });
  render(false); setTimeout(syncEditorItem, 50);
}
document.addEventListener('DOMContentLoaded',()=>{
  const saved=localStorage.getItem('cc_accent'); if(saved) setAccent(saved); else setAccent(accent);
  updatePickerUI(accent, pickerHsv);
  const savedText=localStorage.getItem('cc_text'); if(savedText) setThemeText(savedText); else setThemeText(themeText);
  updateTextPickerUI(themeText, textPickerHsv);
  setupPicker('#themeSv','#themeHue','#themeSvCursor','#themeHueCursor');
  setupPicker('#onboardSv','#onboardHue','#onboardSvCursor','#onboardHueCursor');
  setupTextPicker('#textThemeSv','#textThemeHue','#textThemeSvCursor','#textThemeHueCursor');
  const edEye=document.querySelector('#eyeDropperBtn');
  if(edEye && window.EyeDropper){
    edEye.classList.remove('hidden'); edEye.classList.add('flex');
    edEye.onclick=async()=>{
      try{ const eye=new EyeDropper(); const res=await eye.open(); if(res && res.sRGBHex){ pickerHsv=hexToHsv(res.sRGBHex); setAccent(res.sRGBHex); updatePickerUI(res.sRGBHex, pickerHsv); send({type:'UPDATE_ACCENT_COLOR',color:res.sRGBHex}); } }catch(e){}
    };
  }
  $('#accentBtn').onclick=()=>switchTab('theme');
  const ohInput=document.querySelector('#onboardHex');
  if(ohInput){
    ohInput.addEventListener('input', e=>{
      const v=e.target.value.trim();
      if(/^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$/.test(v)){ pickerHsv=hexToHsv(v); setAccent(v); updatePickerUI(v, pickerHsv); }
    });
  }
  const chInput=document.querySelector('#customHex');
  if(chInput){
    let t;
    const applyNow=(v)=>{
      if(!/^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$/.test(v)) return;
      pickerHsv=hexToHsv(v); setAccent(v); updatePickerUI(v, pickerHsv);
      send({type:'UPDATE_ACCENT_COLOR',color:v});
    };
    chInput.addEventListener('input', e=>{ clearTimeout(t); const v=e.target.value.trim(); t=setTimeout(()=>applyNow(v), 250); });
    chInput.addEventListener('change', e=>{ clearTimeout(t); applyNow(e.target.value.trim()); });
  }
  const thInput=document.querySelector('#textThemeHex');
  if(thInput){
    let t2;
    const applyTextNow=(v)=>{
      if(!/^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$/.test(v)) return;
      textPickerHsv=hexToHsv(v); setThemeText(v); updateTextPickerUI(v, textPickerHsv);
      send({type:'UPDATE_THEME_TEXT_COLOR',color:v});
    };
    thInput.addEventListener('input', e=>{ clearTimeout(t2); const v=e.target.value.trim(); t2=setTimeout(()=>applyTextNow(v), 250); });
    thInput.addEventListener('change', e=>{ clearTimeout(t2); applyTextNow(e.target.value.trim()); });
  }
  $('#onboardApply').onclick=()=>{
    const v=(document.querySelector('#onboardHex')?.value.trim()) || accent;
    if(/^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$/.test(v)) setAccent(v);
    send({type:'UPDATE_ACCENT_COLOR',color:accent});
    localStorage.setItem('cc_onboarded','1');
    $('#onboarding').classList.add('hidden'); $('#onboarding').classList.remove('flex');
  };
  $('#onboarding').addEventListener('click',e=>{ if(e.target.id==='onboarding'){ $('#onboarding').classList.add('hidden'); $('#onboarding').classList.remove('flex'); localStorage.setItem('cc_onboarded','1'); }});
  $$('.sidebar-item, .tab-m').forEach(el=>el.addEventListener('click',()=>switchTab(el.getAttribute('data-tab'))));
  const doExport=()=>send({type:'EXPORT_CONFIG'});
  $('#exportBtn').onclick=doExport; $('#exportBtn2').onclick=doExport;
  const triggerImport=()=>$('#importFile').click();
  $('#importBtn').onclick=triggerImport; $('#importBtn2').onclick=triggerImport;
  $('#importFile').addEventListener('change',async e=>{
    const f=e.target.files[0]; if(!f) return; const text=await f.text();
    try{ const data=JSON.parse(text); send({type:'IMPORT_CONFIG',data}); }catch(err){ alert('Invalid JSON'); }
    e.target.value='';
  });
  window.addEventListener('pointerup',()=> isDraggingSlider=false);
  window.addEventListener('pointercancel',()=> isDraggingSlider=false);

  // HUD editor X/Y inputs — target selected item, fallback to first enabled
  function getEditorTargetId(){
    if(editorSelectedId && config.modules[editorSelectedId]) return editorSelectedId;
    const enabled=Object.keys(MODULES_META).find(id=>config.modules[id] && config.modules[id].enabled);
    return enabled || Object.keys(MODULES_META)[0];
  }
  const ex=document.querySelector('#editorX'), ey=document.querySelector('#editorY');
  if(ex){
    const h=()=>{
      const targetId=getEditorTargetId(); if(!targetId) return;
      const v=parseInt(ex.value)||0;
      const mod=config.modules[targetId]=config.modules[targetId]||{x:10,y:10,scale:1,opacity:1,enabled:false};
      mod.x=v; send({type:'UPDATE_MODULE', id:targetId, data:{x:v}}); syncEditorItem();
    };
    ex.addEventListener('change', h);
    let t; ex.addEventListener('input', ()=>{ clearTimeout(t); t=setTimeout(h, 300); });
  }
  if(ey){
    const h=()=>{
      const targetId=getEditorTargetId(); if(!targetId) return;
      const v=parseInt(ey.value)||0;
      const mod=config.modules[targetId]=config.modules[targetId]||{x:10,y:10,scale:1,opacity:1,enabled:false};
      mod.y=v; send({type:'UPDATE_MODULE', id:targetId, data:{y:v}}); syncEditorItem();
    };
    ey.addEventListener('change', h);
    let t; ey.addEventListener('input', ()=>{ clearTimeout(t); t=setTimeout(h, 300); });
  }
  const exM=document.querySelector('#editorXMinus'), exP=document.querySelector('#editorXPlus'), eyM=document.querySelector('#editorYMinus'), eyP=document.querySelector('#editorYPlus');
  if(exM) exM.onclick=()=>{ const inp=document.querySelector('#editorX'); if(!inp) return; inp.value=parseInt(inp.value||0)-1; inp.dispatchEvent(new Event('change',{bubbles:true})); };
  if(exP) exP.onclick=()=>{ const inp=document.querySelector('#editorX'); if(!inp) return; inp.value=parseInt(inp.value||0)+1; inp.dispatchEvent(new Event('change',{bubbles:true})); };
  if(eyM) eyM.onclick=()=>{ const inp=document.querySelector('#editorY'); if(!inp) return; inp.value=parseInt(inp.value||0)-1; inp.dispatchEvent(new Event('change',{bubbles:true})); };
  if(eyP) eyP.onclick=()=>{ const inp=document.querySelector('#editorY'); if(!inp) return; inp.value=parseInt(inp.value||0)+1; inp.dispatchEvent(new Event('change',{bubbles:true})); };
  const centerBtn=document.querySelector('#editorCenterBtn');
  if(centerBtn) centerBtn.onclick=()=>{
    const targetId=getEditorTargetId(); if(!targetId) return;
    const nx=Math.max(0, Math.round((windowSize.scaledWidth||640)/2)-20);
    const ny=Math.max(0, Math.round((windowSize.scaledHeight||360)/2)-10);
    const mod=config.modules[targetId]=config.modules[targetId]||{x:10,y:10,scale:1,opacity:1,enabled:false};
    mod.x=nx; mod.y=ny;
    send({type:'UPDATE_MODULE', id:targetId, data:{x:nx, y:ny}}); syncEditorItem();
  };
  const resetBtn=document.querySelector('#editorResetBtn');
  if(resetBtn) resetBtn.onclick=()=>{
    const targetId=getEditorTargetId(); if(!targetId) return;
    const mod=config.modules[targetId]=config.modules[targetId]||{x:10,y:10,scale:1,opacity:1,enabled:false};
    mod.x=10; mod.y=10;
    send({type:'UPDATE_MODULE', id:targetId, data:{x:10, y:10}}); syncEditorItem();
  };
  setupEditorDrag();
  const searchEl=document.querySelector('#moduleSearch');
  if(searchEl){
    searchEl.addEventListener('input', e=>{ searchQuery=e.target.value.toLowerCase(); render(false); });
  }
  connect();
  config.modules=Object.fromEntries(Object.keys(MODULES_META).map(k=>[k,{enabled:false,x:10,y:10,scale:1,opacity:1}]));
  render(true);
  setTimeout(syncEditorItem, 200);
});
function downloadJson(obj,name){
  const blob=new Blob([JSON.stringify(obj,null,2)],{type:'application/json'});
  const a=document.createElement('a'); a.href=URL.createObjectURL(blob); a.download=name; a.click(); setTimeout(()=>URL.revokeObjectURL(a.href),1000);
}