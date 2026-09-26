import { useCallback, useEffect, useRef, useState } from 'react';
import { forceCollide, forceLink, forceManyBody, forceSimulation, forceX, forceY } from 'd3-force';
import Icon from './Icons';
import { COLORS, bounds, chargeOf, edgeStyle, hash01, hexToRgba, isInfra, linkDistance, linkStrength, nodeColor, nodeRadius } from '../utils/topologyUtils';
import { HEALTH_LABEL, STATE_LABEL, TYPE_LABEL, fmtMs } from '../utils/formatters';
import '../styles/topology.css';

const prefersReducedMotion = () => window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

/**
 * Canvas force-directed topology.
 *  - solid line = observed, dashed = inferred from real data (traceroute / IP subnet), dotted = assumed.
 *  - a pulse ring radiates from this computer while a scan is running.
 * Pan = drag background, zoom = mouse wheel, drag nodes to rearrange, click a node to inspect it.
 */
export default function TopologyGraph({
  topology, selectedId, onSelect, scanning = false, height = 480,
  showOffline = true, showDevices = true, legend = true
}) {
  const wrapRef = useRef(null);
  const canvasRef = useRef(null);
  const [tip, setTip] = useState(null);
  const g = useRef({
    nodes: [], links: [], sim: null, k: 1, x: 0, y: 0, w: 800, h: height,
    hover: null, drag: null, pan: null, dirty: true, fitted: false, selected: null, scanning: false, t0: performance.now()
  });

  const requestDraw = () => { g.current.dirty = true; };

  // ---------- fit / zoom ----------
  const fit = useCallback(() => {
    const s = g.current;
    const b = bounds(s.nodes);
    if (!b) return;
    const bw = Math.max(80, b.x1 - b.x0), bh = Math.max(80, b.y1 - b.y0);
    const k = Math.max(0.15, Math.min((s.w - 90) / bw, (s.h - 90) / bh, 2));
    s.k = k;
    s.x = s.w / 2 - ((b.x0 + b.x1) / 2) * k;
    s.y = s.h / 2 - ((b.y0 + b.y1) / 2) * k;
    requestDraw();
  }, []);

  const zoomAt = useCallback((factor, px, py) => {
    const s = g.current;
    const k2 = Math.max(0.1, Math.min(5, s.k * factor));
    const wx = (px - s.x) / s.k, wy = (py - s.y) / s.k;
    s.k = k2;
    s.x = px - wx * k2;
    s.y = py - wy * k2;
    requestDraw();
  }, []);

  // ---------- canvas size ----------
  useEffect(() => {
    const wrap = wrapRef.current;
    const resize = () => {
      const s = g.current;
      const w = wrap.clientWidth;
      s.w = w; s.h = height;
      const c = canvasRef.current;
      const dpr = window.devicePixelRatio || 1;
      c.width = Math.round(w * dpr);
      c.height = Math.round(height * dpr);
      c.style.height = `${height}px`;
      requestDraw();
    };
    resize();
    const ro = new ResizeObserver(resize);
    ro.observe(wrap);
    return () => ro.disconnect();
  }, [height]);

  // ---------- (re)build simulation when data changes ----------
  useEffect(() => {
    const s = g.current;
    if (s.sim) s.sim.stop();
    if (!topology) { s.nodes = []; s.links = []; requestDraw(); return; }

    const prev = new Map(s.nodes.map((n) => [n.id, n]));
    const visible = (n) => {
      if (n.type === 'SUBNET' || n.type === 'INTERNET' || isInfra(n)) return true;
      if (!showDevices) return false;
      if (!showOffline && n.state === 'UNREACHABLE') return false;
      return true;
    };
    const hubOf = new Map();
    topology.edges.forEach((e) => { if (e.relation === 'member of subnet') hubOf.set(e.source, e.target); });

    let hubIndex = 0;
    const nodes = topology.nodes.filter(visible).map((n) => {
      const p = prev.get(n.id);
      if (p) return { ...n, x: p.x, y: p.y, vx: p.vx, vy: p.vy, fx: p.fx, fy: p.fy };
      const hub = prev.get(hubOf.get(n.id));
      if (hub) return { ...n, x: hub.x + (Math.random() - 0.5) * 60, y: hub.y + (Math.random() - 0.5) * 60 };
      const a = (hubIndex++ * 2.4) % (Math.PI * 2);
      return { ...n, x: s.w / 2 + Math.cos(a) * 140 + (Math.random() - 0.5) * 30, y: s.h / 2 + Math.sin(a) * 140 + (Math.random() - 0.5) * 30 };
    });
    const ids = new Set(nodes.map((n) => n.id));
    const links = topology.edges.filter((e) => ids.has(e.source) && ids.has(e.target)).map((e) => ({ source: e.source, target: e.target, edge: e }));

    s.nodes = nodes;
    s.links = links;
    const first = !s.fitted;

    const sim = forceSimulation(nodes)
      .force('link', forceLink(links).id((d) => d.id).distance(linkDistance).strength(linkStrength))
      .force('charge', forceManyBody().strength(chargeOf).distanceMax(700))
      .force('collide', forceCollide().radius((d) => nodeRadius(d) + 4).iterations(1))
      .force('x', forceX(s.w / 2).strength(0.025))
      .force('y', forceY(s.h / 2).strength(0.025))
      .alphaDecay(0.035)
      .velocityDecay(0.38)
      .on('tick', requestDraw);

    if (first && nodes.length) {
      sim.stop();
      const ticks = Math.min(320, Math.ceil(Math.log(sim.alphaMin()) / Math.log(1 - sim.alphaDecay())));
      for (let i = 0; i < ticks; i += 1) sim.tick();
      s.fitted = true;
      fit();
    } else {
      sim.alpha(0.25).restart();
    }
    s.sim = sim;
    s.hover = null;
    setTip(null);
    requestDraw();
    return () => sim.stop();
  }, [topology, showOffline, showDevices, fit]);

  useEffect(() => { g.current.scanning = scanning; g.current.t0 = performance.now(); requestDraw(); }, [scanning]);
  useEffect(() => { g.current.selected = selectedId; requestDraw(); }, [selectedId]);

  // ---------- render loop ----------
  useEffect(() => {
    let raf;
    const reduced = prefersReducedMotion();
    const render = (now) => {
      raf = requestAnimationFrame(render);
      const s = g.current;
      const live = !reduced && s.nodes.length > 0; // keep the "ops room" alive even when nothing changed
      if (!s.dirty && !(s.scanning && !reduced) && !live) return;
      s.dirty = false;
      const c = canvasRef.current;
      if (!c) return;
      const ctx = c.getContext('2d');
      const dpr = window.devicePixelRatio || 1;
      ctx.setTransform(1, 0, 0, 1, 0, 0);
      ctx.clearRect(0, 0, c.width, c.height);
      if (!reduced) {
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        drawAmbient(ctx, s.w, s.h, now);
      }
      ctx.setTransform(dpr * s.k, 0, 0, dpr * s.k, dpr * s.x, dpr * s.y);

      // scan pulse
      if (s.scanning && !reduced) {
        const local = s.nodes.find((n) => n.type === 'LOCAL_HOST');
        if (local) {
          [0, 0.5].forEach((off) => {
            const t = (((now - s.t0) / 3400) + off) % 1;
            ctx.beginPath();
            ctx.arc(local.x, local.y, 24 + t * 420, 0, Math.PI * 2);
            ctx.strokeStyle = `rgba(124,196,255,${0.4 * (1 - t)})`;
            ctx.lineWidth = 1.6 / s.k;
            ctx.stroke();
          });
        }
      }

      const focusId = s.hover ? s.hover.id : s.selected;

      // edges
      for (const l of s.links) {
        const st = edgeStyle(l.edge);
        const hot = focusId && (l.source.id === focusId || l.target.id === focusId);
        ctx.beginPath();
        ctx.setLineDash(st.dash.map((d) => d / s.k));
        ctx.moveTo(l.source.x, l.source.y);
        ctx.lineTo(l.target.x, l.target.y);
        ctx.strokeStyle = hot ? 'rgba(124,196,255,0.9)' : st.color;
        ctx.lineWidth = (hot ? st.width + 0.8 : st.width) / s.k;
        ctx.stroke();
        if (!reduced) drawFlow(ctx, l, s, now);
      }
      ctx.setLineDash([]);

      // node glow (additive, drawn once behind everything so overlapping halos don't wash each other out)
      if (!reduced) {
        ctx.globalCompositeOperation = 'lighter';
        for (const n of s.nodes) drawGlow(ctx, n, s, now);
        ctx.globalCompositeOperation = 'source-over';
      }

      // nodes (small devices first so infrastructure is drawn on top)
      const order = [...s.nodes].sort((a, b) => nodeRadius(a) - nodeRadius(b));
      for (const n of order) drawNode(ctx, n, s, n.id === s.selected, s.hover && s.hover.id === n.id);
    };
    raf = requestAnimationFrame(render);
    return () => cancelAnimationFrame(raf);
  }, []);

  // ---------- interaction ----------
  const local = (e) => {
    const r = canvasRef.current.getBoundingClientRect();
    return { px: e.clientX - r.left, py: e.clientY - r.top };
  };
  const pick = (px, py) => {
    const s = g.current;
    if (!s.sim) return null;
    const wx = (px - s.x) / s.k, wy = (py - s.y) / s.k;
    let best = null, bestD = Infinity;
    for (const n of s.nodes) {
      const dx = n.x - wx, dy = n.y - wy;
      const d = Math.sqrt(dx * dx + dy * dy);
      const reach = Math.max(nodeRadius(n) + 3, 9 / s.k);
      if (d <= reach && d < bestD) { best = n; bestD = d; }
    }
    return best;
  };

  const onPointerDown = (e) => {
    const s = g.current;
    const { px, py } = local(e);
    canvasRef.current.setPointerCapture(e.pointerId);
    const n = pick(px, py);
    if (n) {
      s.drag = { node: n, moved: false, sx: px, sy: py };
      n.fx = n.x; n.fy = n.y;
      s.sim.alphaTarget(0.2).restart();
    } else {
      s.pan = { sx: px, sy: py, ox: s.x, oy: s.y, moved: false };
      canvasRef.current.classList.add('dragging');
    }
  };

  const onPointerMove = (e) => {
    const s = g.current;
    const { px, py } = local(e);
    if (s.drag) {
      if (Math.hypot(px - s.drag.sx, py - s.drag.sy) > 4) s.drag.moved = true;
      s.drag.node.fx = (px - s.x) / s.k;
      s.drag.node.fy = (py - s.y) / s.k;
      requestDraw();
    } else if (s.pan) {
      if (Math.hypot(px - s.pan.sx, py - s.pan.sy) > 4) s.pan.moved = true;
      s.x = s.pan.ox + (px - s.pan.sx);
      s.y = s.pan.oy + (py - s.pan.sy);
      requestDraw();
    } else {
      const n = pick(px, py);
      if ((n && n.id) !== (s.hover && s.hover.id)) { s.hover = n; requestDraw(); }
      setTip(n ? { node: n, x: px, y: py } : null);
    }
  };

  const onPointerUp = () => {
    const s = g.current;
    canvasRef.current.classList.remove('dragging');
    if (s.drag) {
      const { node, moved } = s.drag;
      node.fx = null; node.fy = null;
      s.sim.alphaTarget(0);
      s.drag = null;
      if (!moved && onSelect) onSelect(node);
    } else if (s.pan) {
      if (!s.pan.moved && onSelect) onSelect(null);
      s.pan = null;
    }
  };

  useEffect(() => {
    const c = canvasRef.current;
    const wheel = (e) => {
      e.preventDefault();
      const r = c.getBoundingClientRect();
      zoomAt(e.deltaY < 0 ? 1.15 : 1 / 1.15, e.clientX - r.left, e.clientY - r.top);
    };
    c.addEventListener('wheel', wheel, { passive: false });
    return () => c.removeEventListener('wheel', wheel);
  }, [zoomAt]);

  const counts = topology ? `${topology.nodes.length} nodes, ${topology.edges.length} links` : 'no data';

  return (
    <div className="topo-shell" ref={wrapRef}>
      <canvas
        ref={canvasRef}
        className="topo-canvas"
        role="img"
        aria-label={`Network topology graph with ${counts}. Use the Devices page for an accessible list.`}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerLeave={() => { g.current.hover = null; setTip(null); requestDraw(); }}
        onDoubleClick={fit}
      />
      <div className="topo-controls">
        <button className="btn small" onClick={() => zoomAt(1.3, g.current.w / 2, g.current.h / 2)} aria-label="Zoom in"><Icon name="plus" size={14} /></button>
        <button className="btn small" onClick={() => zoomAt(1 / 1.3, g.current.w / 2, g.current.h / 2)} aria-label="Zoom out"><Icon name="minus" size={14} /></button>
        <button className="btn small" onClick={fit}><Icon name="fit" size={14} /> Fit</button>
      </div>

      {tip && !g.current.drag && <Tip tip={tip} width={g.current.w} />}

      {(!topology || topology.nodes.length === 0) && (
        <div className="topo-empty">
          <div>
            <div style={{ fontSize: 15, color: 'var(--text)', marginBottom: 4 }}>{scanning ? 'Scanning the network…' : 'No devices discovered yet'}</div>
            <div>The graph appears as soon as the first scan finishes.</div>
          </div>
        </div>
      )}

      {legend && (
        <div className="topo-legend">
          <span className="live-tag"><span className="live-dot" />live</span>
          <span><i className="line solid" /> observed</span>
          <span><i className="line dashed" /> inferred (traceroute / IP subnet)</span>
          <span><i className="line dotted" /> assumed</span>
          <span><span className="dot ok" /> healthy</span>
          <span><span className="dot warn" /> degraded</span>
          <span><span className="dot bad" /> critical</span>
          <span><span className="dot" /> offline</span>
        </div>
      )}
    </div>
  );
}

