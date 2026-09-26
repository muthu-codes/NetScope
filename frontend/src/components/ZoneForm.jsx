import { useState } from 'react';
import Icon from './Icons';

const TYPES = ['LOCAL', 'ROUTED', 'SERVER', 'MANAGEMENT', 'CUSTOM'];

export default function ZoneForm({ zone, onSave, onClose }) {
  const [form, setForm] = useState(() => ({
    name: zone?.name || '',
    cidr: zone?.cidr || '',
    description: zone?.description || '',
    zoneType: zone?.zoneType || 'ROUTED',
    gateway: zone?.gateway || '',
    authorized: zone?.authorized ?? false,
    authorizedBy: zone?.authorizedBy || '',
    methods: zone?.methods || 'ICMP,TCP',
    tcpPorts: (zone?.tcpPorts || [80, 443, 22]).join(','),
    useNmap: zone?.useNmap ?? true,
    maxConcurrency: zone?.maxConcurrency ?? 20,
    timeoutMs: zone?.timeoutMs ?? 1000,
    maxDevices: zone?.maxDevices ?? 512,
    intervalSeconds: zone?.intervalSeconds ?? 60,
    failureThreshold: zone?.failureThreshold ?? 3
  }));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value }));

  const submit = async (e) => {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      await onSave({
        ...form,
        maxConcurrency: Number(form.maxConcurrency),
        timeoutMs: Number(form.timeoutMs),
        maxDevices: Number(form.maxDevices),
        intervalSeconds: Number(form.intervalSeconds),
        failureThreshold: Number(form.failureThreshold)
      });
    } catch (err) {
      setError(err.message);
      setSaving(false);
    }
  };

  return (
    <>
      <div className="scrim" onClick={onClose} />
      <aside className="drawer" role="dialog" aria-label={zone ? 'Edit zone' : 'Add zone'}>
        <form onSubmit={submit}>
          <div className="drawer-head">
            <h2 style={{ flex: 1 }}>{zone ? `Edit ${zone.name}` : 'Add network zone'}</h2>
            <button type="button" className="btn small ghost" onClick={onClose} aria-label="Close"><Icon name="close" /></button>
          </div>
          <div className="drawer-body" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {error && <div className="banner bad">{error}</div>}
            <label>Name
              <input className="input" style={{ width: '100%' }} value={form.name} onChange={set('name')} required maxLength={80} />
            </label>
            <label>CIDR (private ranges only, e.g. 10.20.30.0/24)
              <input className="input mono" style={{ width: '100%' }} value={form.cidr} onChange={set('cidr')} required
                     placeholder="10.20.30.0/24" disabled={zone?.zoneType === 'LOCAL'} />
            </label>
            <label>Zone type
              <select className="select" style={{ width: '100%' }} value={form.zoneType} onChange={set('zoneType')} disabled={zone?.zoneType === 'LOCAL'}>
                {TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
              </select>
            </label>
            <label>Description
              <input className="input" style={{ width: '100%' }} value={form.description} onChange={set('description')} maxLength={300} />
            </label>
            <label>Gateway IP (optional, used for path/incident correlation)
              <input className="input mono" style={{ width: '100%' }} value={form.gateway} onChange={set('gateway')} placeholder="10.0.0.11" />
            </label>
            {form.zoneType !== 'LOCAL' && (
              <label className="switch" style={{ fontSize: 13.5 }}>
                <input type="checkbox" checked={form.authorized} onChange={set('authorized')} />
                <span className="track" /> I have written permission to monitor this network
              </label>
            )}
            {form.authorized && form.zoneType !== 'LOCAL' && (
              <label>Authorized by (recorded in the audit log)
                <input className="input" style={{ width: '100%' }} value={form.authorizedBy} onChange={set('authorizedBy')} maxLength={120} />
              </label>
            )}
            <label>Monitoring methods (comma-separated: ICMP, TCP)
              <input className="input mono" style={{ width: '100%' }} value={form.methods} onChange={set('methods')} />
            </label>
            <label>TCP ports to check (comma-separated, max 10)
              <input className="input mono" style={{ width: '100%' }} value={form.tcpPorts} onChange={set('tcpPorts')} />
            </label>
            <label className="switch" style={{ fontSize: 13.5 }}>
              <input type="checkbox" checked={form.useNmap} onChange={set('useNmap')} />
              <span className="track" /> Allow deeper nmap scan for this zone (only if nmap is installed)
            </label>
            <div className="grid-2" style={{ gap: 10 }}>
              <label>Max concurrent probes
                <input className="input" type="number" min={1} max={200} value={form.maxConcurrency} onChange={set('maxConcurrency')} />
              </label>
              <label>Timeout (ms)
                <input className="input" type="number" min={200} max={10000} value={form.timeoutMs} onChange={set('timeoutMs')} />
              </label>
              <label>Max devices scanned
                <input className="input" type="number" min={1} max={4096} value={form.maxDevices} onChange={set('maxDevices')} />
              </label>
              <label>Scan interval (seconds)
                <input className="input" type="number" min={10} max={86400} value={form.intervalSeconds} onChange={set('intervalSeconds')} />
              </label>
              <label>Failure threshold before DOWN
                <input className="input" type="number" min={1} max={10} value={form.failureThreshold} onChange={set('failureThreshold')} />
              </label>
            </div>
          </div>
          <div className="drawer-head" style={{ borderTop: '1px solid var(--line)', borderBottom: 'none', justifyContent: 'flex-end', gap: 8 }}>
            <button type="button" className="btn ghost" onClick={onClose}>Cancel</button>
            <button type="submit" className="btn primary" disabled={saving}>{saving ? 'Saving…' : 'Save zone'}</button>
          </div>
        </form>
      </aside>
    </>
  );
}
