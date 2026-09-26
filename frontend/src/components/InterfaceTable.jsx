export default function InterfaceTable({ interfaces }) {
  if (!interfaces.length) return <div className="empty">No interfaces reported.</div>;
  return (
    <div className="table-wrap">
      <table className="table" style={{ minWidth: 820 }}>
        <thead>
          <tr><th>Interface</th><th>Type</th><th>Status</th><th>MAC</th><th>IPv4</th><th>IPv6</th><th className="num">MTU</th></tr>
        </thead>
        <tbody>
          {interfaces.map((i) => (
            <tr key={i.name} style={{ cursor: 'default' }}>
              <td className="name" title={i.name}>
                {i.displayName || i.name}
                {i.active && <span className="pill online" style={{ marginLeft: 8 }}>active</span>}
              </td>
              <td>{i.type.toLowerCase()}{i.virtual && !i.loopback ? ' (virtual)' : ''}</td>
              <td><span className={`dot ${i.up ? 'ok' : ''}`} /> {i.up ? 'up' : 'down'}</td>
              <td className="mono">{i.mac || '—'}</td>
              <td className="mono">{i.ipv4.join(', ') || '—'}</td>
              <td className="mono" title={i.ipv6.join('\n')}>{i.ipv6[0] ? `${i.ipv6[0].slice(0, 26)}${i.ipv6.length > 1 ? ` +${i.ipv6.length - 1}` : ''}` : '—'}</td>
              <td className="num">{i.mtu}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