function Tip({ tip, width }) {
  const n = tip.node;
  const left = tip.x + 16 + 260 > width ? tip.x - 270 : tip.x + 16;
  return (
    <div className="topo-tip" style={{ left, top: tip.y + 12 }}>
      <b>{n.label}</b>
      <div className="row"><span>Type</span><span>{TYPE_LABEL[n.type] || n.type}</span></div>
      {n.ip && <div className="row"><span>IP</span><span>{n.ip}</span></div>}
      {n.type === 'SUBNET' ? (
        <div className="row"><span>Online</span><span>{n.onlineCount} / {n.deviceCount}</span></div>
      ) : (
        <>
          <div className="row"><span>State</span><span>{STATE_LABEL[n.state] || n.state}</span></div>
          {n.latencyMs != null && <div className="row"><span>Ping</span><span>{fmtMs(n.latencyMs)}</span></div>}
        </>
      )}
      {n.healthStatus && n.type !== 'INTERNET' && <div className="row"><span>Health</span><span>{HEALTH_LABEL[n.healthStatus] || n.healthStatus}</span></div>}
      <div className="row"><span>Source</span><span style={{ fontFamily: 'var(--font)' }}>{n.source}</span></div>
    </div>
  );
}

// ---------------------------------------------------------------- drawing

