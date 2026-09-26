import { useCallback, useEffect, useState } from 'react';
import { getDevices } from '../services/deviceService';

export default function useDevices() {
  const [devices, setDevices] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const refresh = useCallback(async () => {
    try {
      setDevices(await getDevices());
      setError(null);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refresh();
    const t = setInterval(refresh, 30000);      // safety net if the WebSocket is down
    return () => clearInterval(t);
  }, [refresh]);

  return { devices, loading, error, refresh };
}
