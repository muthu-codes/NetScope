export default function StatCard({ label, value, sub, tone }) {
  return (
    <div className="kpi">
      <div className="kpi-label">{label}</div>
      <div className={`kpi-value ${tone ? `tone-${tone}` : ''}`} title={typeof value === 'string' ? value : undefined}>{value}</div>
      {sub && <div className="kpi-sub" title={sub}>{sub}</div>}
    </div>
  );
}
