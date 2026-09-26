import { fmtMs, healthClass } from '../utils/formatters';

const OVERALL = { HEALTHY: 'healthy', DEGRADED: 'degraded', CRITICAL: 'critical' };

/** Renders a DiagnosticResponse from /api/diagnostics/network or /api/diagnostics/device. */
export default function DiagnosticPanel({ result, loading, error, onSelectIp, compact = false }) {
  if (loading) return <div className="empty"><span className="spinner" /> Running checks… this can take up to 20 seconds.</div>;
  if (error) return <div className="empty error-text">{error}</div>;
  if (!result) return <div className="empty">No diagnostics have been run yet.</div>;
  const s = result.pingStats;
  return (
    <div>
      <div style={{ padding: compact ? '0 0 10px' : '14px 16px', display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
        <span className={`pill ${healthClass(OVERALL[result.overallStatus])}`}>
          <span className="dot" style={{ background: 'currentColor' }} /> {result.overallStatus.toLowerCase()}
        </span>
        <span>{result.summary}</span>
      </div>

      {s && (
        <div className="metrics" style={compact ? undefined : { margin: '0 16px 12px' }}>
          <div className="metric"><div className="l">Replies</div><div className="v">{s.received}/{s.sent}</div></div>
          <div className="metric"><div className="l">Loss</div><div className="v">{s.lossPercent}%</div></div>
          <div className="metric"><div className="l">Average</div><div className="v">{fmtMs(s.avgMs, 1)}</div></div>
          <div className="metric"><div className="l">Min</div><div className="v">{fmtMs(s.minMs, 1)}</div></div>
          <div className="metric"><div className="l">Max</div><div className="v">{fmtMs(s.maxMs, 1)}</div></div>
          <div className="metric"><div className="l">Jitter</div><div className="v">{fmtMs(s.jitterMs, 1)}</div></div>
        </div>
      )}

      <div className="checks" style={compact ? { border: '1px solid var(--line)', borderRadius: 'var(--radius)' } : undefined}>
        {result.checks.map((c) => (
          <div className="check" key={c.id}>
            <span className={`check-status ${c.status}`}>{c.status}</span>
            <div>
              <div className="check-name">{c.name}: <span style={{ fontWeight: 400 }}>{c.summary}</span></div>
              {c.detail && <div className="check-detail">{c.detail}</div>}
              {c.recommendation && <div className="check-reco">{c.recommendation}</div>}
            </div>
            <div className="check-ms">{c.latencyMs != null ? fmtMs(c.latencyMs, 0) : ''}</div>
          </div>
        ))}
      </div>

      {result.path && result.path.length > 0 && (
        <div style={{ marginTop: 12, padding: compact ? 0 : '0 16px 14px' }}>
          <h3 className="dim" style={{ marginBottom: 6 }}>Route taken (traceroute)</h3>
          <div className="pre">{result.path.join('\n')}</div>
        </div>
      )}

      {result.findings && result.findings.length > 0 && (
        <div style={{ marginTop: 14 }}>
          <h3 className="dim" style={{ padding: '0 16px 6px' }}>Devices needing attention</h3>
          {result.findings.map((f) => (
            <button key={f.ip} className="attention-item" onClick={() => onSelectIp && onSelectIp(f.ip)}>
              <span className={`dot ${healthClass(f.healthStatus)}`} />
              <div>
                <div className="attention-title">
                  <span className="mono">{f.ip}</span>
                  {f.name !== f.ip && <span>{f.name}</span>}
                  <span className={`${healthClass(f.healthStatus)}`} style={{ fontSize: 12 }}>{f.healthStatus.toLowerCase()}</span>
                </div>
                <div className="attention-text">{f.diagnosis}</div>
              </div>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
