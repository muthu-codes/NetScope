import InterfaceTable from '../components/InterfaceTable';
import Icon from '../components/Icons';

export default function Interfaces({ summary, interfaces, onReload }) {
  const cfg = summary?.configuration;
  return (
    <>
      <section className="panel">
        <div className="panel-head"><h2>Active connection</h2></div>
        <div className="panel-body">
          {!cfg ? <span className="dim">Loading…</span> : (
            <dl className="kv" style={{ gridTemplateColumns: '150px minmax(0,1fr)', maxWidth: 760 }}>
              <dt>Computer</dt><dd>{cfg.hostname} <span className="faint">({cfg.os})</span></dd>
              <dt>Interface</dt><dd>{cfg.interfaceDisplayName || '—'} {cfg.interfaceType && <span className="faint">({cfg.interfaceType.toLowerCase()})</span>}</dd>
              <dt>MAC address</dt><dd className="mono">{cfg.mac || '—'}</dd>
              <dt>IPv4 / mask</dt><dd className="mono">{cfg.ipv4 ? `${cfg.ipv4} / ${cfg.netmask} (/${cfg.prefixLength})` : '—'}</dd>
              <dt>Local subnet</dt><dd className="mono">{cfg.networkCidr || '—'}</dd>
              <dt>Default gateway</dt><dd className="mono">{cfg.gatewayIp || '—'} {cfg.gatewayMac && <span className="faint">MAC {cfg.gatewayMac}</span>}</dd>
              <dt>DNS servers</dt><dd className="mono">{cfg.dnsServers.length ? cfg.dnsServers.join(', ') : '—'}</dd>
              <dt>IPv6</dt><dd className="mono">{cfg.ipv6.length ? cfg.ipv6.join(', ') : '—'}</dd>
              <dt>Link</dt><dd><span className={`dot ${cfg.interfaceUp ? 'ok' : 'bad'}`} /> {cfg.interfaceUp ? 'up' : 'down'}</dd>
            </dl>
          )}
          <p className="dim" style={{ fontSize: 12.5, marginTop: 12 }}>
            The gateway comes from the routing table (lowest-metric default route), not from parsing ipconfig text. Every value is read live from the operating system.
          </p>
        </div>
      </section>
      <section className="panel">
        <div className="panel-head"><h2>Network interfaces</h2><span className="spacer" />
          <button className="btn small ghost" onClick={onReload}><Icon name="refresh" size={14} /> Reload</button></div>
        <InterfaceTable interfaces={interfaces} />
      </section>
    </>
  );
}