function drawNode(ctx, n, s, selected, hovered) {
  const r = nodeRadius(n);
  const color = nodeColor(n);
  const k = s.k;
  const offline = n.state === 'UNREACHABLE';

  if (selected || hovered) {
    ctx.beginPath();
    ctx.arc(n.x, n.y, r + 5, 0, Math.PI * 2);
    ctx.strokeStyle = selected ? COLORS.accent : 'rgba(124,196,255,0.5)';
    ctx.lineWidth = 1.6 / k;
    ctx.stroke();
  }

  ctx.lineWidth = 1.6 / k;
  switch (n.type) {
    case 'SUBNET':
      ctx.beginPath(); ctx.arc(n.x, n.y, r, 0, Math.PI * 2);
      ctx.fillStyle = COLORS.panel; ctx.fill();
      ctx.lineWidth = 2.6 / k; ctx.strokeStyle = color; ctx.stroke();
      ctx.fillStyle = COLORS.text;
      ctx.font = `${Math.max(9, 11) / k}px "IBM Plex Mono", monospace`;
      ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
      ctx.fillText(String(n.deviceCount ?? ''), n.x, n.y + 0.5 / k);
      break;
    case 'LOCAL_HOST':
      ctx.beginPath(); ctx.arc(n.x, n.y, r, 0, Math.PI * 2);
      ctx.fillStyle = COLORS.accent; ctx.fill();
      ctx.beginPath(); ctx.arc(n.x, n.y, r * 0.42, 0, Math.PI * 2);
      ctx.fillStyle = COLORS.bg; ctx.fill();
      break;
    case 'GATEWAY':
      hexagon(ctx, n.x, n.y, r);
      ctx.fillStyle = offline ? COLORS.bg : color; ctx.fill();
      ctx.strokeStyle = color; ctx.stroke();
      break;
    case 'ROUTER':
      roundRect(ctx, n.x - r, n.y - r, r * 2, r * 2, 3 / k);
      ctx.fillStyle = COLORS.bg; ctx.fill();
      ctx.strokeStyle = n.healthStatus === 'UNKNOWN' ? COLORS.accent : color; ctx.lineWidth = 2 / k; ctx.stroke();
      break;
    case 'INTERNET':
      ctx.beginPath(); ctx.arc(n.x, n.y, r, 0, Math.PI * 2);
      ctx.setLineDash([3 / k, 3 / k]); ctx.strokeStyle = COLORS.accent; ctx.lineWidth = 1.6 / k; ctx.stroke(); ctx.setLineDash([]);
      break;
    case 'NETWORK_DEVICE':
      roundRect(ctx, n.x - r, n.y - r, r * 2, r * 2, 2 / k);
      ctx.fillStyle = offline ? COLORS.bg : color; ctx.fill(); ctx.strokeStyle = color; ctx.stroke();
      break;
    case 'PRINTER':
      ctx.beginPath();
      ctx.moveTo(n.x, n.y - r - 1.5); ctx.lineTo(n.x + r + 1.5, n.y); ctx.lineTo(n.x, n.y + r + 1.5); ctx.lineTo(n.x - r - 1.5, n.y); ctx.closePath();
      ctx.fillStyle = offline ? COLORS.bg : color; ctx.fill(); ctx.strokeStyle = color; ctx.stroke();
      break;
    default:
      ctx.beginPath(); ctx.arc(n.x, n.y, r, 0, Math.PI * 2);
      ctx.fillStyle = offline || n.state === 'UNKNOWN' ? COLORS.bg : color; ctx.fill();
      ctx.strokeStyle = color; ctx.stroke();
  }

  // labels
  const major = isInfra(n);
  if (major || k >= 1.6 || selected || hovered) {
    ctx.font = `${(major ? 11.5 : 10.5) / k}px "IBM Plex Mono", monospace`;
    ctx.textAlign = 'center'; ctx.textBaseline = 'top';
    const text = n.label.length > 26 ? `${n.label.slice(0, 25)}…` : n.label;
    const y = n.y + r + 5 / k;
    ctx.lineWidth = 3 / k; ctx.strokeStyle = COLORS.bg; ctx.strokeText(text, n.x, y);
    ctx.fillStyle = major ? COLORS.text : COLORS.dim; ctx.fillText(text, n.x, y);
  }
}

