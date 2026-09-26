import { api, qs } from './api';

export const getDevices = (params) => api(`/api/devices${qs(params)}`);
export const getDevice = (ip) => api(`/api/devices/${ip}`);
export const getHistory = (ip) => api(`/api/devices/${ip}/history`);
export const getSubnets = () => api('/api/devices/subnets');
export const pingDevice = (ip, count = 5) => api(`/api/diagnostics/ping${qs({ ip, count })}`);
export const diagnoseDevice = (ip, { count = 8, trace = false } = {}) =>
  api(`/api/diagnostics/device${qs({ ip, count, trace })}`);
