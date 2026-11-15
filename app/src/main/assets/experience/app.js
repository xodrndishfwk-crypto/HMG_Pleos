/* StarLog storyboard UI. Native data arrives through window.StarLogNative (see StarLogExperienceActivity). */
(function () {
  'use strict';
  const $ = (id) => document.getElementById(id);
  const SVGNS = 'http://www.w3.org/2000/svg';
  const native = window.StarLogNative || null;

  // ------------------------------------------------------------------ native bridge
  let seq = 0;
  const pending = {};
  function call(method, args) {
    return new Promise((resolve) => {
      const id = 'r' + (++seq);
      pending[id] = resolve;
      if (native) native.call(id, method, JSON.stringify(args || {}));
      else setTimeout(() => window.StarLogUI.onReply(id, Mock.handle(method, args || {})), 120);
    });
  }
  window.StarLogUI = {
    onReply(id, payload) { const cb = pending[id]; delete pending[id]; if (cb) cb(payload); },
    onEvent(type, payload) { handleEvent(type, payload); },
  };

  // ------------------------------------------------------------------ helpers
  const ROMAN = { '대구 출발': 'DAEGU', '팔공산': 'PALGONGSAN', '구미': 'GUMI', '김천': 'GIMCHEON', '상주': 'SANGJU',
    '이화령 고개': 'IHWARYEONG', '문경 새재': 'MUNGYEONG', '대구': 'DAEGU', '경주': 'GYEONGJU', '울산': 'ULSAN', '부산': 'BUSAN', '김해': 'GIMHAE' };
  const NOTE = ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B'];
  const SOLFA = { C: '도', D: '레', E: '미', F: '파', G: '솔', A: '라', B: '시' };
  const noteName = (m) => NOTE[((m % 12) + 12) % 12] + (Math.floor(m / 12) - 1);
  const solfa = (m) => SOLFA[NOTE[((m % 12) + 12) % 12][0]] || '';
  const hz = (m) => Math.round(440 * Math.pow(2, (m - 69) / 12));
  const km = (v) => (Math.round(v * 10) / 10).toFixed(1);
  const pad2 = (n) => String(n).padStart(2, '0');
  function dateText(ms) { const d = new Date(ms); return `${d.getFullYear()}. ${pad2(d.getMonth() + 1)}. ${pad2(d.getDate())}`; }
  function haversine(a, b) {
    const r = Math.PI / 180, dLat = (b.lat - a.lat) * r, dLon = (b.lon - a.lon) * r;
    const h = Math.sin(dLat / 2) ** 2 + Math.cos(a.lat * r) * Math.cos(b.lat * r) * Math.sin(dLon / 2) ** 2;
    return 2 * 6371 * Math.asin(Math.sqrt(h));
  }
  function bearing(a, b) {
    const r = Math.PI / 180, y = Math.sin((b.lon - a.lon) * r) * Math.cos(b.lat * r);
    const x = Math.cos(a.lat * r) * Math.sin(b.lat * r) - Math.sin(a.lat * r) * Math.cos(b.lat * r) * Math.cos((b.lon - a.lon) * r);
    return (Math.atan2(y, x) / r + 360) % 360;
  }
  function compassWord(deg) { return ['북', '북동', '동', '남동', '남', '남서', '서', '북서'][Math.round(deg / 45) % 8]; }
  function compassCode(deg) { return ['N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW'][Math.round(deg / 45) % 8]; }
  /** Fits lat/lon into a box. stretch > 1 lets the horizontal scale grow up to that factor (sky-chart look). */
  function project(points, w, h, padX, padY, stretch) {
    const lat0 = points.reduce((s, p) => s + p.lat, 0) / Math.max(points.length, 1);
    const xs = points.map((p) => p.lon * Math.cos(lat0 * Math.PI / 180)), ys = points.map((p) => -p.lat);
    const minX = Math.min(...xs), maxX = Math.max(...xs), minY = Math.min(...ys), maxY = Math.max(...ys);
    const fx = (w - padX * 2) / Math.max(maxX - minX, 1e-6), fy = (h - padY * 2) / Math.max(maxY - minY, 1e-6);
    const s = Math.min(fx, fy);
    const sx = Math.min(fx, s * (stretch || 1)), sy = Math.min(fy, s * (stretch || 1));
    const ox = (w - (maxX - minX) * sx) / 2, oy = (h - (maxY - minY) * sy) / 2;
    return points.map((p, i) => ({ x: ox + (xs[i] - minX) * sx, y: oy + (ys[i] - minY) * sy }));
  }
  function el(tag, attrs, parent) {
    const n = document.createElementNS(SVGNS, tag);
    Object.entries(attrs || {}).forEach(([k, v]) => n.setAttribute(k, v));
    if (parent) parent.appendChild(n);
    return n;
  }
  function svgDefs(svg) {
    const defs = el('defs', {}, svg);
    const g = el('radialGradient', { id: svg.id + 'Glow' }, defs);
    el('stop', { offset: '0%', 'stop-color': '#fff7e2', 'stop-opacity': 1 }, g);
    el('stop', { offset: '35%', 'stop-color': '#e5c888', 'stop-opacity': .55 }, g);
    el('stop', { offset: '100%', 'stop-color': '#e5c888', 'stop-opacity': 0 }, g);
    return 'url(#' + svg.id + 'Glow)';
  }
  function star(svg, glow, x, y, r, bright) {
    const g = el('g', { class: 'twinkle', style: 'animation-delay:' + (Math.random() * 3).toFixed(2) + 's' }, svg);
    el('circle', { cx: x, cy: y, r: r * 4.5, fill: glow, opacity: bright ? 1 : .45 }, g);
    el('line', { x1: x - r * 5, y1: y, x2: x + r * 5, y2: y, stroke: '#fff3d6', 'stroke-width': .8, opacity: bright ? .8 : .3 }, g);
    el('line', { x1: x, y1: y - r * 3, x2: x, y2: y + r * 3, stroke: '#fff3d6', 'stroke-width': .8, opacity: bright ? .6 : .25 }, g);
    el('circle', { cx: x, cy: y, r: r, fill: bright ? '#fffaf0' : '#9aa3b5' }, g);
    return g;
  }
  function text(svg, x, y, value, opts) {
    const t = el('text', Object.assign({ x, y, fill: '#f4f1ea', 'font-size': 13, 'text-anchor': 'middle', 'font-family': 'Pretendard, Noto Sans CJK KR, sans-serif' }, opts || {}), svg);
    t.textContent = value;
    return t;
  }
  function backgroundStars(svg, w, h, count, seed) {
    let s = seed || 7;
    const rnd = () => (s = (s * 9301 + 49297) % 233280) / 233280;
    for (let i = 0; i < count; i++) el('circle', { cx: rnd() * w, cy: rnd() * h, r: rnd() * 1.1 + .2, fill: '#cfd6e6', opacity: (rnd() * .5 + .1).toFixed(2) }, svg);
  }
  let toastTimer = 0;
  function toast(msg) { const t = $('toast'); t.textContent = msg; t.classList.add('on'); clearTimeout(toastTimer); toastTimer = setTimeout(() => t.classList.remove('on'), 2600); }

  // ------------------------------------------------------------------ stage scale
  function fit() {
    const s = Math.min(window.innerWidth / 1700, window.innerHeight / 1184);
    const dx = (window.innerWidth - 1700 * s) / 2, dy = (window.innerHeight - 1184 * s) / 2;
    $('stage').style.transform = `translate(${dx}px, ${dy}px) scale(${s})`;
  }
  window.addEventListener('resize', fit);
  fit();

  // ------------------------------------------------------------------ app state
  let state = null;           // last native state
  let current = 'intro';
  let introDone = false;
  let forging = false;
  let melody = null;          // melody shown on the score
  let music = null;           // latest music job json
  let songPlaying = false;

  function journey() { return state && state.journey; }
  function stops() { return state ? state.route.stops : []; }

  function setBar(o) {
    $('barCap').textContent = o.cap || ''; $('barVal').textContent = o.val || '';
    const pill = $('barPill'); pill.textContent = o.pill || ''; pill.classList.toggle('act', !!o.onPill); pill.onclick = o.onPill || null;
    $('barT1').textContent = o.t1 || ''; $('barT2').textContent = o.t2 || '';
    $('barG').textContent = o.g || ''; $('barR2').textContent = o.r2 || '';
    $('barCompass').style.transform = `rotate(${o.heading || 0}deg)`;
  }

  function show(name) {
    current = name;
    ['intro', 'atlas', 'drive', 'memory', 'forge', 'score'].forEach((s) => $('s-' + s).classList.toggle('on', s === name));
    const chrome = !['intro', 'atlas'].includes(name);
    $('chrome').style.display = '';
    $('chrome').classList.toggle('ghost', !chrome);
    const tab = name === 'drive' ? 'drive' : name === 'memory' ? 'memory' : 'end';
    document.querySelectorAll('.tabs button').forEach((b) => b.classList.toggle('on', b.dataset.tab === tab));
    const subs = { drive: '3D CELESTIAL NAVIGATION', memory: 'SAVED CONSTELLATIONS', forge: 'TRIP SUMMARY · MELODY', score: 'TRIP SUMMARY · MELODY' };
    $('brandSub').textContent = subs[name] || '';
    $('brandTag').textContent = name === 'score' ? 'DEMO MOCK MUSIC' : 'DEMO REPLAY';
    if (name === 'drive') renderDrive(true);
    if (name === 'memory') renderMemory();
    if (name === 'score') renderScore();
  }

  document.querySelectorAll('.tabs button').forEach((b) => b.addEventListener('click', () => {
    const j = journey();
    if (b.dataset.tab === 'drive') { if (j && j.status === 'ACTIVE') show('drive'); else show('atlas'); }
    if (b.dataset.tab === 'memory') show('memory');
    if (b.dataset.tab === 'end') {
      if (j && j.status === 'COMPLETED') { show('score'); ensureMelody(); }
      else toast('여정을 종료하면 오늘의 별자리와 오르골 악보가 열립니다');
    }
  }));

  // ------------------------------------------------------------------ start / end
  $('atlasStart').addEventListener('click', startJourney);
  $('arriveEnd').addEventListener('click', endJourney);
  async function startJourney() {
    const r = await call('journey.start');
    if (!r.ok) { toast('여정을 시작할 수 없습니다'); return; }
    state = r.state; melody = null; music = null; car.pos = null;
    show('drive');
  }
  async function endJourney() {
    const r = await call('journey.end');
    state = r.state;
    if (r.ok && journey() && journey().status === 'COMPLETED') runForge();
    else toast(r.message || '아직 여정을 종료할 수 없습니다');
  }

  // ------------------------------------------------------------------ atlas
  function renderAtlas() {
    const s = stops();
    if (!s.length) return;
    const j = journey();
    $('atlasT1').textContent = `DEMO REPLAY · ${s[0].label} → ${s[s.length - 1].label}`;
    if (j && j.status === 'ACTIVE') { $('atlasStart').textContent = '▶ 주행 이어보기'; $('atlasT2').textContent = `기록 ${j.points.length} / ${j.totalStops} · 진행 중인 여정을 이어서 표시합니다`; }
    else { $('atlasStart').textContent = '▶ 여정 시작'; $('atlasT2').textContent = `시연용 재생 데이터로 ${s.length}개 경유 별을 기록합니다`; }
  }

  // ------------------------------------------------------------------ drive (map + overlay)
  let map = null, mapReady = false;
  const car = { pos: null, from: null, to: null, t0: 0, dur: 1, heading: 0 };
  function initMap() {
    if (map || !window.maplibregl) { if (!window.maplibregl) $('map').classList.add('fallback'); return; }
    const fallbackTimer = setTimeout(() => { if (!mapReady) $('map').classList.add('fallback'); }, 9000);
    try {
      map = new maplibregl.Map({ container: 'map', style: 'https://tiles.openfreemap.org/styles/liberty', center: [128.63, 35.88], zoom: 10.4, pitch: 66, bearing: 0, interactive: false, attributionControl: false, fadeDuration: 0 });
    } catch (e) { $('map').classList.add('fallback'); return; }
    map.on('error', () => { if (!mapReady) $('map').classList.add('fallback'); });
    map.on('load', () => {
      mapReady = true; clearTimeout(fallbackTimer); $('map').classList.remove('fallback');
      night(map);
      setTimeout(() => $('map').classList.add('styled'), 150);
      map.addSource('route', { type: 'geojson', data: routeGeo(), lineMetrics: true });
      map.addLayer({ id: 'route-glow', type: 'line', source: 'route', paint: { 'line-color': '#e5c888', 'line-width': 16, 'line-blur': 12, 'line-opacity': .35 } });
      map.addLayer({ id: 'route-core', type: 'line', source: 'route', paint: { 'line-color': '#ffe6b0', 'line-width': 4 } });
      map.addSource('stops', { type: 'geojson', data: stopsGeo() });
      map.addLayer({ id: 'stops-glow', type: 'circle', source: 'stops', paint: { 'circle-radius': 14, 'circle-color': '#e5c888', 'circle-blur': 1, 'circle-opacity': ['case', ['get', 'done'], .6, .15] } });
      map.addLayer({ id: 'stops-dot', type: 'circle', source: 'stops', paint: { 'circle-radius': 4, 'circle-color': ['case', ['get', 'done'], '#fff6df', '#7b8496'] } });
      map.addLayer({ id: 'stops-label', type: 'symbol', source: 'stops', layout: { 'text-field': ['get', 'label'], 'text-size': 15, 'text-offset': [0, -1.6], 'text-font': ['Noto Sans Regular'] }, paint: { 'text-color': '#e5c888', 'text-halo-color': '#05060a', 'text-halo-width': 2 } });
    });
  }
  function night(m) {
    m.getStyle().layers.forEach((l) => {
      try {
        if (l.type === 'background') m.setPaintProperty(l.id, 'background-color', '#0a0f1a');
        else if (l.type === 'fill') m.setPaintProperty(l.id, 'fill-color', /water/.test(l.id) ? '#0b1626' : '#0e1524');
        else if (l.type === 'line') m.setPaintProperty(l.id, 'line-color', /water|river/.test(l.id) ? '#10223a' : /motorway|trunk|primary/.test(l.id) ? '#3a4f72' : '#24324c');
        else if (l.type === 'fill-extrusion') { m.setPaintProperty(l.id, 'fill-extrusion-color', '#1a2438'); m.setPaintProperty(l.id, 'fill-extrusion-opacity', .85); }
        else if (l.type === 'symbol') {
          // Keep only city / town names so the wide 3D view stays as clean as the storyboard.
          if (!/place.*(city|town)|label_city|label_town/.test(l.id)) { m.setLayoutProperty(l.id, 'visibility', 'none'); return; }
          m.setPaintProperty(l.id, 'text-color', '#7f8aa0'); m.setPaintProperty(l.id, 'text-halo-color', '#05060a');
        }
      } catch (e) { /* layer without that paint property */ }
    });
  }
  function routeGeo() { return { type: 'Feature', geometry: { type: 'LineString', coordinates: stops().map((s) => [s.lon, s.lat]) } }; }
  function stopsGeo() {
    const n = journey() ? journey().points.length : 0;
    return { type: 'FeatureCollection', features: stops().map((s, i) => ({ type: 'Feature', properties: { label: s.label, done: i < n }, geometry: { type: 'Point', coordinates: [s.lon, s.lat] } })) };
  }
  function lerp(a, b, t) { return { lat: a.lat + (b.lat - a.lat) * t, lon: a.lon + (b.lon - a.lon) * t }; }
  function retarget() {
    const s = stops(); if (!s.length) return;
    const n = journey() ? journey().points.length : 0;
    const target = s[Math.min(n, s.length - 1)];
    if (!car.pos) car.pos = { lat: s[0].lat - 0.06, lon: s[0].lon + 0.02 };
    car.from = { ...car.pos }; car.to = { lat: target.lat, lon: target.lon };
    car.t0 = performance.now(); car.dur = (state && state.tickMs ? state.tickMs : 2600) * 0.96;
  }
  function frame(now) {
    requestAnimationFrame(frame);
    if (current !== 'drive' || !car.from) return;
    const t = Math.min(1, (now - car.t0) / car.dur);
    const e = t < .5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
    car.pos = lerp(car.from, car.to, e);
    if (haversine(car.pos, car.to) > 0.05) {
      const want = bearing(car.pos, car.to);
      let d = ((want - car.heading + 540) % 360) - 180;
      car.heading = (car.heading + d * 0.08 + 360) % 360;
    }
    if (mapReady) map.jumpTo({ center: [car.pos.lon, car.pos.lat], bearing: car.heading, pitch: 66, zoom: 10.6, padding: { top: 420, bottom: 0, left: 0, right: 0 } });
    if (Math.floor(now / 250) !== frame.last) { frame.last = Math.floor(now / 250); drawSky(); driveBar(); }
  }
  requestAnimationFrame(frame);

  function renderDrive(reset) {
    initMap();
    if (reset || !car.from) retarget();
    if (mapReady) { map.getSource('stops').setData(stopsGeo()); map.getSource('route').setData(routeGeo()); }
    const j = journey(); const s = stops();
    $('arrive').classList.toggle('on', !!(j && j.status === 'ACTIVE' && j.points.length >= s.length));
    drawSky(); driveBar();
  }
  function drawSky() {
    const svg = $('skySvg'); svg.innerHTML = ''; const glow = svgDefs(svg);
    const s = stops(); if (!s.length) return;
    const n = journey() ? journey().points.length : 0;
    const ti = Math.min(n, s.length - 1);
    const pts = project(s, 1044, 150, 40, 18).map((p) => ({ x: p.x + 300, y: p.y + 18 }));
    for (let i = 0; i < s.length - 1; i++) {
      el('line', { x1: pts[i].x, y1: pts[i].y, x2: pts[i + 1].x, y2: pts[i + 1].y, stroke: i < n - 1 ? '#e5c888' : '#5a6070', 'stroke-width': i < n - 1 ? 1.4 : .8, 'stroke-dasharray': i < n - 1 ? '' : '3 5', opacity: i < n - 1 ? .85 : .5 }, svg);
    }
    pts.forEach((p, i) => star(svg, glow, p.x, p.y, i === ti ? 3.4 : 2.2, i < n || i === ti));
    const tgt = s[ti], tp = pts[ti];
    const dist = car.pos ? haversine(car.pos, tgt) : 0;
    const brg = car.pos ? bearing(car.pos, tgt) : 0;
    const rel = ((brg - car.heading + 540) % 360) - 180;
    const side = Math.abs(rel) < 20 ? '정면' : rel < 0 ? '좌측' : '우측';
    text(svg, tp.x, tp.y + 30, `${tgt.label.replace(' 출발', '')} · ${ROMAN[tgt.label] || ''}`, { 'font-size': 17, 'font-family': 'Noto Serif CJK KR, serif' });
    text(svg, tp.x, tp.y + 48, `${compassWord(brg)}쪽 ${side} ${km(dist)} km · ${state.route.label}`, { fill: '#e5c888', 'font-size': 11.5 });
    const ticks = $('skyTicks'); ticks.innerHTML = '';
    [-30, -15, 0, 15, 30].forEach((o) => {
      const deg = Math.round((car.heading + o + 360) % 360);
      const sp = document.createElement('span'); sp.style.left = (822 + o * 13) + 'px';
      sp.textContent = o === 0 ? `▲ ${compassCode(deg)} ${String(deg).padStart(3, '0')}° · ${compassWord(deg)}` : `${Math.abs(o) === 30 ? compassCode(deg) + ' ' : ''}${String(deg).padStart(3, '0')}°`;
      if (o === 0) { sp.style.color = '#f4f1ea'; sp.style.border = '1px solid rgba(255,255,255,.18)'; sp.style.borderRadius = '10px'; sp.style.padding = '1px 10px'; sp.style.top = '-2px'; }
      ticks.appendChild(sp);
    });
  }
  function driveBar() {
    if (current !== 'drive') return;
    const j = journey(); const s = stops(); if (!s.length) return;
    const n = j ? j.points.length : 0, ti = Math.min(n, s.length - 1), tgt = s[ti];
    const dist = car.pos ? haversine(car.pos, tgt) : 0;
    let remain = dist; for (let i = ti; i < s.length - 1; i++) remain += haversine(s[i], s[i + 1]);
    const done = j && n >= s.length;
    const h = Math.round(car.heading);
    setBar({
      cap: 'HEADING COMPASS', val: `▲ ${compassCode(h)} ${String(h).padStart(3, '0')}° ${compassWord(h)} · ${tgt.label.replace(' 출발', '')} 방면`, heading: h,
      pill: done ? '■ 종료' : `${km(dist)} km`, onPill: done ? endJourney : null,
      t1: done ? `${s.length}개 별 기록 완료 · 여정을 종료하면 별자리가 만들어집니다` : `다음 별 · ${tgt.label} (${ti + 1}/${s.length}) 방향으로 주행`,
      t2: `목적지: ${state.route.label} (${s[s.length - 1].label}) · DEMO REPLAY 시연 데이터`,
      g: done ? `잔여 0 km · 별 ${n}/${s.length}` : `잔여 ${km(remain)} km · 별 ${n}/${s.length}`,
      r2: done ? '도착 · 명시적 종료 대기 중' : '경북 방면 REPLAY 주행 중',
    });
  }

  // ------------------------------------------------------------------ memory (P)
  const ARCHIVE = [
    { id: 'jeju', name: '제주 한라자리', place: '제주 한라산 일대', date: '2026. 05. 02', lat: 33.36, photos: [] },
    { id: 'gangneung', name: '강릉 경포자리', place: '강원 강릉시 경포 일대', date: '2026. 07. 19', lat: 37.79, photos: [] },
    { id: 'today' },
    { id: 'busan', name: '부산 갈매기자리', place: '부산 해운대 일대', date: '2026. 08. 30', lat: 35.16, photos: ['photos/next_thumb.png'] },
    { id: 'gyeongju', name: '경주 첨성대자리', place: '경북 경주시 일대', date: '2026. 09. 12', lat: 35.83, photos: [] },
  ];
  const SLOTS = [{ x: 330, y: 352 }, { x: 530, y: 222 }, { x: 822, y: 448 }, { x: 1250, y: 264 }, { x: 1480, y: 372 }];
  const DEMO_PHOTOS = ['ev_station.png', 'saejae_viewpoint.jpg', 'saejae_terrace.jpg'];
  let memIndex = 2, memStar = null;
  $('memPrev').addEventListener('click', () => { memIndex = (memIndex + 4) % 5; renderMemory(); });
  $('memNext').addEventListener('click', () => { memIndex = (memIndex + 1) % 5; renderMemory(); });

  function todayItem() {
    const j = journey(); const s = stops();
    const name = state ? state.route.label : '오늘의 별자리';
    if (!j) return { id: 'today', name, place: '오늘의 여정 · 아직 시작 전', date: '', lat: s.length ? s[s.length - 1].lat : 36.7, live: true, points: [] };
    const pts = j.points.filter((p) => p.newStar);
    const last = s[s.length - 1];
    return {
      id: 'today', name, live: true, points: pts, status: j.status,
      place: j.status === 'COMPLETED' ? `${last.label} 일대` : `오늘의 여정 · 기록 중 ${j.points.length}/${j.totalStops}`,
      date: dateText(j.endedAt || j.startedAt), lat: last.lat,
    };
  }
  function itemAt(i) { return i === 2 ? todayItem() : ARCHIVE[i]; }
  function s0label() { const s = stops(); return s.length ? s[0].label : ''; }

  async function renderMemory() {
    const item = itemAt(memIndex);
    const svg = $('memSky'); svg.innerHTML = ''; const glow = svgDefs(svg);
    backgroundStars(svg, 1644, 960, 120, 11);
    const slotOf = (i) => SLOTS[(i - memIndex + 2 + 5) % 5];
    for (let i = 0; i < 5; i++) {
      const a = slotOf(i), b = slotOf((i + 1) % 5);
      if (Math.abs(((i - memIndex + 7) % 5) - ((i + 1 - memIndex + 7) % 5)) !== 1) continue;
      el('line', { x1: a.x, y1: a.y, x2: b.x, y2: b.y, stroke: '#e5c888', 'stroke-width': .9, opacity: .38, 'stroke-dasharray': Math.abs(a.x - 822) > 400 || Math.abs(b.x - 822) > 400 ? '3 6' : '' }, svg);
    }
    const sats = $('memSats'); sats.innerHTML = '';
    for (let i = 0; i < 5; i++) {
      if (i === memIndex) continue;
      const it = itemAt(i), p = slotOf(i), near = Math.abs(p.x - 822) < 500 && p.x > 822;
      if (!near) star(svg, glow, p.x, p.y, 2.4, true);
      const d = document.createElement('div'); d.className = 'sat';
      d.style.left = (p.x) + 'px'; d.style.top = (p.y + (near ? -50 : 18)) + 'px';
      const thumb = it.photos && it.photos[0] ? `<div class="thumb"><img src="${it.photos[0]}"></div>` : '';
      d.innerHTML = near ? `<div class="next">다음 별자리 · ${pad2(i + 1)}</div>${thumb || '<div class="thumb"></div>'}${it.name}` : `${pad2(i + 1)} · ${it.name}`;
      d.onclick = () => { memIndex = i; renderMemory(); };
      sats.appendChild(d);
    }
    $('memCaption').textContent = item.live ? `SAVED CONSTELLATION · ${item.place.split(' · ')[0]} (${item.lat.toFixed(2)}° N)` : `SAVED CONSTELLATION · DEMO ARCHIVE · ${item.place} (${item.lat.toFixed(2)}° N)`;
    $('memTitle').textContent = item.name;
    $('memSub').textContent = [item.place, item.date].filter(Boolean).join(' · ');
    $('memCur').textContent = `${pad2(memIndex + 1)} / 05 · ${item.name}`;
    const photo = $('memPhoto'); photo.querySelectorAll('img,.empty').forEach((n) => n.remove());
    const chips = $('memStars'); chips.innerHTML = '';
    let count = 0, src = null;
    if (item.live) {
      const pts = item.points;
      if (pts.length && (!memStar || !pts.some((p) => p.starId === memStar))) memStar = pts[pts.length - 1].starId;
      pts.forEach((p) => {
        const b = document.createElement('button'); b.textContent = `★ ${p.label}${state.photoCounts && state.photoCounts[p.starId] ? ' · ' + state.photoCounts[p.starId] : ''}`;
        b.classList.toggle('on', p.starId === memStar); b.onclick = () => { memStar = p.starId; renderMemory(); };
        chips.appendChild(b);
      });
      if (memStar) {
        const add = document.createElement('button'); add.className = 'add'; add.textContent = '+ 시연 사진 등록 (DEMO)';
        add.onclick = async () => {
          const idx = pts.findIndex((p) => p.starId === memStar);
          const r = await call('memory.demoPhoto', { starId: memStar, asset: DEMO_PHOTOS[(idx + 3) % DEMO_PHOTOS.length] });
          if (r.ok) { state.photoCounts = state.photoCounts || {}; state.photoCounts[memStar] = r.count; toast('사진을 앱 내부에 복사해 이 별에 연결했습니다'); renderMemory(); }
          else toast('사진 등록 실패 · ' + (r.error || ''));
        };
        chips.appendChild(add);
        const r = await call('memory.photo', { starId: memStar });
        if (itemAt(memIndex).id !== 'today') return;
        count = r.count || 0; src = r.dataUri;
        const sel = pts.find((p) => p.starId === memStar);
        $('memSub').textContent = `선택한 별 · ${sel ? sel.label : ''} · ${s0label()} → ${item.place.replace(' 일대', '')} · ${item.date}`;
      }
    } else { count = item.photos.length; src = item.photos[0] || null; }
    $('memCount').textContent = `${pad2(memIndex + 1)} / 05 · ${count}장`;
    if (src) { const img = document.createElement('img'); img.src = src; photo.appendChild(img); }
    else {
      const e = document.createElement('div'); e.className = 'empty';
      e.innerHTML = item.live ? (item.points.length ? '이 별에 연결된 사진이 없습니다<br>아래에서 시연 사진을 등록할 수 있습니다' : '여정을 시작하면<br>방문한 별이 이곳에 쌓입니다') : '보관된 사진 없음<br><span style="font-size:11px">DEMO ARCHIVE</span>';
      photo.appendChild(e);
    }
    setBar({ cap: 'SAVED ARCHIVE', val: '정차 P · 기록 열람 중', pill: 'P · 정차', t1: `${item.name}${item.date ? ' · ' + item.date : ''}`,
      t2: item.live ? `${item.place} · 사진 ${count}장` : `${item.place} · DEMO ARCHIVE`, g: `아카이브 ${pad2(memIndex + 1)} / 05`, r2: '좌우 버튼으로 별자리 전환' });
  }

  // ------------------------------------------------------------------ forging
  function runForge() {
    if (forging) return;
    forging = true;
    show('forge');
    const j = journey(); const pts = j.points.filter((p) => p.newStar); const total = pts.length;
    $('forgeTitle').textContent = `오늘의 별자리 작성 : ${state.route.label}`;
    const svg = $('forgeSky'); svg.innerHTML = ''; const glow = svgDefs(svg);
    backgroundStars(svg, 1268, 420, 70, 3);
    const xy = project(pts, 1268, 420, 220, 50, 2.6);
    const nodes = $('tlNodes'); nodes.innerHTML = '';
    pts.forEach((p, i) => { const n = document.createElement('div'); n.className = 'node'; n.style.left = (i / Math.max(total - 1, 1) * 100) + '%'; n.innerHTML = `<span>${p.label}</span>`; nodes.appendChild(n); });
    $('tlFill').style.width = '0';
    let k = 0;
    const step = () => {
      if (k > 0) el('line', { x1: xy[k - 1].x, y1: xy[k - 1].y, x2: xy[k].x, y2: xy[k].y, stroke: '#e5c888', 'stroke-width': 1.6, opacity: .9 }, svg);
      star(svg, glow, xy[k].x, xy[k].y, k === total - 1 ? 4 : 3, true);
      text(svg, xy[k].x, xy[k].y - 20, `${k + 1}. ${pts[k].label}`, { 'font-size': 15, 'font-weight': 600 });
      text(svg, xy[k].x, xy[k].y + 30, k === 0 ? '출발' : k === total - 1 ? '도착지' : `경유 ${k}`, { 'font-size': 11, fill: '#e5c888' });
      nodes.children[k].classList.add('on');
      $('tlFill').style.width = (k / Math.max(total - 1, 1) * 100) + '%';
      $('forgeBadge').textContent = `경유 노드 ${k + 1} / ${total} · ${pts[k].label}${k === total - 1 ? ' (도착지)' : ''}`;
      setBar({ cap: 'TRIP FORGING', val: '여정 별자리 생성 중', pill: `${k + 1} / ${total}`,
        t1: `출발지 ${pts[0].label.replace(' 출발', '')} → 경유지 ${Math.max(total - 2, 0)}곳 → 도착지 ${pts[total - 1].label} 연결 중`,
        t2: '방문 지점 순서대로 별과 광선 경로를 이어 별자리를 생성합니다', g: state.route.label, r2: k === total - 1 ? '별자리 조립 완료' : '별자리 조립 진행 중' });
      k += 1;
      if (k < total) setTimeout(step, 620);
      else setTimeout(() => { forging = false; show('score'); ensureMelody(); }, 1900);
    };
    call('melody.optimize').then((r) => { if (r.ok) melody = r.melody; });
    setTimeout(step, 500);
  }

  // ------------------------------------------------------------------ score
  async function ensureMelody() {
    if (!melody) {
      const r = await call('melody.optimize');
      if (r.ok) melody = r.melody; else toast('멜로디를 만들 수 없습니다 · ' + (r.error || ''));
    }
    const s = await call('state'); if (s.ok) { state = s; music = s.music || music; }
    renderScore();
  }
  function renderScore() {
    if (current !== 'score' || !state) return;
    const j = journey(); if (!j) return;
    $('scoreTitle').textContent = `오늘의 별자리 : ${state.route.label}`;
    const svg = $('scoreSky'); svg.innerHTML = ''; const glow = svgDefs(svg);
    const m = state.match;
    $('match').classList.toggle('on', !!m);
    if (m) { $('matchCap').textContent = `SIMILARITY MATCH · ${m.grade}등급 해금`; $('matchV').textContent = `${m.name} (${m.latin}) · ${(m.score * 100).toFixed(1)}%`; $('matchG').textContent = m.grade; }
    if (!melody) { text(svg, 634, 230, '별자리를 오선지에 맞춰 회전하는 중…', { fill: '#8e929f', 'font-size': 15 }); scoreCard(); return; }
    $('scoreCap').textContent = `TODAY TRIP CONSTELLATION · 회전 ${melody.rotation}° · ${melody.key} · 자연스러움 ${Math.round(melody.naturalness * 100)}%`;
    const notes = melody.notes;
    const mids = notes.map((n) => n.midi), lo = Math.min(...mids), hi = Math.max(...mids);
    const yOf = (mm) => hi === lo ? 200 : 70 + (hi - mm) / (hi - lo) * 260;
    const xOf = (i) => 150 + i * (1000 / Math.max(notes.length - 1, 1));
    const rows = [...new Set(mids)].sort((a, b) => b - a);
    rows.forEach((mm) => {
      const y = yOf(mm), n = notes.find((q) => q.midi === mm);
      el('line', { x1: 60, y1: y, x2: 1240, y2: y, stroke: '#ffffff', opacity: .06 }, svg);
      text(svg, 18, y + 4, noteName(mm), { 'text-anchor': 'start', fill: n && n === notes[0] ? '#e5c888' : '#5f6472', 'font-size': 12, 'font-family': 'Noto Serif CJK KR, serif' });
      text(svg, 52, y + 4, `${solfa(mm)} · ${hz(mm)} Hz`, { 'text-anchor': 'start', fill: '#5f6472', 'font-size': 10 });
    });
    const d = notes.map((n, i) => `${i ? 'L' : 'M'}${xOf(i)} ${yOf(n.midi)}`).join(' ');
    const path = el('path', { d, fill: 'none', stroke: '#e5c888', 'stroke-width': 1.5, opacity: .85 }, svg);
    const len = path.getTotalLength ? path.getTotalLength() : 1000;
    path.style.strokeDasharray = len; path.style.strokeDashoffset = len; path.style.transition = 'stroke-dashoffset 1.6s ease';
    requestAnimationFrame(() => requestAnimationFrame(() => { path.style.strokeDashoffset = 0; }));
    notes.forEach((n, i) => {
      const g = star(svg, glow, xOf(i), yOf(n.midi), 3, true); g.dataset.i = i;
      text(svg, xOf(i), 384, n.label, { 'font-size': 13 });
      text(svg, xOf(i), 404, `${noteName(n.midi)} (${solfa(n.midi)})`, { 'font-size': 11, fill: '#5f6472' });
    });
    scoreSvgPositions = notes.map((n, i) => ({ x: xOf(i), y: yOf(n.midi) }));
    scoreCard();
  }
  let scoreSvgPositions = [];

  function scoreCard() {
    const j = journey(); const s = stops(); const label = state.route.label;
    const confirmed = melody && melody.confirmed;
    const done = music && music.status === 'SUCCEEDED' && music.playable;
    const busy = music && ['SUBMITTING', 'QUEUED', 'GENERATING', 'DOWNLOADING'].includes(music.status);
    $('cardCap').textContent = done ? 'COSMIC SIGNATURE · COMPLETED SONG · DEMO MOCK' : `${ROMAN[s[s.length - 1].label] || 'STARLOG'} MELODY · ${confirmed ? '확정됨' : '미확정 미리보기'}`;
    $('cardTitle').textContent = done ? `${label} · 완성된 여정 음악` : `${label} · 여정 멜로디`;
    $('cardSub').textContent = done ? `${melody ? melody.notes.length : s.length}개 여정 음표 조합 완료 · DEMO MOCK 서버 생성 음원 재생` : `경유 위도 기반 오르골 사운드 생성 · ${s[0].label} → ${s[s.length - 1].label}`;
    $('waveCap').textContent = `${dateText(j.endedAt || Date.now())} · TRIP END`;
    const bars = $('bars'); bars.innerHTML = '';
    const src = melody ? melody.notes.map((n) => n.midi) : [60, 64, 67];
    for (let i = 0; i < 34; i++) { const b = document.createElement('i'); const v = src[i % src.length]; b.style.height = (14 + ((v * 7 + i * 13) % 48)) + 'px'; b.style.opacity = done ? 1 : .55; bars.appendChild(b); }
    const acts = $('acts'); acts.innerHTML = '';
    const btn = (label2, fn, primary, disabled) => { const b = document.createElement('button'); b.textContent = label2; if (primary) b.className = 'primary'; b.disabled = !!disabled; b.onclick = fn; acts.appendChild(b); return b; };
    if (done) {
      btn(songPlaying ? '♪ 완성 음악 재생 중' : '♪ 완성 음악 재생', playSong, true);
      btn('MyHyundai 앱으로 악보 공유 ↗', () => toast('MyHyundai 공유는 시연 범위에 포함되지 않았습니다 (미연동)'));
    } else if (busy) {
      btn('♪ 오르골 멜로디 재생', playMelody, false, !melody);
      const st = document.createElement('div'); st.className = 'status'; st.textContent = `DEMO MOCK ${({ SUBMITTING: '요청 제출 중', QUEUED: '생성 대기 중', GENERATING: '음악 생성 중', DOWNLOADING: '음원 저장 중' })[music.status]}…`; acts.appendChild(st);
    } else if (confirmed) {
      btn('♪ 오르골 멜로디 재생', playMelody, false, !melody);
      btn(music && music.status === 'FAILED' ? '다시 시도 · 여정 음악 만들기' : '여정 음악 만들기 · 편곡 설정', openSheet, true);
    } else {
      btn('♪ 오르골 멜로디 재생', playMelody, true, !melody);
      btn('이 멜로디로 확정하기', confirmMelody, false, !melody);
    }
    $('card').classList.toggle('spin', songPlaying);
    const totalKm = j.routeDistanceKm || 0;
    const mins = j.endedAt ? Math.round((j.endedAt - j.startedAt) / 60000) : 0;
    setBar({
      cap: done ? 'SIGNATURE PLAY' : 'TRIP COMPLETE', val: done ? '여정 음악 완성 · 재생' : `여정 종료 · ${label}`,
      pill: done ? '♪ 완주' : '♪ PLAY', onPill: done ? playSong : playMelody,
      t1: done ? `${label} · 완성된 시그니처 음악 (DEMO MOCK)` : confirmed ? `${label} · 여정 멜로디 저장됨` : `${label} · 여정 멜로디 미리듣기 (미확정)`,
      t2: `${s[0].label} → ${s[s.length - 1].label} · ${s.length}개 경유 위도 노드 변환`,
      g: `경유 직선 ${km(totalKm)} km 완주`, r2: mins ? `기록 ${Math.floor(mins / 60)}h ${mins % 60}m · 보관함 저장 완료` : '보관함 저장 완료',
    });
  }
  async function confirmMelody() {
    const r = await call('melody.confirm');
    if (r.ok) { melody = r.melody; toast('기본 멜로디를 확정했습니다'); renderScore(); }
    else toast('확정 실패 · ' + (r.error || ''));
  }
  let playTimer = 0;
  async function playMelody() {
    if (!melody) return;
    const r = await call('melody.play');
    if (!r.ok) { toast('재생할 멜로디가 없습니다'); return; }
    const beatMs = 60000 / (r.tempo || melody.tempo || 88);
    const tag = $('playtag'); tag.style.display = '';
    clearTimeout(playTimer);
    melody.notes.forEach((n, i) => {
      setTimeout(() => {
        if (current !== 'score' || !scoreSvgPositions[i]) return;
        tag.style.left = (188 + scoreSvgPositions[i].x) + 'px';
        tag.textContent = `PLAYING · ${noteName(n.midi)} (${hz(n.midi)} Hz)`;
      }, n.startBeat * beatMs);
    });
    const last = melody.notes[melody.notes.length - 1];
    playTimer = setTimeout(() => { tag.style.display = 'none'; }, (last.startBeat + last.duration) * beatMs + 400);
  }
  async function playSong() {
    const r = await call('song.play');
    if (!r.ok) toast('저장된 여정 음악이 없습니다');
  }

  // arrangement sheet
  const SHEET = { genre: ['AMBIENT', 'CLASSICAL', 'JAZZ', 'POP', 'ELECTRONIC'], rhythm: ['STRAIGHT', 'WALTZ', 'SWING', 'SYNCOPATED'], instrument: ['MUSIC_BOX', 'PIANO', 'STRINGS', 'SYNTH', 'GUITAR'] };
  const choice = { genre: 'AMBIENT', rhythm: 'STRAIGHT', instrument: 'MUSIC_BOX' };
  document.querySelectorAll('.sheet .chips').forEach((box) => {
    const key = box.dataset.key;
    SHEET[key].forEach((v) => { const b = document.createElement('button'); b.textContent = v.replace('_', ' '); b.dataset.v = v; b.onclick = () => { choice[key] = v; paintChips(); }; box.appendChild(b); });
  });
  function paintChips() { document.querySelectorAll('.sheet .chips').forEach((box) => box.querySelectorAll('button').forEach((b) => b.classList.toggle('on', choice[box.dataset.key] === b.dataset.v))); }
  $('tempo').addEventListener('input', () => { $('tempoLab').textContent = `TEMPO ${$('tempo').value} BPM`; });
  function openSheet(genre) {
    if (typeof genre === 'string' && SHEET.genre.includes(genre)) choice.genre = genre;
    if (melody) { $('tempo').value = melody.tempo; $('tempoLab').textContent = `TEMPO ${melody.tempo} BPM`; }
    paintChips(); $('sheet').classList.add('on');
  }
  $('sheetCancel').addEventListener('click', () => $('sheet').classList.remove('on'));
  $('sheetGo').addEventListener('click', async () => {
    $('sheet').classList.remove('on');
    const r = await call('music.generate', Object.assign({ tempo: Number($('tempo').value) }, choice));
    if (r.ok) { music = { status: 'SUBMITTING', source: 'DEMO_MOCK', playable: false }; renderScore(); }
    else toast(r.error === 'melody.not_confirmed' ? '먼저 기본 멜로디를 확정해 주세요' : '요청 실패 · ' + (r.error || ''));
  });

  // ------------------------------------------------------------------ native events
  function handleEvent(type, p) {
    if (type === 'journey') {
      state = p;
      if (current === 'drive') { retarget(); renderDrive(false); }
      if (current === 'atlas') renderAtlas();
    } else if (type === 'music') {
      music = p;
      if (p.status === 'SUCCEEDED' && p.playable) toast('DEMO MOCK 여정 음악이 저장되었습니다');
      if (p.status === 'FAILED') toast('DEMO MOCK 음악 생성 실패 · ' + (p.error || ''));
      if (current === 'score') scoreCard();
    } else if (type === 'song') {
      songPlaying = p.state === 'PLAYING' || p.state === 'PREPARING';
      if (current === 'score') scoreCard();
    } else if (type === 'gleo') {
      introDone = true;
      if (p.function === 'CONTROL_STARLOG_JOURNEY') {
        if (p.response && p.response.state) state = p.response.state;
        const j = journey();
        if (p.operation === 'START' && j && j.status === 'ACTIVE') { if (current !== 'drive') { melody = null; music = null; } show('drive'); }
        else if (p.operation === 'END' && j && j.status === 'COMPLETED') { if (current !== 'score') runForge(); }
        else if (p.operation === 'STATUS') toast(p.response ? p.response.message : '');
        else if (p.response) toast(p.response.message);
      } else if (p.function === 'PREPARE_CONSTELLATION_MUSIC') {
        show('score'); ensureMelody().then(() => { if (p.confirmed) openSheet(p.genre || undefined); else toast('편곡 전에 기본 멜로디를 확정해 주세요'); });
      } else if (p.function === 'PLAY_CONSTELLATION_MUSIC') {
        show('score'); ensureMelody();
        if (!p.playing) toast('재생할 여정 음악이 아직 없습니다');
      }
    }
  }

  // ------------------------------------------------------------------ boot sequence: intro → atlas → (drive)
  async function boot() {
    show('intro');
    const s = await call('state');
    if (s.ok) { state = s; music = s.music || null; }
    if (native) native.ready();
    setTimeout(() => {
      if (introDone) return;
      show('atlas'); renderAtlas();
      setTimeout(() => {
        if (introDone) return;
        introDone = true;
        const j = journey();
        if (j && j.status === 'ACTIVE') { show('drive'); call('replay.start'); }
      }, 3200);
    }, 3400);
  }

  // ------------------------------------------------------------------ browser-only mock (desktop preview)
  const Mock = {
    stops: [['대구 출발', 35.8797, 128.6286], ['팔공산', 36.0164, 128.6947], ['구미', 36.1195, 128.3446], ['김천', 36.1398, 128.1136], ['상주', 36.411, 128.159], ['이화령 고개', 36.7769, 128.0595], ['문경 새재', 36.7633, 128.0778]],
    j: null, melody: null,
    st() {
      const route = { id: 'mungyeong', label: '문경 새재자리', stops: this.stops.map(([label, lat, lon]) => ({ label, lat, lon })) };
      return { ok: true, tickMs: 2600, route, journey: this.j, melody: this.melody, music: null, match: this.j && this.j.status === 'COMPLETED' ? { name: '오리온자리', latin: 'ORION', score: .806, grade: 'A' } : null, photoCounts: {} };
    },
    handle(m, a) {
      const now = Date.now();
      if (m === 'journey.start') { this.j = { tripId: 'mock', status: 'ACTIVE', startedAt: now, endedAt: null, points: [], totalStops: 7, routeDistanceKm: 118.6 }; this.tick(); return { ok: true, state: this.st() }; }
      if (m === 'journey.end') { if (this.j && this.j.points.length === 7) { this.j.status = 'COMPLETED'; this.j.endedAt = now + 6300000; this.j.constellationId = 'c'; } return { ok: !!this.j, state: this.st(), message: '' }; }
      if (m === 'melody.optimize') { const mid = [60, 65, 62, 69, 71, 72, 67]; this.melody = { id: 'm', confirmed: false, rotation: 315, tempo: 88, key: 'C Major', naturalness: .83, notes: this.stops.map(([label, lat], i) => ({ label, lat, midi: mid[i], startBeat: i, duration: 1 })) }; return { ok: true, melody: this.melody }; }
      if (m === 'melody.confirm') { this.melody.confirmed = true; return { ok: true, melody: this.melody }; }
      if (m === 'memory.photo') return { ok: true, count: 0, dataUri: null };
      if (m === 'melody.play') return { ok: true, tempo: 88 };
      return Object.assign({ ok: true }, m === 'state' ? this.st() : {});
    },
    tick() {
      setTimeout(() => {
        if (!this.j || this.j.points.length >= 7) return;
        const [label, lat, lon] = this.stops[this.j.points.length];
        this.j.points.push({ order: this.j.points.length, label, lat, lon, starId: 's' + this.j.points.length, newStar: true, visitedAt: Date.now() });
        handleEvent('journey', this.st()); this.tick();
      }, 2600);
    },
  };
  boot();
})();
