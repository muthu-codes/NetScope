// Small display helpers shared by all pages.

export const fmtMs = (v, digits = 0) =>
  v === null || v === undefined ? '—' : `${Number(v).toFixed(digits)} ms`;

export const fmtPct = (v) => (v === null || v === undefined ? '—' : `${Number(v).toFixed(0)}%`);

export function relTime(iso) {
  if (!iso) return 'never';
  const s = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 1000));
  if (s < 5) return 'just now';
  if (s < 60) return `${s}s ago`;
  if (s < 3600) return `${Math.floor(s / 60)} min ago`;
  if (s < 86400) return `${Math.floor(s / 3600)} h ago`;
  return `${Math.floor(s / 86400)} d ago`;
}

export function fmtTime(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

export function fmtDateTime(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' });
}

export function fmtDuration(ms) {
  if (ms === null || ms === undefined) return '—';
  if (ms < 1000) return `${ms} ms`;
  const s = Math.round(ms / 1000);
  if (s < 60) return `${s} s`;
  return `${Math.floor(s / 60)} min ${s % 60} s`;
}

export const ipToNum = (ip) => ip.split('.').reduce((a, p) => a * 256 + Number(p), 0);
export const compareIp = (a, b) => ipToNum(a) - ipToNum(b);

export const STATE_LABEL = {
  REACHABLE: 'Online',
  ARP_ONLY: 'ARP only',
  UNREACHABLE: 'Offline',
  UNKNOWN: 'Unknown'
};

export const HEALTH_LABEL = {
  HEALTHY: 'Healthy',
  DEGRADED: 'Degraded',
  CRITICAL: 'Critical',
  LIMITED: 'Limited',
  OFFLINE: 'Offline',
  UNKNOWN: 'Unknown'
};

export const TYPE_LABEL = {
  LOCAL_HOST: 'This computer',
  GATEWAY: 'Gateway',
  ROUTER: 'Router hop',
  COMPUTER: 'Computer',
  MOBILE: 'Mobile',
  PRINTER: 'Printer',
  NETWORK_DEVICE: 'Network device',
  DEVICE: 'Device',
  UNKNOWN: 'Unknown',
  SUBNET: 'Subnet',
  INTERNET: 'Internet'
};

export const EVENT_LABEL = {
  DEVICE_NEW: 'New device',
  DEVICE_ONLINE: 'Online',
  DEVICE_OFFLINE: 'Offline',
  DEVICE_RETURNED: 'Returned',
  GATEWAY_CHANGED: 'Gateway changed',
  IP_CHANGED: 'IP changed',
  MAC_CHANGED: 'MAC changed',
  LATENCY_CHANGED: 'Latency',
  NETWORK_INTERFACE_DOWN: 'Interface down',
  SCAN_COMPLETED: 'Scan complete'
};

export const healthClass = (status) => `tone-${(status || 'UNKNOWN').toLowerCase()}`;
