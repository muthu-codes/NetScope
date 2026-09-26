import Icon from './Icons';

export default function Header({ title, scopeText, scan, monitoring, connected, onToggleMonitoring, onScan, scanBusy }) {
  const pct = scan.running && scan.total > 0 ? Math.min(100, Math.round((scan.done / scan.total) * 100)) : null;
  const enabled = monitoring ? monitoring.enabled : true;
  return (
    <header className="header">
      <div className="header-title">
        <h1>{title}</h1>
        {scopeText && (
          <div className="scope-chip" title="Only addresses inside this authorized scope are ever probed">
            <Icon name="shield" size={13} /> <span>Scope</span> <span className="mono">{scopeText}</span>
          </div>
        )}
      </div>

      {scan.running && (
        <div className="header-scan" aria-live="polite">
          <div className="dim" style={{ fontSize: 12.5 }}>
            {scan.phase}
            {pct !== null && <span className="mono"> · {scan.done.toLocaleString()} / {scan.total.toLocaleString()} ({pct}%)</span>}
          </div>
          <div className={`scan-bar ${pct === null ? 'indeterminate' : ''}`}>
            <span style={pct === null ? undefined : { width: `${pct}%` }} />
          </div>
        </div>
      )}

      <div className="header-actions">
        <span className="live" title={connected ? 'Live updates connected' : 'Reconnecting to live updates...'}>
          <span className={`dot ${connected ? 'ok pulse' : 'warn'}`} />
          {connected ? 'Live' : 'Reconnecting'}
        </span>
        <label className="switch">
          <input type="checkbox" checked={enabled} onChange={(e) => onToggleMonitoring(e.target.checked)} />
          <span className="track" />
          Monitoring {enabled ? 'on' : 'off'}
        </label>
        <button className="btn primary" onClick={onScan} disabled={scanBusy || scan.running}>
          <Icon name="scan" size={15} /> {scan.running ? 'Scanning…' : 'Scan now'}
        </button>
      </div>
    </header>
  );
}
