import Icon from '../components/Icons';

const LIMITS = [
  'A firewall blocks ICMP (very common on Windows PCs). NetScope shows such a device as "ARP only" when it is on the same segment.',
  'The device is asleep, powered off or roaming between access points.',
  'AP / client isolation, VLAN separation or routing rules stop this computer from reaching it.',
  'Its ARP entry expired before the scan read it.',
  'The network does not expose the device to the monitoring computer at all.',
  'MAC addresses only exist for devices on the same Layer-2 segment; routed devices have none. Hostnames and vendors can be unavailable, and vendor names are hints from the MAC prefix.'
];

export default function About() {
  return (
    <div className="grid-2">
      <section className="panel">
        <div className="panel-head"><h2>What NetScope does</h2></div>
        <div className="panel-body" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <p>NetScope is a <b>read-only</b> network observability and diagnostics platform. It discovers the devices it can observe inside an authorized network scope, measures their health, draws a topology from real data and explains problems in plain language.</p>
          <div className="banner info"><Icon name="shield" /><div>
            It never changes router or switch configuration, never reads device files or accounts, never intercepts traffic, never attacks or bypasses security, and never scans public networks.
          </div></div>
          <h3>How the topology is built</h3>
          <ul className="notes">
            <li><b>Observed:</b> this computer's routing table (default gateway) and the ARP table (devices on the same LAN).</li>
            <li><b>Inferred:</b> routers between subnets from traceroute hops, and subnet membership from IP addressing.</li>
            <li><b>Assumed:</b> shown dotted, only when no data exists.</li>
            <li>Physical switch, access-point and cable connections are <b>not</b> drawn. They need authorized SNMP / LLDP data.</li>
          </ul>
          <h3>How health is scored</h3>
          <p className="dim">Every device gets a 0-100 score from ping latency, packet loss over the last probes, jitter and flapping. Each issue carries the measurement, why it matters and the next thing to check.</p>
          <h3>Stack</h3>
          <p className="dim">Java 21 · Spring Boot · Spring Data JPA (H2 / MySQL) · WebSocket · React · Vite · d3-force canvas graph.</p>
        </div>
      </section>

      <section className="panel">
        <div className="panel-head"><h2>Why a device may not appear</h2></div>
        <div className="panel-body">
          <ul className="notes" style={{ fontSize: 13.5, gap: 9 }}>
            {LIMITS.map((l) => <li key={l}>{l}</li>)}
          </ul>
          <p className="dim" style={{ fontSize: 12.5, marginTop: 14 }}>
            Not seeing a device is a limit of observation, not proof that it is absent. Results depend on network segmentation, VLANs, routing, NAT, firewalls and permissions.
          </p>
        </div>
      </section>
    </div>
  );
}
