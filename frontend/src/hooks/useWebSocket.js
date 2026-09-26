import { useEffect, useRef, useState } from 'react';
import { connect, onState, subscribe } from '../services/websocketService';

/** Calls onMessage({type, at, payload}) for every live message. Returns whether the socket is connected. */
export default function useWebSocket(onMessage) {
  const handler = useRef(onMessage);
  handler.current = onMessage;
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    connect();
    const offMsg = subscribe((m) => handler.current && handler.current(m));
    const offState = onState((s) => setConnected(s === 'open'));
    return () => {
      offMsg();
      offState();
    };
  }, []);

  return { connected };
}
