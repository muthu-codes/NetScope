import { useState } from 'react';
import Icon from '../components/Icons';
import StatCard from '../components/StatCard';
import ZoneForm from '../components/ZoneForm';
import ZoneDevices from '../components/ZoneDevices';
import { fmtMs, relTime } from '../utils/formatters';
import { createZone, updateZone, deleteZone, enableZone, scanZone, nmapScanZone } from '../services/zoneService';

const STATUS_TONE = {
  MONITORING: 'online', IDLE: '', DISCOVERING: 'tone-degraded', DEGRADED: 'tone-degraded',
  UNREACHABLE: 'tone-critical', ERROR: 'tone-critical'
};

export default function NetworkZones({ overview, zones, incidents, onRefresh }) {
  const [editing, setEditing] = useState(undefined); // undefined = closed, null = new, object = edit
  const [viewing, setViewing] = useState(null);
  const [busy, setBusy] = useState(null);
  const [notice, setNotice] = useState(null);

  const doAction = async (fn, id) => {
    setBusy(id);
    try {
      await fn(id);
      await onRefresh();
    } catch (e) {
      setNotice(e.message);
    } finally {
      setBusy(null);
    }
  };

  const save = async (form) => {
    if (editing) await updateZone(editing.id, form); else await createZone(form);
    setEditing(undefined);
    await onRefresh();
  };

  return (
    <>
      {notice && <div className="banner warn" role="alert"><Icon name="alert" /><div>{notice}</div></div>}

      {overview && (
        <section className="panel">
          <div className="panel-head"><h2>Network overview</h2></div>
          <div className="kpi-strip">
            <StatCard label="Zones" value={overview.zones} />
            <StatCard label="Devices" value={overview.devices} />
            <StatCard label="Online" value={overview.online} tone="healthy" />
            <StatCard label="Degraded" value={overview.degraded} tone="degraded" />
            <StatCard label="Offline" value={overview.offline} tone="critical" />
            <StatCard label="Services" value={overview.services} />
            <StatCard label="Active incidents" value={overview.activeIncidents} tone={overview.activeIncidents ? 'critical' : 'healthy'} />
          </div>
          <div className="panel-body dim" style={{ fontSize: 12.5 }}>
            {overview.nmapAvailable ? `nmap detected (${overview.nmapVersion}) - deeper scans available per zone.`
              : 'nmap not detected on this host - ICMP/TCP checks still run normally. Install nmap to enable deeper port/service scans.'}
          </div>
        </section>
      )}

      <section className="panel">
        <div className="panel-head">
          <h2>Zones</h2><span className="spacer" />
          <button className="btn small primary" onClick={() => setEditing(null)}><Icon name="plus" size={14} /> Add zone</button>
        </div>
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr><th>Name</th><th>CIDR</th><th>Type</th><th>Status</th><th className="num">Devices</th>
                  <th className="num">Up</th><th className="num">Degraded</th><th className="num">Down</th>
                  <th className="num">Avg latency</th><th>Last scan</th><th>Enabled</th><th /></tr>
            </thead>
            <tbody>
              {zones.map((z) => (
                <tr key={z.id} style={{ cursor: 'pointer' }} onClick={() => setViewing(z)}>
                  <td className="name">{z.name}{!z.authorized && z.zoneType !== 'LOCAL' && <span className="pill" style={{ marginLeft: 6 }}>unauthorized</span>}</td>
                  <td className="mono">{z.cidr}</td>
                  <td>{z.zoneType}</td>
                  <td><span className={`pill ${STATUS_TONE[z.status] || ''}`}><span className="dot" />{z.status}</span></td>
                  <td className="num">{z.deviceCount}</td>
                  <td className="num">{z.up}</td>
                  <td className="num">{z.degraded}</td>
                  <td className="num">{z.down}</td>
                  <td className="num">{fmtMs(z.avgLatencyMs)}</td>
                  <td>{relTime(z.lastScanAt)}</td>
                  <td onClick={(e) => e.stopPropagation()}>
                    <label className="switch"><input type="checkbox" checked={z.enabled} onChange={(e) => doAction(() => enableZone(z.id, e.target.checked), z.id)} /><span className="track" /></label>
                  </td>
                  <td onClick={(e) => e.stopPropagation()} style={{ whiteSpace: 'nowrap' }}>
                    <button className="btn small ghost" disabled={busy === z.id || !z.enabled || (!z.authorized && z.zoneType !== 'LOCAL')}
                            onClick={() => doAction(scanZone, z.id)} title={z.zoneType === 'LOCAL' ? 'Local zone uses the existing dashboard scan' : 'Scan now'}>
                      <Icon name="scan" size={13} /> Scan
                    </button>{' '}
                    {z.useNmap && overview?.nmapAvailable && z.zoneType !== 'LOCAL' && (
                      <button className="btn small ghost" disabled={busy === z.id} onClick={() => doAction(nmapScanZone, z.id)}>nmap</button>
                    )}{' '}
                    <button className="btn small ghost" onClick={() => setEditing(z)}>Edit</button>{' '}
                    {z.zoneType !== 'LOCAL' && (
                      <button className="btn small ghost" onClick={() => doAction(deleteZone, z.id)}>Delete</button>
                    )}
                  </td>
                </tr>
              ))}
              {!zones.length && <tr><td colSpan={12} className="empty">No zones yet - the local subnet is added automatically; click "Add zone" for routed subnets, labs or servers.</td></tr>}
            </tbody>
          </table>
        </div>
      </section>

      <section className="panel">
        <div className="panel-head"><h2>Cross-subnet incidents</h2></div>
        <div className="panel-body">
          {!incidents.length ? <div className="empty">No active incidents.</div> : (
            <ul style={{ margin: 0, paddingLeft: 18, display: 'flex', flexDirection: 'column', gap: 10 }}>
              {incidents.map((inc, i) => (
                <li key={i}>
                  <b>{inc.title}</b> <span className={`pill ${inc.severity === 'CRITICAL' ? 'tone-critical' : 'tone-degraded'}`}>{inc.severity}</span>
                  <ul className="dim" style={{ fontSize: 12.5, marginTop: 4 }}>
                    {inc.evidence.map((e, j) => <li key={j}>{e}</li>)}
                  </ul>
                </li>
              ))}
            </ul>
          )}
        </div>
      </section>

      {editing !== undefined && <ZoneForm zone={editing} onSave={save} onClose={() => setEditing(undefined)} />}
      {viewing && <ZoneDevices zone={viewing} onClose={() => setViewing(null)} />}
    </>
  );
}
