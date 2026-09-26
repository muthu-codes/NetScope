import { useEffect, useMemo, useState } from 'react';
import { HealthPill, Sparkline, StatePill } from './Badges';
import { TYPE_LABEL, fmtMs, ipToNum, relTime } from '../utils/formatters';

const HEALTH_RANK = { CRITICAL: 0, DEGRADED: 1, OFFLINE: 2, LIMITED: 3, HEALTHY: 4, UNKNOWN: 5 };

const COLUMNS = [
  { key: 'health', label: 'Health', sort: (d) => HEALTH_RANK[d.healthStatus] ?? 9 },
  { key: 'ip', label: 'IP address', sort: (d) => ipToNum(d.ip) },
  { key: 'name', label: 'Name', sort: (d) => (d.hostname || '\uffff').toLowerCase() },
  { key: 'mac', label: 'MAC', sort: (d) => d.mac || '\uffff' },
  { key: 'vendor', label: 'Vendor (hint)', sort: (d) => (d.vendor || '\uffff').toLowerCase() },
  { key: 'type', label: 'Type', sort: (d) => d.deviceType },
  { key: 'state', label: 'State', sort: (d) => d.state },
  { key: 'latency', label: 'Ping', sort: (d) => d.latencyMs ?? 1e9, numeric: true },
  { key: 'trend', label: 'Trend' },
  { key: 'seen', label: 'Last seen', sort: (d) => -(d.lastSeen ? new Date(d.lastSeen).getTime() : 0) }
];

export default function DeviceTable({ devices, onSelect, selectedIp, pageSize = 50, defaultSort = 'ip' }) {
  const [sortKey, setSortKey] = useState(defaultSort);
  const [asc, setAsc] = useState(true);
  const [page, setPage] = useState(0);

  const sorted = useMemo(() => {
    const col = COLUMNS.find((c) => c.key === sortKey);
    if (!col || !col.sort) return devices;
    const out = [...devices].sort((a, b) => {
      const x = col.sort(a), y = col.sort(b);
      return x < y ? -1 : x > y ? 1 : 0;
    });
    return asc ? out : out.reverse();
  }, [devices, sortKey, asc]);

  useEffect(() => setPage(0), [devices.length, sortKey, asc]);
  const pages = Math.max(1, Math.ceil(sorted.length / pageSize));
  const rows = sorted.slice(page * pageSize, page * pageSize + pageSize);

  const toggle = (key) => {
    if (key === sortKey) setAsc(!asc);
    else { setSortKey(key); setAsc(true); }
  };

  if (devices.length === 0) return <div className="empty">No devices match. Run a scan or relax the filters.</div>;

  return (
    <>
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              {COLUMNS.map((c) => (
                <th key={c.key} className={c.numeric ? 'num' : undefined} aria-sort={c.key === sortKey ? (asc ? 'ascending' : 'descending') : undefined}>
                  {c.sort ? (
                    <button onClick={() => toggle(c.key)}>{c.label}{c.key === sortKey ? (asc ? ' ↑' : ' ↓') : ''}</button>
                  ) : c.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((d) => (
              <tr key={d.ip} className={d.ip === selectedIp ? 'selected' : ''} onClick={() => onSelect(d.ip)}
                  tabIndex={0} onKeyDown={(e) => { if (e.key === 'Enter') onSelect(d.ip); }}>
                <td><HealthPill status={d.healthStatus} score={d.healthScore} /></td>
                <td className="mono">{d.ip}</td>
                <td className="name" title={d.hostname || ''}>{d.hostname || <span className="faint">—</span>}</td>
                <td className="mono">{d.mac || <span className="faint" title="Not visible from this network segment">—</span>}</td>
                <td title={d.vendorNote || ''}>{d.vendor || <span className="faint">—</span>}</td>
                <td>{TYPE_LABEL[d.deviceType] || d.deviceType}</td>
                <td><StatePill state={d.state} /></td>
                <td className="num">{d.state === 'REACHABLE' ? fmtMs(d.latencyMs, d.latencyMs < 10 ? 1 : 0) : '—'}</td>
                <td><Sparkline values={d.latencyHistory} /></td>
                <td className="dim">{relTime(d.lastSeen)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="pager">
        <span>{sorted.length.toLocaleString()} devices</span>
        <span className="spacer" />
        <button className="btn small ghost" onClick={() => setPage(page - 1)} disabled={page === 0}>Previous</button>
        <span>Page {page + 1} of {pages}</span>
        <button className="btn small ghost" onClick={() => setPage(page + 1)} disabled={page >= pages - 1}>Next</button>
      </div>
    </>
  );
}
