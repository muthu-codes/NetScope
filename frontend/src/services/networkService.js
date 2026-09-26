import { api, qs } from './api';

export const getSummary = () => api('/api/network/summary');
export const getConfiguration = () => api('/api/network/configuration');
export const getInterfaces = () => api('/api/network/interfaces');
export const getScope = () => api('/api/network/scope');
export const getTopology = () => api('/api/topology');
export const getEvents = (params) => api(`/api/events${qs(params)}`);
export const getMonitoringStatus = () => api('/api/monitoring/status');
export const startMonitoring = () => api('/api/monitoring/start', { method: 'POST' });
export const stopMonitoring = () => api('/api/monitoring/stop', { method: 'POST' });
export const startScan = (mode = 'FULL') => api(`/api/monitoring/scan${qs({ mode })}`, { method: 'POST' });
export const runNetworkDiagnostics = () => api('/api/diagnostics/network');
export const getLastDiagnostics = () => api('/api/diagnostics/network/last');
