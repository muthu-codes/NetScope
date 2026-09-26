import { useCallback, useEffect, useState } from 'react';
import { listZones, zonesOverview, zoneIncidents } from '../services/zoneService';

/** Zones list + overview, polled like the rest of the dashboard. */
export default function useZones() {
  const [zones, setZones] = useState([]);
  const [overview, setOverview] = useState(null);
  const [incidents, setIncidents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const refresh = useCallback(async () => {
    try {
      const [z, o, i] = await Promise.all([listZones(), zonesOverview(), zoneIncidents()]);
      setZones(z);
      setOverview(o);
      setIncidents(i);
      setError(null);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refresh();
    const t = setInterval(refresh, 10000);
    return () => clearInterval(t);
  }, [refresh]);

  return { zones, overview, incidents, loading, error, refresh };
}
