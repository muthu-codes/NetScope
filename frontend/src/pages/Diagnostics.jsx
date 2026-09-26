import { useEffect, useState } from 'react';
import DiagnosticPanel from '../components/DiagnosticPanel';
import Icon from '../components/Icons';
import { diagnoseDevice } from '../services/deviceService';
import { getLastDiagnostics, runNetworkDiagnostics } from '../services/networkService';
import { relTime } from '../utils/formatters';

export default function Diagnostics({ onSelectIp }) {
  const [net, setNet] = useState({ loading: false, result: null, error: null });
  const [dev, setDev] = useState({ loading: false, result: null, error: null });
  const [ip, setIp] = useState('');

  useEffect(() => {
    getLastDiagnostics().then((r) => r && setNet((s) => ({ ...s, result: r }))).catch(() => {});
  }, []);

  const runNet = async () => {
    setNet({ loading: true, result: null, error: null });
    try { setNet({ loading: false, result: await runNetworkDiagnostics(), error: null }); }
    catch (e) { setNet({ loading: false, result: null, error: e.message }); }
  };

  const runDev = async (trace) => {
    if (!ip.trim()) return;
    setDev({ loading: true, result: null, error: null });
    try { setDev({ loading: false, result: await diagnoseDevice(ip.trim(), { count: 8, trace }), error: null }); }
    catch (e) { setDev({ loading: false, result: null, error: e.message }); }
  };

  return (
    <div className="grid-2">
      <section className="panel">
        <div className="panel-head">
          <h2>Network health check</h2><span className="spacer" />
          {net.result && <span className="dim" style={{ fontSize: 12.5 }}>ran {relTime(net.result.generatedAt)}</span>}
          <button className="btn primary small" onClick={runNet} disabled={net.loading}><Icon name="refresh" size={14} /> Run checks</button>
        </div>
        <div className="panel-body dim" style={{ fontSize: 12.5, paddingBottom: 0 }}>
          Checks the local interface, the default gateway, DNS, internet reachability and the health of every discovered device. Read-only: only ping and DNS lookups are used.
        </div>
        <DiagnosticPanel result={net.result} loading={net.loading} error={net.error} onSelectIp={onSelectIp} />
      </section>

      <section className="panel">
        <div className="panel-head"><h2>Test one device</h2></div>
        <div className="panel-body">
          <div className="toolbar">
            <input className="input mono" style={{ width: 200 }} placeholder="IPv4 address" value={ip} onChange={(e) => setIp(e.target.value)}
                   onKeyDown={(e) => e.key === 'Enter' && runDev(false)} aria-label="Device IP address" />
            <button className="btn small" onClick={() => runDev(false)} disabled={dev.loading || !ip.trim()}>Ping test</button>
            <button className="btn small" onClick={() => runDev(true)} disabled={dev.loading || !ip.trim()}>Ping + route trace</button>
          </div>
          <p className="dim" style={{ fontSize: 12.5, marginTop: 8 }}>
            Only addresses inside the authorized scan scope can be tested. Anything else is refused by the backend.
          </p>
        </div>
        <DiagnosticPanel result={dev.result} loading={dev.loading} error={dev.error} onSelectIp={onSelectIp} />
        {!dev.result && !dev.loading && !dev.error && <div className="empty">Enter an IP address from the Devices page to measure loss, latency and jitter.</div>}
      </section>
    </div>
  );
}
