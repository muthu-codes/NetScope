// Single shared WebSocket to /ws/events with automatic reconnect (exponential back-off, max 10 s).

const listeners = new Set();
const stateListeners = new Set();
let socket = null;
let retry = 0;
let timer = null;
let state = 'connecting';

function setState(s) {
  state = s;
  stateListeners.forEach((fn) => fn(s));
}

function url() {
  const proto = window.location.protocol === 'https:' ? 'wss' : 'ws';
  return `${proto}://${window.location.host}/ws/events`;
}

export function connect() {
  if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) return;
  clearTimeout(timer);
  setState('connecting');
  try {
    socket = new WebSocket(url());
  } catch {
    scheduleReconnect();
    return;
  }
  socket.onopen = () => {
    retry = 0;
    setState('open');
  };
  socket.onmessage = (ev) => {
    try {
      const msg = JSON.parse(ev.data);
      listeners.forEach((fn) => fn(msg));
    } catch {
      /* ignore malformed frame */
    }
  };
  socket.onclose = () => {
    setState('closed');
    scheduleReconnect();
  };
  socket.onerror = () => socket && socket.close();
}

function scheduleReconnect() {
  clearTimeout(timer);
  retry += 1;
  timer = setTimeout(connect, Math.min(10000, 500 * 2 ** Math.min(retry, 5)));
}

export function subscribe(fn) {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

export function onState(fn) {
  stateListeners.add(fn);
  fn(state);
  return () => stateListeners.delete(fn);
}
