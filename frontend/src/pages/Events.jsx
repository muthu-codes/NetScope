import { useMemo, useState } from 'react';
import EventTimeline from '../components/EventTimeline';
import { EVENT_LABEL } from '../utils/formatters';

export default function Events({ events, onSelectIp, onRefresh }) {
  const [type, setType] = useState('');
  const [severity, setSeverity] = useState('');
  const filtered = useMemo(
    () => events.filter((e) => (!type || e.type === type) && (!severity || e.severity === severity)),
    [events, type, severity]
  );

  return (
    <section className="panel">
      <div className="panel-head">
        <select className="select" value={type} onChange={(e) => setType(e.target.value)} aria-label="Filter by event type">
          <option value="">All event types</option>
          {Object.entries(EVENT_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
        <select className="select" value={severity} onChange={(e) => setSeverity(e.target.value)} aria-label="Filter by severity">
          <option value="">All severities</option><option value="CRITICAL">Critical</option><option value="WARNING">Warning</option><option value="INFO">Info</option>
        </select>
        <span className="spacer" />
        <span className="dim" style={{ fontSize: 12.5 }}>{filtered.length} events · updates live</span>
        <button className="btn small ghost" onClick={onRefresh}>Reload</button>
      </div>
      <div style={{ maxHeight: 'none' }}>
        <EventTimeline events={filtered} limit={300} onSelectIp={onSelectIp} />
      </div>
    </section>
  );
}
