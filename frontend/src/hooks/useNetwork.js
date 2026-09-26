import { useCallback, useEffect, useRef, useState } from 'react';
import { getEvents, getSummary, getTopology, getInterfaces } from '../services/networkService';

const EMPTY_SCAN = { running: false, phase: 'Idle', done: 0, total: 0 };

/** Summary, topology, events and live scan progress for the whole app. */
export default function useNetwork() {
  const [summary, setSummary] = useState(null);
  const [topology, setTopology] = useState(null);
  const [events, setEvents] = useState([]);
  const [interfaces, setInterfaces] = useState([]);
  const [progress, setProgressState] = useState(EMPTY_SCAN);
  const [error, setError] = useState(null);
  const seen = useRef(new Set());

  const refreshSummary = useCallback(async () => {
    try {
      setSummary(await getSummary());
      setError(null);
    } catch (e) {
      setError(e.message);
    }
  }, []);

  const refreshTopology = useCallback(async () => {
    try {
      setTopology(await getTopology());
    } catch {
      /* summary error already shown */
    }
  }, []);

  const refreshEvents = useCallback(async () => {
    try {
      const list = await getEvents({ limit: 200 });
      seen.current = new Set(list.map((e) => e.id));
      setEvents(list);
    } catch {
      /* ignore */
    }
  }, []);

  const refreshInterfaces = useCallback(async () => {
    try {
      setInterfaces(await getInterfaces());
    } catch {
      /* ignore */
    }
  }, []);

  const refreshAll = useCallback(() => {
    refreshSummary();
    refreshTopology();
    refreshEvents();
  }, [refreshSummary, refreshTopology, refreshEvents]);

  const setProgress = useCallback((p) => setProgressState(p && p.running ? p : EMPTY_SCAN), []);

  const pushEvent = useCallback((ev) => {
    if (seen.current.has(ev.id)) return;
    seen.current.add(ev.id);
    setEvents((old) => [ev, ...old].slice(0, 300));
  }, []);

  useEffect(() => {
    refreshAll();
    refreshInterfaces();
    const t = setInterval(refreshSummary, 15000);
    return () => clearInterval(t);
  }, [refreshAll, refreshInterfaces, refreshSummary]);

  const monitoring = summary?.monitoring;
  const scan = progress.running
    ? progress
    : monitoring?.scanning
    ? { running: true, phase: monitoring.phase, done: monitoring.progressDone, total: monitoring.progressTotal }
    : EMPTY_SCAN;

  return {
    summary, topology, events, interfaces, scan, error,
    refreshAll, refreshSummary, refreshTopology, refreshEvents, refreshInterfaces, setProgress, pushEvent
  };
}