function hexagon(ctx, x, y, r) {
  ctx.beginPath();
  for (let i = 0; i < 6; i += 1) {
    const a = (Math.PI / 3) * i + Math.PI / 6;
    const px = x + r * Math.cos(a), py = y + r * Math.sin(a);
    if (i === 0) ctx.moveTo(px, py); else ctx.lineTo(px, py);
  }
  ctx.closePath();
}

function roundRect(ctx, x, y, w, h, r) {
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + w, y, x + w, y + h, r);
  ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r);
  ctx.arcTo(x, y, x + w, y, r);
  ctx.closePath();
}

// ---------------------------------------------------------------- ops-room ambience

/** Screen-space (not affected by pan/zoom): faint grid rings + a slow rotating radar sweep, like a live ops-room screen. */
function drawAmbient(ctx, w, h, now) {
  const cx = w / 2, cy = h / 2;
  const R = Math.max(w, h) * 0.78;

  ctx.strokeStyle = 'rgba(124,196,255,0.055)';
  ctx.lineWidth = 1;
  for (let i = 1; i <= 3; i += 1) {
    ctx.beginPath();
    ctx.arc(cx, cy, (R / 3) * i, 0, Math.PI * 2);
    ctx.stroke();
  }
  ctx.beginPath();
  ctx.moveTo(cx - R, cy); ctx.lineTo(cx + R, cy);
  ctx.moveTo(cx, cy - R); ctx.lineTo(cx, cy + R);
  ctx.strokeStyle = 'rgba(124,196,255,0.03)';
  ctx.stroke();

  const angle = ((now / 7000) % 1) * Math.PI * 2;
  const trail = 1.15, steps = 22;
  for (let i = 0; i < steps; i += 1) {
    const a0 = angle - (trail * i) / steps;
    const a1 = angle - (trail * (i + 1)) / steps;
    ctx.beginPath();
    ctx.moveTo(cx, cy);
    ctx.arc(cx, cy, R, a1, a0);
    ctx.closePath();
    ctx.fillStyle = `rgba(124,196,255,${0.05 * (1 - i / steps)})`;
    ctx.fill();
  }
}

