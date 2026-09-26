import { useCallback, useEffect, useMemo, useState } from 'react';
import ChannelChart from '../components/ChannelChart';
import Icon from '../components/Icons';
import { SignalBar } from '../components/Badges';
import { getWifi } from '../services/wifiService';
import { relTime } from '../utils/formatters';
import '../styles/wifi.css';

const SEC_LABEL = { OPEN: 'Open', WEP: 'WEP', WPA: 'WPA', WPA2: 'WPA2', WPA3: 'WPA3', OWE: 'Enhanced Open', UNKNOWN: 'Unknown' };

/** Passive survey: the access points this computer's adapter can hear. It never joins or touches them. */
export default function Wifi() {
  const [survey, setSurvey] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [q, setQ] = useState('');
  const [band, setBand] = useState('');
  const [onlyInsecure, setOnlyInsecure] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try { setSurvey(await getWifi()); setError(null); }
    catch (e) { setError(e.message); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => {
    load();
    const t = setInterval(load, 20000);
    return () => clearInterval(t);
  }, [load]);

  const nets = useMemo(() => {
    const needle = q.trim().toLowerCase();
    return (survey?.networks || []).filter((n) =>
      (!band || n.band === band) && (!onlyInsecure || n.insecure) &&
      (!needle || [n.ssid, n.bssid, n.vendor].some((v) => v && v.toLowerCase().includes(needle))));
  }, [survey, q, band, onlyInsecure]);

  const conn = survey?.connection;

  return (
    <>
      <div className="banner info">
        <Icon name="shield" />
        <div>
          <b>Passive survey.</b> This lists the Wi-Fi access points your adapter already hears, the same list as the taskbar Wi-Fi menu. NetScope never joins,
          probes or interferes with them, and it cannot see the devices connected to other networks.
        </div>
      </div>
      {error && <div className="banner bad" role="alert"><Icon name="alert" /><div>{error}</div></div>}
      {survey && survey.message && <div className="banner warn" role="status"><Icon name="alert" /><div>{survey.message}</div></div>}

      <div className="dash-grid">
        <section className="panel">
          <div className="panel-head">
            <h2>Your connection</h2><span className="spacer" />
            {survey && <span className="dim" style={{ fontSize: 12.5 }}>updated {relTime(survey.collectedAt)}</span>}
            <button className="btn small ghost" onClick={load} disabled={loading}><Icon name="refresh" size={14} /> Refresh</button>
          </div>
          <div className="panel-body">
            {!conn ? (
              <span className="dim">{loading ? 'Reading the Wi-Fi adapter…' : 'This computer is not connected to Wi-Fi (or the adapter reports nothing).'}</span>
            ) : (
              <dl className="kv" style={{ gridTemplateColumns: '130px minmax(0,1fr)' }}>
                <dt>Network</dt><dd>{conn.ssid}</dd>
                <dt>Signal</dt><dd><SignalBar percent={conn.signalPercent} /> <span className="dim"> {conn.qualityLabel}{conn.dbm != null ? `, about ${conn.dbm} dBm` : ''}</span></dd>
                <dt>Channel</dt><dd className="mono">{conn.channel ?? '—'}{conn.band ? ` · ${conn.band}` : ''}</dd>
                <dt>Standard</dt><dd>{conn.radioType || '—'}</dd>
                <dt>Link speed</dt><dd className="mono">{conn.receiveMbps != null ? `${conn.receiveMbps} down / ${conn.transmitMbps ?? '—'} up Mbps` : '—'}</dd>
                <dt>Access point</dt><dd className="mono">{conn.bssid || '—'}</dd>
                <dt>Security</dt><dd>{conn.authentication || '—'}</dd>
              </dl>
            )}
          </div>
        </section>

        <section className="panel">
          <div className="panel-head"><h2>What NetScope notices</h2></div>
          <div className="panel-body">
            {survey && survey.insights.length > 0
              ? <ul className="insights">{survey.insights.map((i) => <li key={i}>{i}</li>)}</ul>
              : <span className="dim">Nothing to report yet.</span>}
          </div>
        </section>
      </div>

      <div className="grid-2">
        <section className="panel">
          <div className="panel-head"><h2>2.4 GHz channels</h2><span className="spacer" /><span className="dim" style={{ fontSize: 12.5 }}>bar height = access points</span></div>
          <ChannelChart channels={survey?.channels || []} band="2.4 GHz" />
          <div className="chan-note">Only channels 1, 6 and 11 do not overlap each other. Colour shows how much overlapping neighbours crowd that channel.</div>
        </section>
        <section className="panel">
          <div className="panel-head"><h2>5 / 6 GHz channels</h2></div>
          <ChannelChart channels={survey?.channels || []} band="5 GHz" />
          <div className="chan-note">Channels with no bar are not in use nearby.</div>
        </section>
      </div>

      <section className="panel">
        <div className="panel-head">
          <div className="filters">
            <input className="input" type="search" placeholder="Search name, BSSID or vendor" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search access points" />
            <select className="select" value={band} onChange={(e) => setBand(e.target.value)} aria-label="Filter by band">
              <option value="">All bands</option><option value="2.4 GHz">2.4 GHz</option><option value="5 GHz">5 GHz</option><option value="6 GHz">6 GHz</option>
            </select>
            <label className="switch"><input type="checkbox" checked={onlyInsecure} onChange={(e) => setOnlyInsecure(e.target.checked)} /><span className="track" />Only insecure</label>
          </div>
          <span className="spacer" />
          <span className="dim" style={{ fontSize: 12.5 }}>{nets.length} of {survey?.networks.length ?? 0} access points</span>
        </div>
        {nets.length === 0 ? <div className="empty">{loading ? 'Scanning…' : 'No access points to show.'}</div> : (
          <div className="table-wrap">
            <table className="table" style={{ minWidth: 900 }}>
              <thead><tr><th>Network</th><th>Signal</th><th>Channel</th><th>Band</th><th>Standard</th><th>Security</th><th>Access point (BSSID)</th><th>Maker (hint)</th></tr></thead>
              <tbody>
                {nets.map((n) => (
                  <tr key={n.bssid} style={{ cursor: 'default' }}>
                    <td>{n.ssid || <span className="faint">(hidden)</span>}{n.connected && <span className="wifi-you">connected</span>}</td>
                    <td><SignalBar percent={n.signalPercent} /></td>
                    <td className="mono">{n.channel ?? '—'}</td>
                    <td>{n.band || '—'}</td>
                    <td>{n.radioType || '—'}</td>
                    <td><span className={`pill sec ${n.insecure ? 'insecure' : 'ok'}`} title={n.authentication || ''}>{SEC_LABEL[n.securityLevel] || n.securityLevel}</span></td>
                    <td className="mono">{n.bssid}</td>
                    <td title={n.privateBssid ? 'Randomized BSSID: typical of phone hotspots and virtual SSIDs' : 'From the BSSID prefix - a hint, not proof'}>
                      {n.vendor || (n.privateBssid ? <span className="faint">private address</span> : <span className="faint">—</span>)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </>
  );
}
