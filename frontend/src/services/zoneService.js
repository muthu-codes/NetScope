import { api } from './api';

export const listZones = () => api('/api/network/zones');
export const getZone = (id) => api(`/api/network/zones/${id}`);
export const createZone = (body) => api('/api/network/zones', { method: 'POST', body });
export const updateZone = (id, body) => api(`/api/network/zones/${id}`, { method: 'PUT', body });
export const deleteZone = (id) => api(`/api/network/zones/${id}`, { method: 'DELETE' });
export const enableZone = (id, value) => api(`/api/network/zones/${id}/enable?value=${value}`, { method: 'POST' });
export const scanZone = (id) => api(`/api/network/zones/${id}/scan`, { method: 'POST' });
export const nmapScanZone = (id) => api(`/api/network/zones/${id}/nmap-scan`, { method: 'POST' });
export const zoneScanStatus = (id) => api(`/api/network/zones/${id}/scan-status`);
export const zoneDevices = (id) => api(`/api/network/zones/${id}/devices`);
export const zonesOverview = () => api('/api/network/zones/overview');
export const zoneIncidents = () => api('/api/network/zones/incidents');
export const zoneTopology = () => api('/api/network/zones/topology');
export const routeTable = () => api('/api/network/routes');
