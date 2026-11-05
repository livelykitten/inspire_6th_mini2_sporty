// iso.js — README 아키텍처 이미지용 아이소메트릭 SVG 렌더러 (의존성 없음)
// 월드 좌표 (x, y, z): x = 화면 오른쪽 아래, y = 화면 왼쪽 아래, z = 위.
// 렌더: python docs/assets/src/render.py  →  docs/assets/*.png
(function () {
  const NS = 'http://www.w3.org/2000/svg';
  const C30 = Math.cos(Math.PI / 6);
  const S30 = 0.5;

  function el(tag, attrs, parent) {
    const n = document.createElementNS(NS, tag);
    for (const k in attrs) if (attrs[k] !== undefined) n.setAttribute(k, attrs[k]);
    if (parent) parent.appendChild(n);
    return n;
  }

  function hexToRgb(h) {
    const v = h.replace('#', '');
    return [0, 2, 4].map((i) => parseInt(v.slice(i, i + 2), 16));
  }
  function rgbToHex(r) {
    return '#' + r.map((c) => Math.max(0, Math.min(255, Math.round(c))).toString(16).padStart(2, '0')).join('');
  }
  // f > 0 밝게, f < 0 어둡게
  function shade(hex, f) {
    const c = hexToRgb(hex);
    return rgbToHex(c.map((v) => (f >= 0 ? v + (255 - v) * f : v * (1 + f))));
  }

  class Iso {
    constructor(svg, { scale = 32, ox = 0, oy = 0 } = {}) {
      this.svg = svg;
      this.s = scale;
      this.ox = ox;
      this.oy = oy;
      this.defs = el('defs', {}, svg);
      this.layer = el('g', {}, svg);
      this.markers = {};
      el('filter', { id: 'shadow', x: '-30%', y: '-30%', width: '160%', height: '180%' }, this.defs)
        .appendChild(el('feDropShadow', { dx: 0, dy: 6, stdDeviation: 7, 'flood-color': '#0f172a', 'flood-opacity': 0.18 }));
      el('filter', { id: 'soft', x: '-30%', y: '-30%', width: '160%', height: '180%' }, this.defs)
        .appendChild(el('feDropShadow', { dx: 0, dy: 2, stdDeviation: 2.5, 'flood-color': '#0f172a', 'flood-opacity': 0.16 }));
    }

    p(x, y, z = 0) {
      return [this.ox + (x - y) * C30 * this.s, this.oy + (x + y) * S30 * this.s - z * this.s];
    }
    pts(list) {
      return list.map((q) => this.p(...q).map((v) => v.toFixed(1)).join(',')).join(' ');
    }
    group(attrs = {}) {
      return el('g', attrs, this.layer);
    }

    marker(color) {
      if (this.markers[color]) return this.markers[color];
      const id = 'arrow' + Object.keys(this.markers).length;
      const m = el('marker', { id, viewBox: '0 0 10 10', refX: 8.5, refY: 5, markerWidth: 7, markerHeight: 7, orient: 'auto-start-reverse' }, this.defs);
      el('path', { d: 'M0,0 L10,5 L0,10 z', fill: color }, m);
      this.markers[color] = `url(#${id})`;
      return this.markers[color];
    }

    // 직육면체. 보이는 면: 윗면, +y 면(왼쪽 아래), +x 면(오른쪽 아래)
    box(x, y, z, w, d, h, color, o = {}) {
      const g = el('g', { filter: o.shadow === false ? undefined : o.shadow || 'url(#soft)', opacity: o.opacity }, o.parent || this.layer);
      const stroke = o.stroke || shade(color, -0.25);
      const sw = o.strokeWidth ?? 0.8;
      el('polygon', { points: this.pts([[x, y + d, z], [x + w, y + d, z], [x + w, y + d, z + h], [x, y + d, z + h]]), fill: o.left || shade(color, -0.08), stroke, 'stroke-width': sw, 'stroke-linejoin': 'round' }, g);
      el('polygon', { points: this.pts([[x + w, y, z], [x + w, y + d, z], [x + w, y + d, z + h], [x + w, y, z + h]]), fill: o.right || shade(color, -0.28), stroke, 'stroke-width': sw, 'stroke-linejoin': 'round' }, g);
      el('polygon', { points: this.pts([[x, y, z + h], [x + w, y, z + h], [x + w, y + d, z + h], [x, y + d, z + h]]), fill: o.top || shade(color, 0.18), stroke, 'stroke-width': sw, 'stroke-linejoin': 'round' }, g);
      if (o.gloss !== false && h > 0.3) {
        // 윗면 하이라이트
        el('polygon', { points: this.pts([[x + w * 0.08, y + d * 0.08, z + h], [x + w * 0.55, y + d * 0.08, z + h], [x + w * 0.08, y + d * 0.55, z + h]]), fill: '#ffffff', opacity: 0.18 }, g);
      }
      return g;
    }

    // 얇은 판 (레이어/서브넷 플랫폼)
    slab(x, y, z, w, d, h, color, o = {}) {
      return this.box(x, y, z, w, d, h, color, { shadow: 'url(#shadow)', gloss: false, strokeWidth: 1, ...o });
    }

    // 원기둥 (DB · 캐시)
    cylinder(x, y, z, r, h, color, o = {}) {
      const g = el('g', { filter: 'url(#soft)' }, o.parent || this.layer);
      const [bx, by] = this.p(x, y, z);
      const [, ty] = this.p(x, y, z + h);
      const rx = r * this.s * Math.SQRT2 * C30;
      const ry = r * this.s * Math.SQRT2 * S30;
      const stroke = shade(color, -0.3);
      const gid = 'cyl' + Math.random().toString(36).slice(2, 8);
      const lg = el('linearGradient', { id: gid, x1: 0, x2: 1, y1: 0, y2: 0 }, this.defs);
      el('stop', { offset: '0%', 'stop-color': shade(color, -0.02) }, lg);
      el('stop', { offset: '55%', 'stop-color': shade(color, -0.18) }, lg);
      el('stop', { offset: '100%', 'stop-color': shade(color, -0.38) }, lg);
      el('path', { d: `M${bx - rx},${ty} L${bx - rx},${by} A${rx},${ry} 0 0 0 ${bx + rx},${by} L${bx + rx},${ty} Z`, fill: `url(#${gid})`, stroke, 'stroke-width': 0.8 }, g);
      const bands = o.bands ?? 2;
      for (let i = 1; i <= bands; i++) {
        const yy = by - ((by - ty) * i) / (bands + 1);
        el('path', { d: `M${bx - rx},${yy} A${rx},${ry} 0 0 0 ${bx + rx},${yy}`, fill: 'none', stroke: shade(color, 0.35), 'stroke-width': 1.2, opacity: 0.7 }, g);
      }
      el('ellipse', { cx: bx, cy: ty, rx, ry, fill: shade(color, 0.25), stroke, 'stroke-width': 0.8 }, g);
      el('ellipse', { cx: bx - rx * 0.25, cy: ty - ry * 0.2, rx: rx * 0.45, ry: ry * 0.35, fill: '#fff', opacity: 0.22 }, g);
      return g;
    }

    // 면 위에 눕힌 텍스트. face: 'top'(x 방향) | 'topY'(-y 방향) | 'left'(+y 면, x 방향) | 'right'(+x 면, -y 방향)
    faceText(x, y, z, text, o = {}) {
      const [X, Y] = this.p(x, y, z);
      const m = {
        top: [C30, S30, -C30, S30],
        topY: [C30, -S30, C30, S30],
        left: [C30, S30, 0, 1],
        right: [C30, -S30, 0, 1],
      }[o.face || 'top'];
      const t = el('text', {
        transform: `matrix(${m.join(',')},${X.toFixed(1)},${Y.toFixed(1)})`,
        'font-size': o.size || 13,
        'font-weight': o.weight || 700,
        'font-family': o.mono ? 'var(--mono)' : 'var(--sans)',
        fill: o.color || '#fff',
        'text-anchor': o.anchor || 'start',
        'dominant-baseline': o.baseline || 'middle',
        'letter-spacing': o.spacing,
        opacity: o.opacity,
      }, o.parent || this.layer);
      t.textContent = text;
      return t;
    }

    // 화면 좌표 기준 라벨 카드 (월드 점 위에 띄움)
    tag(x, y, z, title, sub, o = {}) {
      const [X0, Y0] = this.p(x, y, z);
      const X = X0 + (o.dx || 0);
      const Y = Y0 - (o.lift ?? 12);
      const g = el('g', {}, o.parent || this.layer);
      const tsize = o.size || 14;
      const ssize = o.subSize || 11.5;
      const t = el('text', { x: X, y: 0, 'font-size': tsize, 'font-weight': 800, 'font-family': 'var(--sans)', fill: o.color || '#0f172a', 'text-anchor': 'middle' }, g);
      t.textContent = title;
      let s;
      if (sub) {
        s = el('text', { x: X, y: 0, 'font-size': ssize, 'font-weight': 600, 'font-family': o.subMono === false ? 'var(--sans)' : 'var(--mono)', fill: o.subColor || '#475569', 'text-anchor': 'middle' }, g);
        s.textContent = sub;
      }
      const tw = t.getComputedTextLength();
      const sw = s ? s.getComputedTextLength() : 0;
      const w = Math.max(tw, sw) + 22;
      const hgt = s ? tsize + ssize + 16 : tsize + 12;
      const top = Y - hgt;
      const bg = el('rect', { x: X - w / 2, y: top, width: w, height: hgt, rx: 9, fill: o.bg || 'rgba(255,255,255,0.96)', stroke: o.border || '#e2e8f0', 'stroke-width': 1, filter: 'url(#soft)' });
      g.insertBefore(bg, t);
      if (o.accent) el('rect', { x: X - w / 2, y: top, width: 4, height: hgt, rx: 2, fill: o.accent }, g).parentNode.insertBefore(g.lastChild, t);
      t.setAttribute('y', top + 7 + tsize * 0.85);
      if (s) s.setAttribute('y', top + 7 + tsize + 4 + ssize * 0.8);
      // 꼬리
      if (o.tail !== false) el('path', { d: `M${X0 - 6},${Y} L${X0},${Y + 7} L${X0 + 6},${Y} Z`, fill: o.bg || 'rgba(255,255,255,0.96)' }, g);
      return g;
    }

    // 화면 좌표 작은 배지 (월드 점 기준)
    chip(x, y, z, text, o = {}) {
      const [X0, Y0] = this.p(x, y, z);
      const X = X0 + (o.dx || 0);
      const Y = Y0 + (o.dy || 0);
      const g = el('g', {}, o.parent || this.layer);
      const t = el('text', { x: X, y: Y + 4.2, 'font-size': o.size || 11, 'font-weight': 700, 'font-family': o.mono ? 'var(--mono)' : 'var(--sans)', fill: o.color || '#fff', 'text-anchor': 'middle' }, g);
      t.textContent = text;
      const w = t.getComputedTextLength() + 16;
      const h = (o.size || 11) + 10;
      g.insertBefore(el('rect', { x: X - w / 2, y: Y - h / 2, width: w, height: h, rx: h / 2, fill: o.bg || '#0f172a', stroke: o.border, 'stroke-width': o.border ? 1 : 0, filter: o.shadow === false ? undefined : 'url(#soft)' }), t);
      return g;
    }

    // 월드 폴리라인 연결선
    path3d(points, o = {}) {
      const color = o.color || '#334155';
      const d = 'M' + points.map((q) => this.p(...q).map((v) => v.toFixed(1)).join(',')).join(' L');
      return this.drawLine(d, color, o);
    }

    // 두 월드 점을 잇는 들린 곡선 (동기 호출 등)
    arc3d(a, b, lift, o = {}) {
      const color = o.color || '#334155';
      const [x1, y1] = this.p(...a);
      const [x2, y2] = this.p(...b);
      const cx = (x1 + x2) / 2;
      const cy = Math.min(y1, y2) - lift;
      // 2차 베지어 t=0.5 지점 (라벨 위치)
      return this.drawLine(`M${x1},${y1} Q${cx},${cy} ${x2},${y2}`, color, o, { x: (x1 + 2 * cx + x2) / 4, y: (y1 + 2 * cy + y2) / 4 });
    }

    // 화면 좌표 배지
    chipXY(X, Y, text, o = {}) {
      const g = el('g', {}, o.parent || this.layer);
      const t = el('text', { x: X, y: Y + 4.2, 'font-size': o.size || 11, 'font-weight': 700, 'font-family': o.mono ? 'var(--mono)' : 'var(--sans)', fill: o.color || '#fff', 'text-anchor': 'middle' }, g);
      t.textContent = text;
      const w = t.getComputedTextLength() + 16;
      const h = (o.size || 11) + 10;
      g.insertBefore(el('rect', { x: X - w / 2, y: Y - h / 2, width: w, height: h, rx: h / 2, fill: o.bg || '#0f172a', stroke: o.border, 'stroke-width': o.border ? 1 : 0, filter: 'url(#soft)' }), t);
      return g;
    }

    drawLine(d, color, o, mid) {
      const g = el('g', {}, o.parent || this.layer);
      if (o.halo !== false) el('path', { d, fill: 'none', stroke: o.haloColor || '#ffffff', 'stroke-width': (o.width || 2.2) + 4, 'stroke-linecap': 'round', 'stroke-linejoin': 'round', opacity: 0.85 }, g);
      el('path', {
        d,
        fill: 'none',
        stroke: color,
        'stroke-width': o.width || 2.2,
        'stroke-dasharray': o.dash,
        'stroke-linecap': 'round',
        'stroke-linejoin': 'round',
        'marker-end': o.arrow === false ? undefined : this.marker(color),
        'marker-start': o.both ? this.marker(color) : undefined,
      }, g);
      if (o.start) {
        const m = d.match(/^M([\d.-]+),([\d.-]+)/);
        el('circle', { cx: m[1], cy: m[2], r: 3.6, fill: color, stroke: '#fff', 'stroke-width': 1.5 }, g);
      }
      g.mid = mid;
      return g;
    }

    // 화면 좌표 텍스트
    text(X, Y, str, o = {}) {
      const t = el('text', { x: X, y: Y, 'font-size': o.size || 14, 'font-weight': o.weight || 600, 'font-family': o.mono ? 'var(--mono)' : 'var(--sans)', fill: o.color || '#0f172a', 'text-anchor': o.anchor || 'start', 'letter-spacing': o.spacing, opacity: o.opacity }, o.parent || this.svg);
      t.textContent = str;
      return t;
    }
  }

  window.Iso = Iso;
  window.isoShade = shade;
  window.isoEl = el;
  // 폰트 로드 뒤 그린다 (텍스트 폭 측정 때문)
  window.isoReady = (fn) => {
    const go = () => document.fonts.ready.then(() => { fn(); document.body.setAttribute('data-ready', '1'); });
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', go);
    else go();
  };
})();
