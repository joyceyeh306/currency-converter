(() => {
  'use strict';

  const ratioSpecs = {
    square:      { ar: 1,    w: 2400, h: 2400, label: '1:1' },
    landscape43: { ar: 4/3,  w: 2880, h: 2160, label: '4:3' },
    portrait34:  { ar: 3/4,  w: 2160, h: 2880, label: '3:4' },
    landscape:   { ar: 16/9, w: 2880, h: 1620, label: '16:9' },
    portrait:    { ar: 9/16, w: 1620, h: 2880, label: '9:16' }
  };

  state.dateAlign = state.dateAlign || 'center';
  state.editorZoom = 1;
  state.activeBoundary = null;

  const stageShell = document.getElementById('stageShell');
  const focusOpenBtn = document.getElementById('focusOpen');
  focusOpenBtn.textContent = '放大編輯區';

  let replaceIndex = -1;
  const replaceInput = document.createElement('input');
  replaceInput.type = 'file';
  replaceInput.accept = 'image/*';
  replaceInput.id = 'replaceSinglePhoto';
  replaceInput.style.display = 'none';
  document.body.appendChild(replaceInput);

  async function replaceSelectedPhoto() {
    if (!state.photos.length) return;
    replaceIndex = state.selected;
    replaceInput.click();
  }
  replaceInput.addEventListener('change', async e => {
    const file = [...e.target.files].find(f => f.type.startsWith('image/'));
    if (!file || replaceIndex < 0 || replaceIndex >= state.photos.length) {
      replaceInput.value = '';
      replaceIndex = -1;
      return;
    }
    try {
      const np = await fileToPhoto(file);
      state.photos[replaceIndex] = np;
      state.selected = replaceIndex;
      renderEditor();
      renderOrder();
      renderDates();
      updatePhotoAdjustUI();
    } catch (err) {
      alert('這張照片無法讀取，請重新選擇。');
    }
    replaceInput.value = '';
    replaceIndex = -1;
  });

  const resetPanel = document.getElementById('panel-reset');
  resetPanel.innerHTML = `
    <div id="selectedPhotoInfo" class="selected-photo-info">目前：第 1 張</div>
    <div class="photo-adjust-row">
      <span>照片大小</span>
      <button id="photoScaleMinus" class="secondary">－</button>
      <b id="photoScaleLabel">100%</b>
      <button id="photoScalePlus" class="secondary">＋</button>
    </div>
    <div class="photo-adjust-actions">
      <button id="replaceCurrentPhoto" class="primary">更換目前照片</button>
      <button id="resetPhoto" class="secondary">重設目前照片</button>
      <button id="resetBoundaries" class="secondary">重設邊界</button>
    </div>
    <div class="help-text">「更換目前照片」只換這一格；其他照片、版型、順序與邊界都保留，新照片會重新讀取拍攝日期。</div>`;

  function updatePhotoAdjustUI() {
    const p = state.photos[state.selected];
    if (!p) return;
    const info = document.getElementById('selectedPhotoInfo');
    const label = document.getElementById('photoScaleLabel');
    if (info) info.textContent = `目前：第 ${state.selected + 1} 張`;
    if (label) label.textContent = `${Math.round(p.scale * 100)}%`;
  }
  function changeSelectedScale(mult) {
    const p = state.photos[state.selected];
    if (!p) return;
    p.scale = clamp(p.scale * mult, .45, 8);
    updatePieces();
    updatePhotoAdjustUI();
  }
  document.getElementById('photoScaleMinus').onclick = () => changeSelectedScale(.90);
  document.getElementById('photoScalePlus').onclick = () => changeSelectedScale(1.10);
  document.getElementById('replaceCurrentPhoto').onclick = replaceSelectedPhoto;
  document.getElementById('resetPhoto').onclick = () => {
    const p = state.photos[state.selected]; if (!p) return;
    p.panX = 0; p.panY = 0; p.scale = 1;
    renderEditor(); updatePhotoAdjustUI();
  };
  document.getElementById('resetBoundaries').onclick = () => { resetBoundaries(); renderEditor(); };

  const ratioPanel = document.getElementById('panel-ratio');
  ratioPanel.innerHTML = `
    <div class="ratio-row v123-ratios">
      <button class="pill" data-ratio-v123="square">1:1</button>
      <button class="pill" data-ratio-v123="landscape43">4:3</button>
      <button class="pill" data-ratio-v123="portrait34">3:4</button>
      <button class="pill" data-ratio-v123="landscape">16:9</button>
      <button class="pill" data-ratio-v123="portrait">9:16</button>
    </div>
    <div class="frame-row v123-frame-row"><span class="small-label">框線</span>
      <button class="pill" data-frame-v123="0">無</button>
      <button class="pill" data-frame-v123="3">細</button>
      <button class="pill" data-frame-v123="7">中</button>
      <button class="pill" data-frame-v123="12">粗</button>
    </div>
    <div class="help-text">比例只控制整張拼圖；每張照片維持原始比例。框線只會出現在輸出成品，編輯時不再用白線遮住照片。</div>`;

  function refreshRatioButtons() {
    document.querySelectorAll('[data-ratio-v123]').forEach(b => b.classList.toggle('active', b.dataset.ratioV123 === state.ratio));
    document.querySelectorAll('[data-frame-v123]').forEach(b => b.classList.toggle('active', Number(b.dataset.frameV123) === state.frameWidth));
  }
  document.querySelectorAll('[data-ratio-v123]').forEach(b => b.onclick = () => {
    state.ratio = b.dataset.ratioV123;
    refreshRatioButtons();
    requestAnimationFrame(() => { fitStage(); renderEditor(); });
  });
  document.querySelectorAll('[data-frame-v123]').forEach(b => b.onclick = () => {
    state.frameWidth = Number(b.dataset.frameV123);
    refreshRatioButtons();
  });

  const datesPanel = document.getElementById('panel-dates');
  const dateGrid = document.getElementById('dateGrid');
  const alignRow = document.createElement('div');
  alignRow.className = 'date-align-row';
  alignRow.innerHTML = `<span class="small-label">日期位置</span>
    <button class="pill" data-date-align-v123="left">靠左</button>
    <button class="pill" data-date-align-v123="center">置中</button>
    <button class="pill" data-date-align-v123="right">靠右</button>`;
  datesPanel.insertBefore(alignRow, dateGrid);
  document.querySelectorAll('[data-date-align-v123]').forEach(b => b.onclick = () => {
    state.dateAlign = b.dataset.dateAlignV123;
    renderDates();
    renderEditor();
  });

  const oldRenderDates = renderDates;
  renderDates = function() {
    oldRenderDates();
    document.querySelectorAll('[data-date-align-v123]').forEach(b => b.classList.toggle('active', b.dataset.dateAlignV123 === state.dateAlign));
  };

  const wholeFocus = document.createElement('div');
  wholeFocus.id = 'wholeCanvasFocus';
  wholeFocus.className = 'whole-canvas-focus';
  wholeFocus.innerHTML = `
    <div class="whole-focus-header">
      <button id="wholeFocusClose">← 回拼圖</button>
      <strong id="wholeFocusTitle">放大編輯區 100%</strong>
      <button id="wholeFocusFit">全覽</button>
    </div>
    <div id="wholeFocusViewport" class="whole-focus-viewport"><div id="wholeFocusHolder" class="whole-focus-holder"></div></div>
    <div class="whole-focus-tools">
      <div class="whole-focus-row">
        <button id="wholeCanvasMode" class="secondary whole-mode">移動畫布</button>
        <button id="wholePhotoMode" class="secondary whole-mode active">調照片</button>
        <button id="wholeBoundaryMode" class="secondary whole-mode">調邊界</button>
      </div>
      <div class="whole-focus-row">
        <span>編輯區</span><button id="wholeZoomMinus" class="secondary">－</button><b id="wholeZoomLabel">100%</b><button id="wholeZoomPlus" class="secondary">＋</button>
        <span>照片大小</span><button id="wholePhotoMinus" class="secondary">－</button><button id="wholePhotoPlus" class="secondary">＋</button>
        <button id="wholeReplacePhoto" class="primary">更換照片</button>
      </div>
    </div>`;
  document.body.appendChild(wholeFocus);
  const wholeViewport = document.getElementById('wholeFocusViewport');
  const wholeHolder = document.getElementById('wholeFocusHolder');
  function inWholeFocus() { return wholeFocus.classList.contains('active'); }
  function fitIn(w0, h0, ar) {
    let w = Math.max(1, w0), h = w / ar;
    if (h > h0) { h = Math.max(1, h0); w = h * ar; }
    return {w,h};
  }

  fitStage = function() {
    const spec = ratioSpecs[state.ratio] || ratioSpecs.landscape;
    if (inWholeFocus()) {
      const pad = 18;
      const base = fitIn(Math.max(1, wholeViewport.clientWidth - pad*2), Math.max(1, wholeViewport.clientHeight - pad*2), spec.ar);
      const w = Math.floor(base.w * state.editorZoom), h = Math.floor(base.h * state.editorZoom);
      const hw = Math.max(wholeViewport.clientWidth, w + pad*2), hh = Math.max(wholeViewport.clientHeight, h + pad*2);
      wholeHolder.style.width = `${hw}px`; wholeHolder.style.height = `${hh}px`;
      stage.style.position = 'absolute'; stage.style.width = `${w}px`; stage.style.height = `${h}px`;
      stage.style.left = `${Math.floor((hw-w)/2)}px`; stage.style.top = `${Math.floor((hh-h)/2)}px`;
    } else {
      const base = fitIn(stageShell.clientWidth, stageShell.clientHeight, spec.ar);
      stage.style.position = 'relative'; stage.style.left = 'auto'; stage.style.top = 'auto';
      stage.style.width = `${Math.floor(base.w)}px`; stage.style.height = `${Math.floor(base.h)}px`;
    }
  };

  function syncWholeModeButtons() {
    document.getElementById('wholeCanvasMode').classList.toggle('active', state.mode === 'canvas');
    document.getElementById('wholePhotoMode').classList.toggle('active', state.mode === 'photo');
    document.getElementById('wholeBoundaryMode').classList.toggle('active', state.mode === 'boundary');
    photoModeBtn.classList.toggle('active', state.mode === 'photo');
    boundaryModeBtn.classList.toggle('active', state.mode === 'boundary');
  }
  setMode = function(mode) {
    state.mode = mode;
    syncWholeModeButtons();
    renderEditor();
  };
  photoModeBtn.onclick = () => setMode('photo');
  boundaryModeBtn.onclick = () => setMode('boundary');
  document.getElementById('wholeCanvasMode').onclick = () => setMode('canvas');
  document.getElementById('wholePhotoMode').onclick = () => setMode('photo');
  document.getElementById('wholeBoundaryMode').onclick = () => setMode('boundary');

  function updateWholeZoomText() {
    const t = `${Math.round(state.editorZoom*100)}%`;
    document.getElementById('wholeZoomLabel').textContent = t;
    document.getElementById('wholeFocusTitle').textContent = `放大編輯區 ${t}`;
  }
  function centerWholeOnSelected() {
    if (!inWholeFocus() || !state.photos.length) return;
    const bb = bbox(polygons()[state.selected]);
    const left = parseFloat(stage.style.left)||0, top=parseFloat(stage.style.top)||0;
    const x = left + (bb.x + bb.w/2)*stage.clientWidth;
    const y = top + (bb.y + bb.h/2)*stage.clientHeight;
    wholeViewport.scrollLeft = Math.max(0, x - wholeViewport.clientWidth/2);
    wholeViewport.scrollTop = Math.max(0, y - wholeViewport.clientHeight/2);
  }
  function setWholeZoom(v, center=true) {
    state.editorZoom = clamp(Math.round(v*100)/100, 1, 2.5);
    updateWholeZoomText(); fitStage(); renderEditor();
    if (center) requestAnimationFrame(centerWholeOnSelected);
  }
  function openWholeFocus() {
    if (!state.photos.length) return;
    wholeFocus.classList.add('active'); wholeHolder.appendChild(stage); state.editorZoom=1;
    if (state.mode === 'canvas') state.mode='photo';
    syncWholeModeButtons(); updateWholeZoomText();
    requestAnimationFrame(() => { fitStage(); renderEditor(); });
  }
  function closeWholeFocus() {
    stageShell.insertBefore(stage, focusOpenBtn);
    wholeFocus.classList.remove('active');
    if (state.mode === 'canvas') state.mode='photo';
    syncWholeModeButtons();
    requestAnimationFrame(() => { fitStage(); renderEditor(); });
  }
  focusOpenBtn.onclick = openWholeFocus;
  document.getElementById('wholeFocusClose').onclick = closeWholeFocus;
  document.getElementById('wholeFocusFit').onclick = () => setWholeZoom(1, false);
  document.getElementById('wholeZoomMinus').onclick = () => setWholeZoom(state.editorZoom-.25);
  document.getElementById('wholeZoomPlus').onclick = () => setWholeZoom(state.editorZoom+.25);
  document.getElementById('wholePhotoMinus').onclick = () => changeSelectedScale(.90);
  document.getElementById('wholePhotoPlus').onclick = () => changeSelectedScale(1.10);
  document.getElementById('wholeReplacePhoto').onclick = replaceSelectedPhoto;

  let canvasDrag=null;
  stage.addEventListener('pointerdown', e => {
    if (state.mode !== 'canvas' || !inWholeFocus()) return;
    canvasDrag={x:e.clientX,y:e.clientY,l:wholeViewport.scrollLeft,t:wholeViewport.scrollTop};
    stage.setPointerCapture?.(e.pointerId); e.preventDefault();
  }, {passive:false});
  stage.addEventListener('pointermove', e => {
    if (!canvasDrag || state.mode !== 'canvas') return;
    wholeViewport.scrollLeft = canvasDrag.l - (e.clientX-canvasDrag.x);
    wholeViewport.scrollTop = canvasDrag.t - (e.clientY-canvasDrag.y);
    e.preventDefault();
  }, {passive:false});
  const endCanvasDrag=()=>canvasDrag=null;
  stage.addEventListener('pointerup',endCanvasDrag); stage.addEventListener('pointercancel',endCanvasDrag);

  attachPhotoGestures = function(el,index) {
    let active=false,last=null;
    el.addEventListener('pointerdown',e=>{
      if(state.mode!=='photo')return;
      selectPhoto(index); active=true; last={x:e.clientX,y:e.clientY};
      el.setPointerCapture?.(e.pointerId); e.preventDefault();
    },{passive:false});
    el.addEventListener('pointermove',e=>{
      if(state.mode!=='photo'||!active||!last)return;
      const p=state.photos[index],bb=bbox(polygons()[index]);
      p.panX=clamp(p.panX+(e.clientX-last.x)/Math.max(60,bb.w*stage.clientWidth),-1.5,1.5);
      p.panY=clamp(p.panY+(e.clientY-last.y)/Math.max(60,bb.h*stage.clientHeight),-1.5,1.5);
      last={x:e.clientX,y:e.clientY}; updatePieces(); e.preventDefault();
    },{passive:false});
    const end=()=>{active=false;last=null;}; el.addEventListener('pointerup',end);el.addEventListener('pointercancel',end);
    el.addEventListener('wheel',e=>{if(state.mode!=='photo')return;e.preventDefault();selectPhoto(index);changeSelectedScale(e.deltaY<0?1.07:.93);},{passive:false});
  };

  const oldUpdatePieces = updatePieces;
  updatePieces = function() {
    oldUpdatePieces();
    const ps=polygons(),sw=stage.clientWidth,sh=stage.clientHeight;
    ps.forEach((poly,i)=>{
      const piece=stage.querySelector(`.piece[data-index="${i}"]`),d=piece?.querySelector('.date-badge');
      if(!d)return; const bb=bbox(poly),pad=10;
      d.style.top=`${Math.max(0,(bb.y+bb.h)*sh-42)}px`;d.style.bottom='auto';d.style.right='auto';d.style.transform='none';
      if(state.dateAlign==='left')d.style.left=`${bb.x*sw+pad}px`;
      else if(state.dateAlign==='right'){d.style.left='auto';d.style.right=`${(1-bb.x-bb.w)*sw+pad}px`;}
      else{d.style.left=`${(bb.x+bb.w/2)*sw}px`;d.style.transform='translateX(-50%)';}
    });
    updatePhotoAdjustUI();
  };

  renderFrameOverlay = function() {};

  function segments() {
    ensureBoundaries(); const b=state.boundaries,s=[];
    if(b.type==='row')b.lines.forEach((ln,i)=>s.push({key:`row:${i}`,kind:'row',i,p1:[ln.a,0],p2:[ln.b,1]}));
    else if(b.type==='col')b.lines.forEach((ln,i)=>s.push({key:`col:${i}`,kind:'col',i,p1:[0,ln.a],p2:[1,ln.b]}));
    else if(b.type==='grid'){s.push({key:'grid:x',kind:'gridX',p1:[b.x,0],p2:[b.x,1]},{key:'grid:y',kind:'gridY',p1:[0,b.y],p2:[1,b.y]});}
    else if(b.type==='leftBig'){s.push({key:'left:main',kind:'leftMain',p1:[b.v.a,0],p2:[b.v.b,1]});b.ys.forEach((y,i)=>s.push({key:`left:sub:${i}`,kind:'leftSub',i,p1:[lerp(b.v.a,b.v.b,y),y],p2:[1,y]}));}
    else if(b.type==='topBig'){s.push({key:'top:main',kind:'topMain',p1:[0,b.h.a],p2:[1,b.h.b]});b.xs.forEach((x,i)=>s.push({key:`top:sub:${i}`,kind:'topSub',i,p1:[x,lerp(b.h.a,b.h.b,x)],p2:[x,1]}));}
    return s;
  }
  function ensureActive(){const ss=segments();if(!ss.some(s=>s.key===state.activeBoundary))state.activeBoundary=ss[0]?.key||null;}
  function distToSegment(x,y,s){const sw=stage.clientWidth,sh=stage.clientHeight,px=x*sw,py=y*sh,x1=s.p1[0]*sw,y1=s.p1[1]*sh,x2=s.p2[0]*sw,y2=s.p2[1]*sh,dx=x2-x1,dy=y2-y1,l2=dx*dx+dy*dy;if(!l2)return Math.hypot(px-x1,py-y1);const t=clamp(((px-x1)*dx+(py-y1)*dy)/l2,0,1);return Math.hypot(px-(x1+t*dx),py-(y1+t*dy));}
  stage.addEventListener('pointerdown',e=>{
    if(state.mode!=='boundary'||e.target.closest?.('.handle'))return;
    const r=stage.getBoundingClientRect(),x=clamp((e.clientX-r.left)/r.width,0,1),y=clamp((e.clientY-r.top)/r.height,0,1),ss=segments();
    if(!ss.length)return; ss.sort((a,b)=>distToSegment(x,y,a)-distToSegment(x,y,b)); state.activeBoundary=ss[0].key; renderEditor(); e.preventDefault();
  },{passive:false});

  function smallHandle(layer,mid=false){const h=document.createElement('div');h.className='handle'+(mid?' mid':'');layer.appendChild(h);return h;}
  function hpos(h,x,y){h.style.left=`${x*100}%`;h.style.top=`${y*100}%`;}
  function hdrag(h,fn){let active=false;h.addEventListener('pointerdown',e=>{active=true;h.setPointerCapture?.(e.pointerId);e.stopPropagation();e.preventDefault();});h.addEventListener('pointermove',e=>{if(!active)return;const r=stage.getBoundingClientRect();fn(clamp((e.clientX-r.left)/r.width,0,1),clamp((e.clientY-r.top)/r.height,0,1));e.preventDefault();});const end=()=>active=false;h.addEventListener('pointerup',end);h.addEventListener('pointercancel',end);}
  renderBoundaryLayer = function(){
    if(state.mode!=='boundary')return; ensureActive(); const b=state.boundaries,layer=document.createElement('div');layer.className='boundary-layer';
    const svg=document.createElementNS('http://www.w3.org/2000/svg','svg');svg.setAttribute('class','boundary-svg');layer.appendChild(svg);
    const hint=document.createElement('div');hint.className='boundary-hint';hint.textContent='點分隔線選擇；拖中間＝整條移動，拖端點＝調斜度';layer.appendChild(hint);stage.appendChild(layer);
    const lineMap=new Map();for(const s of segments()){const l=document.createElementNS('http://www.w3.org/2000/svg','line');l.setAttribute('class','boundary-line'+(s.key===state.activeBoundary?' active':''));svg.appendChild(l);lineMap.set(s.key,l);}
    const active=segments().find(s=>s.key===state.activeBoundary);if(!active)return;let h1=null,h2=null,hm=null;
    if(['row','col','leftMain','topMain'].includes(active.kind)){h1=smallHandle(layer);h2=smallHandle(layer);hm=smallHandle(layer,true);}else hm=smallHandle(layer,true);
    const refresh=()=>{const ss=segments();for(const s of ss){const l=lineMap.get(s.key);if(l){l.setAttribute('x1',s.p1[0]*100+'%');l.setAttribute('y1',s.p1[1]*100+'%');l.setAttribute('x2',s.p2[0]*100+'%');l.setAttribute('y2',s.p2[1]*100+'%');l.setAttribute('class','boundary-line'+(s.key===state.activeBoundary?' active':''));}}const a=ss.find(s=>s.key===state.activeBoundary);if(!a)return;if(hm)hpos(hm,(a.p1[0]+a.p2[0])/2,(a.p1[1]+a.p2[1])/2);if(h1){if(a.kind==='row'||a.kind==='leftMain')hpos(h1,a.p1[0],.04);else hpos(h1,.04,a.p1[1]);}if(h2){if(a.kind==='row'||a.kind==='leftMain')hpos(h2,a.p2[0],.96);else hpos(h2,.96,a.p2[1]);}updatePieces();};
    if(active.kind==='row'){const ln=b.lines[active.i];hdrag(h1,x=>{ln.a=x;constrainRowLine(active.i);refresh();});hdrag(h2,x=>{ln.b=x;constrainRowLine(active.i);refresh();});hdrag(hm,x=>{const m=(ln.a+ln.b)/2,d=x-m;ln.a+=d;ln.b+=d;constrainRowLine(active.i);refresh();});}
    else if(active.kind==='col'){const ln=b.lines[active.i];hdrag(h1,(x,y)=>{ln.a=y;constrainColLine(active.i);refresh();});hdrag(h2,(x,y)=>{ln.b=y;constrainColLine(active.i);refresh();});hdrag(hm,(x,y)=>{const m=(ln.a+ln.b)/2,d=y-m;ln.a+=d;ln.b+=d;constrainColLine(active.i);refresh();});}
    else if(active.kind==='gridX')hdrag(hm,x=>{b.x=clamp(x,.18,.82);refresh();});
    else if(active.kind==='gridY')hdrag(hm,(x,y)=>{b.y=clamp(y,.18,.82);refresh();});
    else if(active.kind==='leftMain'){const v=b.v;hdrag(h1,x=>{v.a=clamp(x,.18,.84);refresh();});hdrag(h2,x=>{v.b=clamp(x,.18,.84);refresh();});hdrag(hm,x=>{const m=(v.a+v.b)/2,d=x-m;v.a=clamp(v.a+d,.18,.84);v.b=clamp(v.b+d,.18,.84);refresh();});}
    else if(active.kind==='leftSub'){const i=active.i;hdrag(hm,(x,y)=>{const lo=i===0?.12:b.ys[i-1]+.10,hi=i===b.ys.length-1?.88:b.ys[i+1]-.10;b.ys[i]=clamp(y,lo,hi);refresh();});}
    else if(active.kind==='topMain'){const h=b.h;hdrag(h1,(x,y)=>{h.a=clamp(y,.18,.84);refresh();});hdrag(h2,(x,y)=>{h.b=clamp(y,.18,.84);refresh();});hdrag(hm,(x,y)=>{const m=(h.a+h.b)/2,d=y-m;h.a=clamp(h.a+d,.18,.84);h.b=clamp(h.b+d,.18,.84);refresh();});}
    else if(active.kind==='topSub'){const i=active.i;hdrag(hm,(x,y)=>{const lo=i===0?.12:b.xs[i-1]+.10,hi=i===b.xs.length-1?.88:b.xs[i+1]-.10;b.xs[i]=clamp(x,lo,hi);refresh();});}
    refresh();
  };

  const oldResetBoundaries = resetBoundaries;
  resetBoundaries = function(){oldResetBoundaries();state.activeBoundary=null;};

  const oldSelectPhoto = selectPhoto;
  selectPhoto = function(index){oldSelectPhoto(index);updatePhotoAdjustUI();};

  function drawDatePosition(bb,W,tw,pad){
    if(state.dateAlign==='left') return {x:bb.x*W+pad,align:'left',rx:bb.x*W+pad*.5};
    if(state.dateAlign==='right') return {x:(bb.x+bb.w)*W-pad,align:'right',rx:(bb.x+bb.w)*W-tw-pad*1.5};
    return {x:(bb.x+bb.w/2)*W,align:'center',rx:(bb.x+bb.w/2)*W-tw/2-pad};
  }
  buildCanvas = async function(){
    const spec=ratioSpecs[state.ratio]||ratioSpecs.landscape,W=spec.w,H=spec.h,note=document.getElementById('note').value.trim(),noteH=note?Math.round(H*.09):0,imageH=H-noteH;
    const c=document.createElement('canvas');c.width=W;c.height=H;const ctx=c.getContext('2d');ctx.fillStyle='#eaf2fb';ctx.fillRect(0,0,W,H);const ps=polygons();
    for(let i=0;i<state.photos.length;i++){
      const p=state.photos[i],poly=ps[i],bb=bbox(poly),img=await loadImg(p.src);ctx.save();clipPoly(ctx,poly,W,imageH);ctx.fillStyle='#eaf2fb';ctx.fillRect(0,0,W,imageH);
      const bw=bb.w*W,bh=bb.h*imageH,fit=Math.min(bw/p.w,bh/p.h),dw=p.w*fit*p.scale,dh=p.h*fit*p.scale,cx=(bb.x+bb.w/2+p.panX*bb.w)*W,cy=(bb.y+bb.h/2+p.panY*bb.h)*imageH;ctx.drawImage(img,cx-dw/2,cy-dh/2,dw,dh);
      if(state.showDates){const txt=fmtDate(p.date);if(txt){const size=Math.max(44,Math.round(Math.min(W,H)*.044));ctx.font=`800 ${size}px system-ui,sans-serif`;const tw=ctx.measureText(txt).width,pad=size*.40,pos=drawDatePosition(bb,W,tw,pad),y=(bb.y+bb.h)*imageH-size*.72;ctx.fillStyle='rgba(255,255,255,.84)';roundRect(ctx,pos.rx,y-size*.72,tw+pad*2,size*1.20,size*.30);ctx.fill();ctx.fillStyle='#17324f';ctx.textAlign=pos.align;ctx.textBaseline='middle';ctx.fillText(txt,pos.x,y-size*.12);}}ctx.restore();
    }
    if(state.frameWidth>0){const fw=state.frameWidth*(W/700);ctx.save();ctx.strokeStyle='#fff';ctx.lineJoin='round';ctx.lineWidth=fw;for(const poly of ps){ctx.beginPath();poly.forEach(([x,y],i)=>{const X=x*W,Y=y*imageH;i?ctx.lineTo(X,Y):ctx.moveTo(X,Y)});ctx.closePath();ctx.stroke();}ctx.restore();}
    if(noteH){ctx.fillStyle='#fff';ctx.fillRect(0,imageH,W,noteH);ctx.strokeStyle='#d4e0ed';ctx.lineWidth=2;ctx.beginPath();ctx.moveTo(0,imageH);ctx.lineTo(W,imageH);ctx.stroke();ctx.fillStyle='#17324f';ctx.textAlign='center';ctx.textBaseline='middle';ctx.font=`650 ${Math.round(noteH*.32)}px system-ui,sans-serif`;drawWrap(ctx,note,W/2,imageH+noteH/2,W*.9,noteH*.38,2);}return c;
  };

  const oldRenderExportPreview=renderExportPreview;
  renderExportPreview=async function(){await oldRenderExportPreview();const spec=ratioSpecs[state.ratio]||ratioSpecs.landscape;const help=document.querySelector('#exportPage .help-text');if(help)help.textContent=`PNG 為無損輸出；JPG 使用高畫質。${spec.label} 輸出 ${spec.w}×${spec.h}。`;};

  const oldRenderAllPanels = renderAllPanels;
  renderAllPanels = function(){oldRenderAllPanels();refreshRatioButtons();renderDates();updatePhotoAdjustUI();};
  refreshRatioButtons();renderDates();updatePhotoAdjustUI();syncWholeModeButtons();
})();
