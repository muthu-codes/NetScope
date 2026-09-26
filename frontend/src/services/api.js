// One tiny fetch wrapper. In dev, Vite proxies /api to Spring Boot; in production Spring serves both.

const BASE = import.meta.env.VITE_API_BASE || '';

export class ApiError extends Error {
  constructor(message, status, body) {
    super(message);
    this.status = status;
    this.body = body;
  }
}

export async function api(path, { method = 'GET', signal, body } = {}) {
  let res;
  const headers = { Accept: 'application/json' };
  let payload;
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }
  try {
    res = await fetch(BASE + path, { method, headers, signal, body: payload });
  } catch (e) {
    if (e.name === 'AbortError') throw e;
    throw new ApiError('Cannot reach the NetScope backend. Is it running on port 8080?', 0, null);
  }
  if (res.status === 204) return null;
  let responseBody = null;
try {
  responseBody = await res.json();
} catch {
  /* empty body */
}
if (!res.ok) {
  throw new ApiError(
    responseBody?.message || `${res.status} ${res.statusText}`,
    res.status,
    responseBody
  );
}
return responseBody;
}

export const qs = (params = {}) => {
  const p = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') p.set(k, v);
  });
  const s = p.toString();
  return s ? `?${s}` : '';
};
