import { HEALTH_LABEL, STATE_LABEL, healthClass } from '../utils/formatters';

export function StatePill({ state }) {
  const cls = state === 'REACHABLE' ? 'online' : state === 'ARP_ONLY' ? 'arp' : state === 'UNREACHABLE' ? 'offline' : '';
  const dot = state === 'REACHABLE' ? 'ok' : state === 'ARP_ONLY' ? 'limited' : '';
  return (
    <span className={`pill ${cls}`}>
      <span className={`dot ${dot}`} />
      {STATE_LABEL[state] || state}
    </span>
  );
}

export function HealthPill({ status, score }) {
  return (
    <span className={`pill ${healthClass(status)}`} title={score !== undefined && score !== null ? `Health score ${score}/100` : undefined}>
      <span className={`dot ${healthClass(status)}`} style={{ background: 'currentColor' }} />
      {HEALTH_LABEL[status] || status}
    </span>
  );
}

/** Tiny latency trend. null values are lost probes (drawn as red ticks). */
export function Sparkline({ values = [], width = 84, height = 22 }) {
  const nums = values.filter((v) => v !== null && v !== undefined);
  if (values.length < 2 || nums.length === 0) return <span className="faint">—</span>;
  const max = Math.max(...nums, 5);
  const step = width / (values.length - 1);
  const y = (v) => height - 3 - (v / max) * (height - 8);
  let d = '';
  let pen = false;
  values.forEach((v, i) => {
    if (v === null || v === undefined) { pen = false; return; }
    d += `${pen ? 'L' : 'M'}${(i * step).toFixed(1)} ${y(v).toFixed(1)} `;
    pen = true;
  });
  return (
    <svg className="spark" width={width} height={height} role="img" aria-label="Recent latency trend">
      <path d={d} fill="none" stroke="#7cc4ff" strokeWidth="1.4" strokeLinejoin="round" />
      {values.map((v, i) => (v === null || v === undefined ? (
        <rect key={i} x={i * step - 1} y={height - 5} width="2" height="5" fill="#ff6b6b" />
      ) : null))}
    </svg>
  );
}

/** Latency over time from stored observations. */
export function LatencyChart({ points = [] }) {
  const W = 480, H = 120, PAD = 8;
  if (points.length < 2) return <div className="chart empty" style={{ display: 'grid', placeItems: 'center' }}>Not enough history yet</div>;
  const lat = points.map((p) => p.latencyMs).filter((v) => v !== null && v !== undefined);
  const max = Math.max(...lat, 10) * 1.15;
  const step = (W - PAD * 2) / (points.length - 1);
  const y = (v) => H - PAD - (v / max) * (H - PAD * 2);
  let d = '';
  let pen = false;
  points.forEach((p, i) => {
    if (p.latencyMs === null || p.latencyMs === undefined) { pen = false; return; }
    d += `${pen ? 'L' : 'M'}${(PAD + i * step).toFixed(1)} ${y(p.latencyMs).toFixed(1)} `;
    pen = true;
  });
  return (
    <svg className="chart" viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" role="img" aria-label="Latency history">
      {[0.25, 0.5, 0.75].map((f) => (
        <line key={f} x1="0" x2={W} y1={H * f} y2={H * f} stroke="#22314a" strokeDasharray="2 4" />
      ))}
      <path d={d} fill="none" stroke="#7cc4ff" strokeWidth="1.6" vectorEffect="non-scaling-stroke" />
      {points.map((p, i) => (!p.reachable ? (
        <circle key={i} cx={PAD + i * step} cy={H - PAD} r="3" fill="#ff6b6b" />
      ) : null))}
      <text x={W - 4} y="12" fill="#8798b2" fontSize="10" textAnchor="end">{Math.round(max)} ms</text>
    </svg>
  );
}

/** Signal strength as four bars + percentage. */
export function SignalBar({ percent }) {
  if (percent === null || percent === undefined) return <span className="faint">—</span>;
  const bars = percent >= 80 ? 4 : percent >= 60 ? 3 : percent >= 40 ? 2 : 1;
  const color = percent >= 60 ? 'var(--ok)' : percent >= 40 ? 'var(--warn)' : 'var(--bad)';
  return (
    <span className="signal" title={`${percent}% signal`}>
      <span className="signal-bars" aria-hidden="true">
        {[1, 2, 3, 4].map((i) => (
          <i key={i} style={{ height: 3 + i * 3, background: i <= bars ? color : 'var(--line-strong)' }} />
        ))}
      </span>
      <span className="mono">{percent}%</span>
    </span>
  );
}
