import { relTime } from '../utils/formatters';

const HEADLINE = {
  HEALTHY: 'Network healthy',
  DEGRADED: 'Network degraded',
  DOWN: 'Network down',
  UNKNOWN: 'Waiting for the first scan'
};

export default function NetworkStatus({ summary }) {
  if (!summary) {
    return <div className="status-strip unknown"><div className="headline">Connecting to NetScope…</div></div>;
  }
  const { networkStatus, networkStatusReason, configuration: cfg, monitoring } = summary;
  const cls = networkStatus.toLowerCase();
  return (
    <div className={`status-strip ${cls}`} role="status">
      <span className={`dot ${networkStatus === 'HEALTHY' ? 'ok' : networkStatus === 'DEGRADED' ? 'warn' : networkStatus === 'DOWN' ? 'bad' : ''}`} style={{ width: 12, height: 12 }} />
      <div>
        <div className="headline">{HEADLINE[networkStatus]}</div>
        <div className="reason">{networkStatusReason}</div>
      </div>
      <div className="meta">
        <div>{cfg?.interfaceType ? `${cfg.interfaceType.toLowerCase()} · ` : ''}<span className="mono">{cfg?.networkCidr || 'no network'}</span></div>
        <div>Last full scan {monitoring?.lastFullScanAt ? relTime(monitoring.lastFullScanAt) : 'not yet'}</div>
      </div>
    </div>
  );
}
