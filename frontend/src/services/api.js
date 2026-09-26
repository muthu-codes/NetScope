
// Central API wrapper for NetScope.
// Authentication is stored only for the current browser session.

const BASE = import.meta.env.VITE_API_BASE || '';

const AUTH_STORAGE_KEY = 'netscope_auth';

export class ApiError extends Error {
  constructor(message, status, body) {
    super(message);
    this.status = status;
    this.body = body;
  }
}

export function setAuth(username, password) {
  const token = btoa(`${username}:${password}`);
  sessionStorage.setItem(AUTH_STORAGE_KEY, token);
}

export function clearAuth() {
  sessionStorage.removeItem(AUTH_STORAGE_KEY);
}

export function hasAuth() {
  return Boolean(sessionStorage.getItem(AUTH_STORAGE_KEY));
}

function getAuthHeader() {
  const token = sessionStorage.getItem(AUTH_STORAGE_KEY);
  return token ? `Basic ${token}` : null;
}

export async function api(path, { method = 'GET', signal, body } = {}) {
  let res;

  const headers = {
    Accept: 'application/json'
  };

  const authHeader = getAuthHeader();

  if (authHeader) {
    headers.Authorization = authHeader;
  }

  let payload;

  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }

  try {
    res = await fetch(BASE + path, {
      method,
      headers,
      signal,
      body: payload
    });
  } catch (e) {
    if (e.name === 'AbortError') {
      throw e;
    }

    throw new ApiError(
      'Cannot reach the NetScope backend. Is it running on port 8080?',
      0,
      null
    );
  }

  if (res.status === 204) {
    return null;
  }

  let responseBody = null;

  try {
    responseBody = await res.json();
  } catch {
    // Empty or non-JSON response.
  }

  if (res.status === 401) {
    clearAuth();

    throw new ApiError(
      'Authentication required or credentials are invalid.',
      401,
      responseBody
    );
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
    if (v !== undefined && v !== null && v !== '') {
      p.set(k, v);
    }
  });

  const s = p.toString();

  return s ? `?${s}` : '';
};

