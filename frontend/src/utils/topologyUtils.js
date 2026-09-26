// Colours / sizes / line styles used by the canvas topology. Keep in sync with styles/global.css.

export const COLORS = {
  HEALTHY: '#3ED598',
  DEGRADED: '#F4B740',
  CRITICAL: '#FF6B6B',
  OFFLINE: '#5E6E88',
  LIMITED: '#93A9C9',
  UNKNOWN: '#7B8CA6',
  accent: '#7CC4FF',
  bg: '#0A111C',
  panel: '#111B2B',
  text: '#DCE5F2',
  dim: '#8798B2'
};

export function nodeColor(n) {
  if (n.type === 'INTERNET') return COLORS.accent;
  if (n.state === 'UNREACHABLE') return COLORS.OFFLINE;
  return COLORS[n.healthStatus] || COLORS.UNKNOWN;
}

/** '#3ED598' + 0.4 -> 'rgba(62,213,152,0.4)' - used for the node glow / particle-flow gradients. */
export function hexToRgba(hex, alpha) {
  const h = (hex || '#000000').replace('#', '');
  const r = parseInt(h.substring(0, 2), 16) || 0;
  const g = parseInt(h.substring(2, 4), 16) || 0;
  const b = parseInt(h.substring(4, 6), 16) || 0;
  return `rgba(${r},${g},${b},${alpha})`;
}

/** Stable 0..1 value from a string, so each edge's flow particles get a consistent (not-synchronized) phase. */
export function hash01(str) {
  let h = 0;
  for (let i = 0; i < str.length; i += 1) h = (h * 31 + str.charCodeAt(i)) >>> 0;
  return (h % 1000) / 1000;
}

export const isInfra = (n) => ['LOCAL_HOST', 'GATEWAY', 'ROUTER', 'SUBNET', 'INTERNET'].includes(n.type);

export function nodeRadius(n) {
  switch (n.type) {
    case 'SUBNET':
      return 15 + Math.min(14, Math.sqrt(n.deviceCount || 1) * 1.6);
    case 'GATEWAY':
      return 12;
    case 'LOCAL_HOST':
      return 11;
    case 'ROUTER':
      return 9;
    case 'INTERNET':
      return 13;
    case 'NETWORK_DEVICE':
      return 7;
    default:
      return 5.5;
  }
}

/** Solid = observed, dashed = inferred from real data, dotted = assumed / conceptual. */
export function edgeStyle(edge) {
  switch (edge.confidence) {
    case 'OBSERVED':
      return { dash: [], color: 'rgba(147,169,201,0.42)', width: 1.2 };
    case 'INFERRED':
      return { dash: [6, 4], color: 'rgba(124,196,255,0.40)', width: 1.1 };
    case 'ASSUMED':
      return { dash: [1.5, 4], color: 'rgba(244,183,64,0.55)', width: 1.4 };
    default:
      return { dash: [1.5, 4], color: 'rgba(147,169,201,0.30)', width: 1.2 };
  }
}

export function linkDistance(link) {
  const e = link.edge;
  if (e.relation === 'member of subnet') return 30;
  if (e.relation === 'default route') return 70;
  if (e.confidence === 'CONCEPTUAL') return 90;
  return 115;
}

export function linkStrength(link) {
  return link.edge.relation === 'member of subnet' ? 0.9 : 0.5;
}

export function chargeOf(n) {
  switch (n.type) {
    case 'SUBNET':
      return -320 - Math.min(700, (n.deviceCount || 0) * 3);
    case 'GATEWAY':
      return -300;
    case 'ROUTER':
      return -260;
    case 'LOCAL_HOST':
      return -240;
    case 'INTERNET':
      return -200;
    default:
      return -22;
  }
}

export function bounds(nodes) {
  if (!nodes.length) return null;
  let x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity;
  for (const n of nodes) {
    if (n.x === undefined) continue;
    x0 = Math.min(x0, n.x); y0 = Math.min(y0, n.y);
    x1 = Math.max(x1, n.x); y1 = Math.max(y1, n.y);
  }
  if (x0 === Infinity) return null;
  return { x0, y0, x1, y1 };
}
