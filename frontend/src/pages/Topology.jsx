import { useState } from 'react';
import TopologyGraph from '../components/TopologyGraph';
import Icon from '../components/Icons';
import { HealthPill, StatePill } from '../components/Badges';
import { TYPE_LABEL, fmtMs } from '../utils/formatters';
import '../styles/topology.css';

export default function Topology({ topology, devices, scan, selectedIp, onSelectIp }) {
  const [showOffline, setShowOffline] = useState(true);
  const [showDevices, setShowDevices] = useState(true);
  const [node, setNode] = useState(null);

  const onSelect = (n) => {
    if (!n) { setNode(null); return; }
    if (devices.some((d) => d.ip === n.id)) { setNode(null); onSelectIp(n.id); } else setNode(n);
  };
  const sum = topology?.summary;

  return (
    <div className="topo-page">
      <section className="panel">
        <div className="panel-head">
          <h2>Observed / inferred topology</h2>
          <span className="spacer" />
          <label className="switch"><input type="checkbox" checked={showDevices} onChange={(e) => setShowDevices(e.target.checked)} /><span className="track" />Devices</label>
          <label className="switch"><input type="checkbox" checked={showOffline} onChange={(e) => setShowOffline(e.target.checked)} /><span className="track" />Offline</label>
        </div>
        <TopologyGraph topology={topology} scanning={scan.running} height={680} showOffline={showOffline} showDevices={showDevices}
                       selectedId={node ? node.id : selectedIp} onSelect={onSelect} />
      </section>

      <aside className="inspector">
        <div className="banner info">
          <Icon name="shield" />
          <div>{topology?.disclaimer || 'Observed / inferred topology.'}</div>
        </div>

        {node && (
          <section className="panel">
            <div className="panel-head"><h2>{node.label}</h2></div>
            <div className="panel-body">
              <dl className="kv" style={{ gridTemplateColumns: '90px 1fr' }}>
                <dt>Type</dt><dd>{TYPE_LABEL[node.type] || node.type}</dd>
                {node.ip && (<><dt>IP</dt><dd className="mono">{node.ip}</dd></>)}
                {node.type === 'SUBNET' ? (
                  <><dt>Devices</dt><dd>{node.onlineCount} online of {node.deviceCount}</dd>
                    <dt>Avg ping</dt><dd>{fmtMs(node.latencyMs, 1)}</dd></>
                ) : (
                  <><dt>State</dt><dd><StatePill state={node.state} /></dd>
                    {node.latencyMs != null && (<><dt>Hop RTT</dt><dd>{fmtMs(node.latencyMs, 1)}</dd></>)}</>
                )}
                <dt>Health</dt><dd><HealthPill status={node.healthStatus} /></dd>
                <dt>Known from</dt><dd>{node.source}</dd>
              </dl>
              {node.type === 'ROUTER' && <p className="dim" style={{ marginTop: 10, fontSize: 12.5 }}>This router forwarded NetScope's traceroute packets, so it is a real Layer-3 hop on the path.</p>}
            </div>
          </section>
        )}

        <section className="panel">
          <div className="panel-head"><h2>What you are looking at</h2></div>
          <div className="panel-body">
            {sum && (
              <dl className="kv" style={{ gridTemplateColumns: '120px 1fr', marginBottom: 12 }}>
                <dt>Devices</dt><dd className="mono">{sum.devices}</dd>
                <dt>Subnets</dt><dd className="mono">{sum.subnets}</dd>
                <dt>Router hops</dt><dd className="mono">{sum.routers}</dd>
                <dt>Observed links</dt><dd className="mono">{sum.edgesObserved}</dd>
                <dt>Inferred links</dt><dd className="mono">{sum.edgesInferred}</dd>
                <dt>Assumed links</dt><dd className="mono">{sum.edgesAssumed}</dd>
              </dl>
            )}
            <ul className="notes">
              {(topology?.notes || []).map((n) => <li key={n}>{n}</li>)}
            </ul>
          </div>
        </section>
      </aside>
    </div>
  );
}
