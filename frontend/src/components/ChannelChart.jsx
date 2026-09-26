/**
 * Bars = how many access points sit on each channel. For 2.4 GHz the bar colour shows the overlap load,
 * because channels 1-13 overlap each other (only 1, 6 and 11 are clear of each other).
 */
export default function ChannelChart({ channels, band }) {
  const list = channels.filter((c) => c.band === band);
  if (list.length === 0) return <div className="empty">No {band} networks heard.</div>;
  const maxAp = Math.max(1, ...list.map((c) => c.apCount));
  const maxLoad = Math.max(0.5, ...list.map((c) => c.load));

  return (
    <div className="chan-chart" role="img" aria-label={`${band} channel usage: ${list.filter((c) => c.apCount > 0).map((c) => `channel ${c.channel} has ${c.apCount}`).join(', ') || 'no networks'}`}>
      {list.map((c) => {
        const level = c.load / maxLoad;
        const color = c.apCount === 0 ? 'var(--line-strong)' : level > 0.66 ? 'var(--bad)' : level > 0.33 ? 'var(--warn)' : 'var(--ok)';
        return (
          <div className={`chan-col ${c.connectedHere ? 'here' : ''}`} key={`${c.band}-${c.channel}`}
               title={`Channel ${c.channel}: ${c.apCount} access point(s)${c.strongestSignal ? `, strongest ${c.strongestSignal}%` : ''}${band === '2.4 GHz' ? `, overlap load ${c.load}` : ''}${c.connectedHere ? ' (you are here)' : ''}`}>
            <div className="chan-count mono">{c.apCount || ''}</div>
            <div className="chan-bar-wrap">
              <div className="chan-bar" style={{ height: `${Math.max(c.apCount ? 8 : 3, (c.apCount / maxAp) * 100)}%`, background: color }} />
            </div>
            <div className="chan-label mono">{c.channel}</div>
            {c.connectedHere && <div className="chan-you">you</div>}
          </div>
        );
      })}
    </div>
  );
}
