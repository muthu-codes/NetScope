import { useMemo, useState } from 'react';
import DeviceTable from '../components/DeviceTable';
import { TYPE_LABEL } from '../utils/formatters';
import '../styles/devices.css';

export default function Devices({ devices, loading, error, onSelectIp, selectedIp }) {
  const [q, setQ] = useState('');
  const [state, setState] = useState('');
  const [type, setType] = useState('');
  const [health, setHealth] = useState('');
  const [subnet, setSubnet] = useState('');

  const subnets = useMemo(() => [...new Set(devices.map((d) => d.subnet).filter(Boolean))].sort(), [devices]);
  const types = useMemo(() => [...new Set(devices.map((d) => d.deviceType))].sort(), [devices]);

  const filtered = useMemo(() => {
    const needle = q.trim().toLowerCase();
    return devices.filter((d) =>
      (!state || d.state === state) && (!type || d.deviceType === type) &&
      (!health || d.healthStatus === health) && (!subnet || d.subnet === subnet) &&
      (!needle || [d.ip, d.mac, d.hostname, d.vendor].some((v) => v && v.toLowerCase().includes(needle))));
  }, [devices, q, state, type, health, subnet]);

  return (
    <section className="panel">
      <div className="panel-head">
        <div className="filters">
          <input className="input" type="search" placeholder="Search IP, name, MAC or vendor" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search devices" />
          <select className="select" value={state} onChange={(e) => setState(e.target.value)} aria-label="Filter by state">
            <option value="">All states</option><option value="REACHABLE">Online</option><option value="ARP_ONLY">ARP only</option><option value="UNREACHABLE">Offline</option>
          </select>
          <select className="select" value={health} onChange={(e) => setHealth(e.target.value)} aria-label="Filter by health">
            <option value="">All health</option><option value="CRITICAL">Critical</option><option value="DEGRADED">Degraded</option>
            <option value="HEALTHY">Healthy</option><option value="LIMITED">Limited</option><option value="OFFLINE">Offline</option>
          </select>
          <select className="select" value={type} onChange={(e) => setType(e.target.value)} aria-label="Filter by type">
            <option value="">All types</option>
            {types.map((t) => <option key={t} value={t}>{TYPE_LABEL[t] || t}</option>)}
          </select>
          <select className="select" value={subnet} onChange={(e) => setSubnet(e.target.value)} aria-label="Filter by subnet">
            <option value="">All subnets</option>
            {subnets.map((s) => <option key={s} value={s}>{s}</option>)}
          </select>
        </div>
        <span className="spacer" />
        <span className="dim" style={{ fontSize: 12.5 }}>{filtered.length.toLocaleString()} of {devices.length.toLocaleString()}</span>
      </div>
      {error && <div className="banner bad" style={{ margin: 12 }}>{error}</div>}
      {loading ? <div className="empty"><span className="spinner" /> Loading devices…</div>
        : <DeviceTable devices={filtered} onSelect={onSelectIp} selectedIp={selectedIp} />}
      <div className="panel-body dim" style={{ fontSize: 12.5, borderTop: '1px solid var(--line)' }}>
        A device can be missing because a firewall blocks ping, it is asleep, AP/client isolation or a VLAN hides it, or its ARP entry expired.
        MAC addresses only appear for devices on the same network segment as the monitoring computer. Vendor names are hints from the MAC prefix, not proof.
      </div>
    </section>
  );
}
