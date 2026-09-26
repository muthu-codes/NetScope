import { useEffect, useState } from 'react';
import Icon from './Icons';
import { fmtMs, relTime } from '../utils/formatters';
import { zoneDevices } from '../services/zoneService';

const STATE_TONE = { UP: 'online', DEGRADED: 'tone-degraded', DOWN: 'offline', UNKNOWN: '' };

export default function ZoneDevices({ zone, onClose }) {
  const [devices, setDevices] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!zone) return undefined;
    let alive = true;
    setLoading(true);
    zoneDevices(zone.id).then((d) => alive && setDevices(d)).catch((e) => alive && setError(e.message)).finally(() => alive && setLoading(false));
    return () => { alive = false; };
  }, [zone]);

  if (!zone) return null;

  return (
    <>
      <div className="scrim" onClick={onClose} />
      <aside className="drawer" role="dialog" aria-label={`Devices in ${zone.name}`} style={{ width: 620 }}>
        <div className="drawer-head">
          <div style={{ flex: 1 }}>
            <h2>{zone.name}</h2>
            <div className="dim mono" style={{ marginTop: 2 }}>{zone.cidr}</div>
          </div>
          <button type="button" className="btn small ghost" onClick={onClose} aria-label="Close"><Icon name="close" /></button>
        </div>
        <div className="drawer-body">
          {loading ? <div className="empty"><span className="spinner" /> Loading devices…</div>
            : error ? <div className="banner bad">{error}</div>
            : !devices.length ? <div className="empty">No devices recorded yet. Run a scan first.</div>
            : (
              <div className="table-wrap">
                <table className="table">
                  <thead><tr><th>IP</th><th>Hostname</th><th>MAC</th><th>State</th><th>Method</th><th className="num">Latency</th><th>Open ports</th><th>Last seen</th></tr></thead>
                  <tbody>
                    {devices.map((d) => (
                      <tr key={d.ip} style={{ cursor: 'default' }}>
                        <td className="mono">{d.ip}</td>
                        <td>{d.hostname || '—'}</td>
                        <td className="mono" title={d.macNote || ''}>{d.mac || (d.macNote ? <span className="faint">n/a</span> : '—')}</td>
                        <td><span className={`pill ${STATE_TONE[d.state] || ''}`}><span className="dot" />{d.state}</span></td>
                        <td>{d.discoveryMethod || '—'}</td>
                        <td className="num">{fmtMs(d.latencyMs)}</td>
                        <td className="mono">{d.openPorts?.length ? d.openPorts.join(', ') : '—'}</td>
                        <td>{relTime(d.lastSeen)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          <p className="dim" style={{ fontSize: 12.5, marginTop: 12 }}>
            MAC addresses cannot normally be learned across a router - remote devices show "n/a" unless nmap/SNMP infrastructure data is added later.
          </p>
        </div>
      </aside>
    </>
  );
}
