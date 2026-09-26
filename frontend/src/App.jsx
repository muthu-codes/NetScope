import { useCallback, useEffect, useMemo, useState } from 'react';
import Sidebar, { PAGES } from './components/Sidebar';
import Header from './components/Header';
import DeviceDetails from './components/DeviceDetails';
import Icon from './components/Icons';
import Dashboard from './pages/Dashboard';
import Devices from './pages/Devices';
import NetworkZones from './pages/NetworkZones';
import Topology from './pages/Topology';
import Diagnostics from './pages/Diagnostics';
import Wifi from './pages/Wifi';
import Events from './pages/Events';
import Interfaces from './pages/Interfaces';
import Settings from './pages/Settings';
import About from './pages/About';
import useNetwork from './hooks/useNetwork';
import useDevices from './hooks/useDevices';
import useZones from './hooks/useZones';
import useWebSocket from './hooks/useWebSocket';
import { startMonitoring, startScan, stopMonitoring } from './services/networkService';

function currentPage() {
  const id = window.location.hash.replace(/^#\/?/, '');
  return PAGES.some((p) => p.id === id) ? id : 'dashboard';
}

export default function App() {
  const [page, setPage] = useState(currentPage());
  const [selectedIp, setSelectedIp] = useState(null);
  const [notice, setNotice] = useState(null);
  const [busy, setBusy] = useState(false);

  const net = useNetwork();
  const dev = useDevices();
  const zonesData = useZones();
  const { refreshAll, refreshSummary, setProgress, pushEvent } = net;
  const refreshDevices = dev.refresh;
  const refreshZones = zonesData.refresh;

  useEffect(() => {
    const onHash = () => setPage(currentPage());
    window.addEventListener('hashchange', onHash);
    return () => window.removeEventListener('hashchange', onHash);
  }, []);

  useEffect(() => {
    if (!notice) return undefined;
    const t = setTimeout(() => setNotice(null), 7000);
    return () => clearTimeout(t);
  }, [notice]);

  // live updates from Spring Boot
  const { connected } = useWebSocket(useCallback((m) => {
    switch (m.type) {
      case 'SCAN_PROGRESS': setProgress(m.payload); break;
      case 'SCAN_COMPLETE': refreshAll(); refreshDevices(); break;
      case 'SCAN_ERROR': refreshSummary(); setNotice({ tone: 'warn', text: m.payload?.message || 'Scan failed' }); break;
      case 'ZONE_SCAN_COMPLETE': refreshZones(); break;
      case 'EVENT': pushEvent(m.payload); break;
      default: break;
    }
  }, [setProgress, refreshAll, refreshDevices, refreshSummary, refreshZones, pushEvent]));

  const selectedDevice = useMemo(() => dev.devices.find((d) => d.ip === selectedIp) || null, [dev.devices, selectedIp]);
  const closeDetails = useCallback(() => setSelectedIp(null), []);
  const openPage = useCallback((id) => { window.location.hash = `#/${id}`; }, []);

  const onScan = async (mode = 'FULL') => {
    setBusy(true);
    try {
      await startScan(mode);
      setNotice({ tone: 'info', text: `${mode === 'FULL' ? 'Full scan' : 'Quick refresh'} started.` });
      refreshSummary();
    } catch (e) {
      setNotice({ tone: 'warn', text: e.message });
    } finally {
      setBusy(false);
    }
  };

  const onToggleMonitoring = async (on) => {
    try {
      if (on) await startMonitoring(); else await stopMonitoring();
      refreshSummary();
    } catch (e) {
      setNotice({ tone: 'warn', text: e.message });
    }
  };

  const scope = net.summary?.scope;
  const scopeText = scope ? (scope.valid ? scope.cidrs.join(', ') : 'blocked') : null;
  const title = PAGES.find((p) => p.id === page)?.label || 'Dashboard';

  return (
    <div className="app">
      <Sidebar page={page} />
      <div className="main">
        <Header title={title} scopeText={scopeText} scan={net.scan} monitoring={net.summary?.monitoring}
                connected={connected} onToggleMonitoring={onToggleMonitoring} onScan={() => onScan('FULL')} scanBusy={busy} />
        <main className="content">
          {net.error && <div className="banner bad" role="alert"><Icon name="alert" /><div>{net.error}</div></div>}
          {notice && <div className={`banner ${notice.tone}`} role="status"><Icon name={notice.tone === 'warn' ? 'alert' : 'about'} /><div>{notice.text}</div></div>}

          {page === 'dashboard' && (
            <Dashboard summary={net.summary} topology={net.topology} events={net.events} devices={dev.devices} scan={net.scan}
                       onSelectIp={setSelectedIp} selectedIp={selectedIp} onOpenPage={openPage} zonesOverview={zonesData.overview} />
          )}
          {page === 'devices' && <Devices devices={dev.devices} loading={dev.loading} error={dev.error} onSelectIp={setSelectedIp} selectedIp={selectedIp} />}
          {page === 'zones' && <NetworkZones overview={zonesData.overview} zones={zonesData.zones} incidents={zonesData.incidents} onRefresh={zonesData.refresh} />}
          {page === 'topology' && <Topology topology={net.topology} devices={dev.devices} scan={net.scan} selectedIp={selectedIp} onSelectIp={setSelectedIp} />}
          {page === 'diagnostics' && <Diagnostics onSelectIp={setSelectedIp} />}
          {page === 'wifi' && <Wifi />}
          {page === 'events' && <Events events={net.events} onSelectIp={setSelectedIp} onRefresh={net.refreshEvents} />}
          {page === 'interfaces' && <Interfaces summary={net.summary} interfaces={net.interfaces} onReload={net.refreshInterfaces} />}
          {page === 'settings' && <Settings summary={net.summary} scan={net.scan} onToggleMonitoring={onToggleMonitoring} onScan={onScan} />}
          {page === 'about' && <About />}
        </main>
      </div>
      <DeviceDetails device={selectedDevice} onClose={closeDetails} />
    </div>
  );
}
