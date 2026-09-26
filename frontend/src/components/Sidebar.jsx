import Icon from './Icons';

export const PAGES = [
  { id: 'dashboard', label: 'Dashboard', icon: 'dashboard' },
  { id: 'devices', label: 'Devices', icon: 'devices' },
  { id: 'zones', label: 'Network Zones', icon: 'zones' },
  { id: 'topology', label: 'Topology', icon: 'topology' },
  { id: 'wifi', label: 'Wi-Fi', icon: 'wifi' },
  { id: 'diagnostics', label: 'Diagnostics', icon: 'diagnostics' },
  { id: 'events', label: 'Events', icon: 'events' },
  { id: 'interfaces', label: 'Interfaces', icon: 'interfaces' },
  { id: 'settings', label: 'Settings', icon: 'settings' },
  { id: 'about', label: 'About', icon: 'about' }
];

export default function Sidebar({ page }) {
  return (
    <aside className="sidebar">
      <div className="brand">
        <svg className="brand-mark" viewBox="0 0 32 32" aria-hidden="true">
          <circle cx="16" cy="16" r="3.2" fill="#7cc4ff" />
          <circle cx="16" cy="16" r="8" fill="none" stroke="#7cc4ff" strokeOpacity=".65" strokeWidth="1.6" />
          <circle cx="16" cy="16" r="13" fill="none" stroke="#7cc4ff" strokeOpacity=".28" strokeWidth="1.6" />
        </svg>
        <div>
          <div className="brand-name">NetScope</div>
          <div className="brand-sub">Network observability</div>
        </div>
      </div>
      <nav className="nav" aria-label="Main">
        {PAGES.map((p) => (
          <a key={p.id} href={`#/${p.id}`} aria-current={page === p.id ? 'page' : undefined}>
            <Icon name={p.icon} />
            <span>{p.label}</span>
          </a>
        ))}
      </nav>
      <div className="sidebar-foot">
        <strong><Icon name="shield" size={15} /> Read-only mode</strong>
        Observes only. Never changes devices, routers or switches.
      </div>
    </aside>
  );
}