/** Soft pulsing halo behind infrastructure / unhealthy nodes - faster, brighter pulse the worse the health. */
function drawGlow(ctx, n, s, now) {
  const worrying = n.healthStatus === 'CRITICAL' || n.healthStatus === 'DEGRADED';
  if (!isInfra(n) && n.type !== 'LOCAL_HOST' && !worrying) return;
  const color = nodeColor(n);
  const r = nodeRadius(n);
  const period = n.healthStatus === 'CRITICAL' ? 900 : n.healthStatus === 'DEGRADED' ? 1500 : 2800;
  const phase = (now % period) / period;
  const glowR = r * (2.1 + Math.sin(phase * Math.PI * 2) * (worrying ? 0.55 : 0.3));
  const grad = ctx.createRadialGradient(n.x, n.y, 0, n.x, n.y, Math.max(1, glowR));
  grad.addColorStop(0, hexToRgba(color, worrying ? 0.45 : 0.28));
  grad.addColorStop(1, hexToRgba(color, 0));
  ctx.beginPath();
  ctx.arc(n.x, n.y, Math.max(1, glowR), 0, Math.PI * 2);
  ctx.fillStyle = grad;
  ctx.fill();
}

/**
 * Small dots travelling source -> target, only on links we actually have evidence for (OBSERVED/INFERRED).
 * ASSUMED/CONCEPTUAL edges are deliberately left static - NetScope doesn't animate a connection it can't prove.
 */
function drawFlow(ctx, l, s, now) {
  const conf = l.edge.confidence;
  if (conf === 'ASSUMED' || conf === 'CONCEPTUAL') return;
  const st = edgeStyle(l.edge);
  const dx = l.target.x - l.source.x, dy = l.target.y - l.source.y;
  const len = Math.hypot(dx, dy);
  if (len < 6) return;
  const speed = conf === 'OBSERVED' ? 1300 : 2100;
  const count = Math.max(1, Math.min(3, Math.round(len / 90)));
  const offset = hash01(String(l.edge.source) + String(l.edge.target));
  const particleColor = st.color.replace(/[\d.]+\)$/, '0.95)');
  for (let i = 0; i < count; i += 1) {
    const phase = ((now / speed) + offset + i / count) % 1;
    const px = l.source.x + dx * phase;
    const py = l.source.y + dy * phase;
    ctx.beginPath();
    ctx.arc(px, py, 1.7 / s.k, 0, Math.PI * 2);
    ctx.fillStyle = particleColor;
    ctx.fill();
  }
}
