import { EVENT_LABEL, fmtTime, relTime } from '../utils/formatters';

const SEV_DOT = { INFO: '', WARNING: 'warn', CRITICAL: 'bad' };

export default function EventTimeline({ events, limit = 100, onSelectIp, empty = 'No events yet. They appear when devices join, leave or degrade.' }) {
  const list = events.slice(0, limit);
  if (list.length === 0) return <div className="empty">{empty}</div>;
  return (
    <div className="timeline">
      {list.map((e) => (
        <div className="tl-item" key={e.id}>
          <span className={`dot ${SEV_DOT[e.severity] || ''}`} style={!SEV_DOT[e.severity] ? { background: 'var(--accent)' } : undefined} />
          <div>
            <div className={`tl-type sev-${e.severity.toLowerCase()}`}>{EVENT_LABEL[e.type] || e.type}</div>
            <div className="tl-msg">
              {e.message}
              {e.ip && onSelectIp && e.type !== 'SCAN_COMPLETED' && (
                <> <button className="link mono" onClick={() => onSelectIp(e.ip)}>{e.ip}</button></>
              )}
            </div>
          </div>
          <div className="tl-time" title={new Date(e.timestamp).toLocaleString()}>{fmtTime(e.timestamp)} · {relTime(e.timestamp)}</div>
        </div>
      ))}
    </div>
  );
}
