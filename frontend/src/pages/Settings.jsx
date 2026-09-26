import Icon from '../components/Icons';
import { fmtDateTime, fmtDuration } from '../utils/formatters';

export default function Settings({ summary, scan, onToggleMonitoring, onScan }) {
  const scope = summary?.scope;
  const mon = summary?.monitoring;
  return (
    <div className="grid-2">
      <section className="panel">
        <div className="panel-head"><h2>Authorized scan scope</h2></div>
        <div className="panel-body">
          {!scope ? <span className="dim">Loading…</span> : (
            <>
              <div className={`banner ${scope.valid ? 'info' : 'bad'}`} style={{ marginBottom: 14 }}>
                <Icon name={scope.valid ? 'shield' : 'alert'} /><div>{scope.message}</div>
              </div>
              <dl className="kv" style={{ gridTemplateColumns: '170px minmax(0,1fr)' }}>
                <dt>Mode</dt><dd>{scope.mode === 'AUTO' ? 'AUTO - only the subnet this computer is on' : 'CONFIGURED - listed CIDR blocks'}</dd>
                <dt>Scanned ranges</dt><dd className="mono">{scope.cidrs.length ? scope.cidrs.join(', ') : '—'}</dd>
                <dt>Addresses per full scan</dt><dd className="mono">{scope.totalTargets.toLocaleString()}</dd>
                <dt>Estimated maximum time</dt><dd>{scope.estimatedMaxSeconds ? `about ${fmtDuration(scope.estimatedMaxSeconds * 1000)}` : '—'}</dd>
                <dt>Written permission</dt><dd>{scope.authorizationConfirmed ? 'confirmed in configuration' : 'not needed for the local subnet'}</dd>
                <dt>Authorized by</dt><dd>{scope.authorizedBy || '—'}</dd>
              </dl>
              {scope.warnings.map((w) => <div className="banner warn" key={w} style={{ marginTop: 10 }}><Icon name="alert" /><div>{w}</div></div>)}
              <p className="dim" style={{ fontSize: 12.5, marginTop: 14 }}>
                The scope is deliberately <b>not editable from the browser</b>: widening what NetScope may probe is a security decision that must be made in
                <span className="mono"> application.properties</span> (netscope.scope.*) and needs a restart. Only private address ranges are ever accepted.
              </p>
            </>
          )}
        </div>
      </section>

      <section className="panel">
        <div className="panel-head"><h2>Monitoring</h2></div>
        <div className="panel-body">
          <div className="toolbar" style={{ marginBottom: 14 }}>
            <label className="switch">
              <input type="checkbox" checked={mon ? mon.enabled : true} onChange={(e) => onToggleMonitoring(e.target.checked)} />
              <span className="track" />Automatic monitoring {mon?.enabled ? 'on' : 'off'}
            </label>
            <span className="spacer" />
            <button className="btn small" onClick={() => onScan('REFRESH')} disabled={scan.running}>Quick refresh</button>
            <button className="btn small primary" onClick={() => onScan('FULL')} disabled={scan.running}>Full scan</button>
          </div>
          {mon && (
            <dl className="kv" style={{ gridTemplateColumns: '190px minmax(0,1fr)' }}>
              <dt>Full sweep every</dt><dd>{fmtDuration(mon.fullScanIntervalSeconds * 1000)}</dd>
              <dt>Quick refresh every</dt><dd>{fmtDuration(mon.refreshIntervalSeconds * 1000)} (known devices only)</dd>
              <dt>Last full scan</dt><dd>{fmtDateTime(mon.lastFullScanAt)}</dd>
              <dt>Last scan duration</dt><dd>{fmtDuration(mon.lastScanDurationMs)}</dd>
              <dt>Next full scan</dt><dd>{mon.enabled ? fmtDateTime(mon.nextFullScanAt) : 'paused'}</dd>
              <dt>Status</dt><dd>{scan.running ? scan.phase : 'idle'}</dd>
              {mon.lastError && (<><dt>Last error</dt><dd className="error-text">{mon.lastError}</dd></>)}
            </dl>
          )}
          <p className="dim" style={{ fontSize: 12.5, marginTop: 14 }}>
            A full sweep pings every address in the scope (rate-limited). Between sweeps only devices already known are re-checked, which keeps large campus networks responsive.
          </p>
        </div>
      </section>
    </div>
  );
}
