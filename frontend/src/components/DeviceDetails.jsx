import { useEffect, useRef, useState } from 'react';
import Icon from './Icons';
import DiagnosticPanel from './DiagnosticPanel';
import { HealthPill, LatencyChart, Sparkline, StatePill } from './Badges';
import { diagnoseDevice, getHistory } from '../services/deviceService';
import { TYPE_LABEL, fmtDateTime, fmtMs, fmtPct, healthClass, relTime } from '../utils/formatters';

/** Slide-in panel with everything NetScope knows about one device + an on-demand deep test. */
export default function DeviceDetails({ device, onClose }) {
  const [history, setHistory] = useState([]);
  const [deep, setDeep] = useState({ loading: false, result: null, error: null });
  const closeRef = useRef(null);
  const ip = device?.ip;

  useEffect(() => {
    if (!ip) return undefined;
    let alive = true;
    setHistory([]);
    setDeep({ loading: false, result: null, error: null });
    getHistory(ip).then((h) => alive && setHistory(h)).catch(() => {});
    closeRef.current?.focus();
    const onKey = (e) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    return () => { alive = false; window.removeEventListener('keydown', onKey); };
  }, [ip, onClose]);

  if (!device) return null;
  const d = device;

  const runDeep = async (trace) => {
    setDeep({ loading: true, result: null, error: null });
    try {
      setDeep({ loading: false, result: await diagnoseDevice(d.ip, { count: 8, trace }), error: null });
    } catch (e) {
      setDeep({ loading: false, result: null, error: e.message });
    }
  };

  return (
    <>
      <div className="scrim" onClick={onClose} />
      <aside className="drawer" role="dialog" aria-label={`Details for ${d.ip}`}>
        <div className="drawer-head">
          <div style={{ flex: 1, minWidth: 0 }}>
            <h2>{d.hostname || d.ip}</h2>
            <div className="dim mono" style={{ marginTop: 2 }}>{d.ip}{d.hostname ? '' : ''}</div>
            <div style={{ display: 'flex', gap: 8, marginTop: 8, flexWrap: 'wrap' }}>
              <StatePill state={d.state} />
              <HealthPill status={d.healthStatus} score={d.healthScore} />
              <span className="pill">{TYPE_LABEL[d.deviceType] || d.deviceType}</span>
            </div>
          </div>
          <button ref={closeRef} className="btn small ghost" onClick={onClose} aria-label="Close details"><Icon name="close" size={15} /></button>
        </div>

        <div className="drawer-body">
          <section>
            <h3>Diagnosis</h3>
            <div className={`diagnosis ${healthClass(d.healthStatus)}`}>{d.diagnosis || 'No diagnosis yet.'}</div>
          </section>

          {d.issues.length > 0 && (
            <section>
              <h3>Issues</h3>
              {d.issues.map((i) => (
                <div className="issue" key={i.code}>
                  <div className="issue-title">
                    <span className={`dot ${i.severity === 'CRITICAL' ? 'bad' : i.severity === 'WARNING' ? 'warn' : ''}`} style={i.severity === 'INFO' ? { background: 'var(--accent)' } : undefined} />
                    {i.title}
                  </div>
                  <p>{i.detail}</p>
                  {i.recommendation && <p className="reco">Next step: {i.recommendation}</p>}
                </div>
              ))}
            </section>
          )}

          <section>
            <h3>Measurements</h3>
            <div className="metrics">
              <div className="metric"><div className="l">Ping now</div><div className="v">{d.state === 'REACHABLE' ? fmtMs(d.latencyMs, 1) : '—'}</div></div>
              <div className="metric"><div className="l">Average</div><div className="v">{fmtMs(d.avgLatencyMs, 1)}</div></div>
              <div className="metric"><div className="l">Jitter</div><div className="v">{fmtMs(d.jitterMs, 1)}</div></div>
              <div className="metric"><div className="l">Packet loss</div><div className="v">{fmtPct(d.packetLossPercent)}</div></div>
              <div className="metric"><div className="l">TTL</div><div className="v">{d.ttl ?? '—'}</div></div>
              <div className="metric"><div className="l">Health score</div><div className="v">{d.healthScore}</div></div>
            </div>
            <div style={{ marginTop: 10, display: 'flex', alignItems: 'center', gap: 10 }}>
              <span className="dim" style={{ fontSize: 12.5 }}>Last probes</span>
              <Sparkline values={d.latencyHistory} width={200} height={26} />
            </div>
          </section>

          <section>
            <h3>Latency history</h3>
            <LatencyChart points={history} />
          </section>

          <section>
            <h3>Identity</h3>
            <dl className="kv">
              <dt>Hostname</dt><dd>{d.hostname || '—'}{d.hostnameSource ? <span className="faint"> ({d.hostnameSource})</span> : null}</dd>
              <dt>MAC address</dt><dd className="mono">{d.mac || '—'}</dd>
              <dt>Vendor</dt><dd>{d.vendor || '—'}<div className="faint" style={{ fontSize: 12 }}>{d.vendorNote}</div></dd>
              <dt>Type</dt><dd>{TYPE_LABEL[d.deviceType] || d.deviceType}<div className="faint" style={{ fontSize: 12 }}>{d.classificationReason}</div></dd>
              <dt>OS hint</dt><dd>{d.osHint || '—'}</dd>
              <dt>Subnet</dt><dd className="mono">{d.subnet}</dd>
              <dt>Seen via</dt><dd>{d.source || '—'}{d.sameSegment ? ' · same Layer-2 segment' : ' · routed (behind a router)'}</dd>
              <dt>First seen</dt><dd>{fmtDateTime(d.firstSeen)}</dd>
              <dt>Last seen</dt><dd>{fmtDateTime(d.lastSeen)} <span className="faint">({relTime(d.lastSeen)})</span></dd>
            </dl>
          </section>

          {!d.local && (
            <section>
              <h3>Deep test</h3>
              <div className="toolbar" style={{ marginBottom: 10 }}>
                <button className="btn small" onClick={() => runDeep(false)} disabled={deep.loading}>Ping test (8 packets)</button>
                <button className="btn small" onClick={() => runDeep(true)} disabled={deep.loading}>Ping + route trace</button>
              </div>
              {(deep.loading || deep.result || deep.error) && <DiagnosticPanel result={deep.result} loading={deep.loading} error={deep.error} compact />}
            </section>
          )}
        </div>
      </aside>
    </>
  );
}
