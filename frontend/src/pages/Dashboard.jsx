import { useMemo } from 'react';
import NetworkStatus from '../components/NetworkStatus';
import StatCard from '../components/StatCard';
import TopologyGraph from '../components/TopologyGraph';
import EventTimeline from '../components/EventTimeline';
import Icon from '../components/Icons';
import { fmtDuration, fmtMs, healthClass, relTime, compareIp } from '../utils/formatters';
import '../styles/dashboard.css';

const PRIORITY = (d) => (d.gateway ? 0 : d.deviceType === 'NETWORK_DEVICE' ? 1 : d.healthStatus === 'CRITICAL' ? 2 : d.healthStatus === 'DEGRADED' ? 3 : 4);

export default function Dashboard({ summary, topology, events, devices, scan, onSelectIp, selectedIp, onOpenPage, zonesOverview }) {
  const stats = summary?.devices;
  const cfg = summary?.configuration;
  const mon = summary?.monitoring;

  const attention = useMemo(
    () => devices
      .filter((d) => !d.local && ['CRITICAL', 'DEGRADED', 'OFFLINE'].includes(d.healthStatus))
      .sort((a, b) => PRIORITY(a) - PRIORITY(b) || a.healthScore - b.healthScore)
      .slice(0, 12),
    [devices]
  );

  const subnets = useMemo(() => {
    const map = new Map();
    devices.forEach((d) => {
      if (!d.subnet) return;
      const s = map.get(d.subnet) || { subnet: d.subnet, total: 0, online: 0, offline: 0, bad: 0, lat: 0, latN: 0 };
      s.total += 1;
      if (d.state === 'REACHABLE' || d.state === 'ARP_ONLY') s.online += 1;
      if (d.state === 'UNREACHABLE') s.offline += 1;
      if (d.healthStatus === 'CRITICAL' || d.healthStatus === 'DEGRADED') s.bad += 1;
      if (d.state === 'REACHABLE' && !d.local && d.latencyMs != null) { s.lat += d.latencyMs; s.latN += 1; }
      map.set(d.subnet, s);
    });
    return [...map.values()].sort((a, b) => compareIp(a.subnet.split('/')[0], b.subnet.split('/')[0]));
  }, [devices]);

  const onNodeSelect = (node) => {
    if (node && devices.some((d) => d.ip === node.id)) onSelectIp(node.id);
  };

  return (
    <>
      {summary && !summary.scope.valid && (
        <div className="banner bad" role="alert"><Icon name="alert" /><div><b>Scanning is blocked:</b> {summary.scope.message}</div></div>
      )}
      {mon?.lastError && summary?.scope.valid && (
        <div className="banner warn" role="alert"><Icon name="alert" /><div><b>Last scan failed:</b> {mon.lastError}</div></div>
      )}
      {cfg?.warnings?.map((w) => (
        <div className="banner info" key={w}><Icon name="about" /><div>{w}</div></div>
      ))}

      <NetworkStatus summary={summary} />

      <div className="kpi-strip">
        <StatCard label="This computer" value={cfg?.ipv4 || '—'} sub={cfg ? `${cfg.interfaceType?.toLowerCase() || ''} · ${cfg.hostname}` : ''} />
        <StatCard label="Gateway" value={cfg?.gatewayIp || '—'} sub={cfg?.gatewayIp ? (cfg.gatewayMac ? `MAC ${cfg.gatewayMac}` : 'MAC not visible') : 'no default route'} />
        <StatCard label="Devices seen" value={stats ? stats.total.toLocaleString() : '—'} sub={stats ? `${stats.subnets} subnet${stats.subnets === 1 ? '' : 's'}` : ''} />
        <StatCard label="Online" value={stats ? (stats.reachable + stats.arpOnly).toLocaleString() : '—'} sub={stats ? `${stats.unreachable} offline · ${stats.arpOnly} ping-blocked` : ''} tone={stats && stats.unreachable > 0 ? undefined : undefined} />
        <StatCard label="Average ping" value={stats ? fmtMs(stats.avgLatencyMs, 1) : '—'}
                  sub={stats ? `${stats.critical} critical · ${stats.degraded} degraded` : ''}
                  tone={stats?.critical > 0 ? 'critical' : stats?.degraded > 0 ? 'degraded' : undefined} />
        <StatCard label="Last full scan" value={mon?.lastFullScanAt ? relTime(mon.lastFullScanAt) : 'not yet'}
                  sub={mon?.lastScanDurationMs ? `took ${fmtDuration(mon.lastScanDurationMs)}` : (scan.running ? scan.phase : '')} />
      </div>

      {zonesOverview && zonesOverview.zones > 1 && (
        <section className="panel" aria-label="Network zones health">
          <div className="panel-head">
            <h2>Zone health</h2><span className="spacer" />
            <button className="btn small ghost" onClick={() => onOpenPage('zones')}>Open Network Zones</button>
          </div>
          <div className="panel-body" style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>
            {zonesOverview.zoneHealth.map((z) => (
              <span key={z.id} className={`pill ${z.status === 'MONITORING' ? 'tone-healthy' : z.status === 'DEGRADED' ? 'tone-degraded' : z.status === 'UNREACHABLE' ? 'tone-critical' : ''}`}>
                <span className="dot" />{z.name} · {z.up}/{z.up + z.degraded + z.down || 1}
              </span>
            ))}
            {zonesOverview.activeIncidents > 0 && <span className="pill tone-critical">{zonesOverview.activeIncidents} active incident{zonesOverview.activeIncidents === 1 ? '' : 's'}</span>}
          </div>
        </section>
      )}

      <div className="dash-grid">
        <section className="panel" aria-label="Network topology">
          <div className="panel-head">
            <h2>Network topology</h2>
            <span className="dim" style={{ fontSize: 12.5 }}>observed / inferred from live data</span>
            <span className="spacer" />
            <button className="btn small ghost" onClick={() => onOpenPage('topology')}>Open full view</button>
          </div>
          <TopologyGraph topology={topology} scanning={scan.running} height={470} selectedId={selectedIp} onSelect={onNodeSelect} />
        </section>

        <section className="panel" aria-label="Devices needing attention">
          <div className="panel-head">
            <h2>Needs attention</h2>
            <span className="spacer" />
            <span className="dim" style={{ fontSize: 12.5 }}>{attention.length ? `${attention.length} shown` : ''}</span>
          </div>
          {attention.length === 0 ? (
            <div className="empty">{stats && stats.total > 1 ? 'Nothing needs attention. All observed devices are healthy.' : 'Waiting for scan results.'}</div>
          ) : (
            <div className="attention">
              {attention.map((d) => (
                <button key={d.ip} className="attention-item" onClick={() => onSelectIp(d.ip)}>
                  <span className={`dot ${healthClass(d.healthStatus)}`} />
                  <div>
                    <div className="attention-title">
                      <span className="mono">{d.ip}</span>
                      {d.hostname && <span>{d.hostname}</span>}
                      <span className={healthClass(d.healthStatus)} style={{ fontSize: 12 }}>{d.healthStatus.toLowerCase()}</span>
                    </div>
                    <div className="attention-text">{d.diagnosis}</div>
                  </div>
                </button>
              ))}
            </div>
          )}
        </section>
      </div>

      <div className="dash-grid-2">
        <section className="panel">
          <div className="panel-head"><h2>Subnets</h2><span className="spacer" /><span className="dim" style={{ fontSize: 12.5 }}>{subnets.length} observed</span></div>
          {subnets.length === 0 ? <div className="empty">No subnets yet.</div> : (
            <div style={{ overflow: 'auto', maxHeight: 420 }}>
              <table className="subnet-table">
                <thead><tr><th>Subnet</th><th>Online</th><th style={{ minWidth: 110 }}>Availability</th><th>Problems</th><th>Avg ping</th></tr></thead>
                <tbody>
                  {subnets.map((s) => (
                    <tr key={s.subnet}>
                      <td className="mono">{s.subnet}</td>
                      <td>{s.online} / {s.total}</td>
                      <td>
                        <div className="bar" aria-hidden="true">
                          <span style={{ width: `${(s.online / s.total) * 100}%`, background: 'var(--ok)' }} />
                          <span style={{ width: `${(s.offline / s.total) * 100}%`, background: 'var(--off)' }} />
                        </div>
                      </td>
                      <td className={s.bad ? 'tone-degraded' : 'dim'}>{s.bad || '—'}</td>
                      <td className="mono">{s.latN ? fmtMs(s.lat / s.latN, 1) : '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>

        <section className="panel">
          <div className="panel-head">
            <h2>Recent events</h2><span className="spacer" />
            <button className="btn small ghost" onClick={() => onOpenPage('events')}>All events</button>
          </div>
          <EventTimeline events={events} limit={30} onSelectIp={onSelectIp} />
        </section>
      </div>
    </>
  );
}
