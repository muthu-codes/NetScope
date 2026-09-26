# Architecture

```
                    React dashboard (Vite)  <---- WebSocket /ws/events (progress, events, scan complete)
                            |  REST /api/*
   +------------------------v--------------------------------------------------+
   |  controller   Network / Device / Topology / Diagnostic / Event / Monitoring|
   +------------------------+--------------------------------------------------+
   |  service                                                                    |
   |   MonitoringService  --> DeviceDiscoveryService --> ScopeService (safety gate)
   |        |                    |   PingService   NeighborTableService         |
   |        |                    |   GatewayService  HostnameResolutionService  |
   |        |                    |   DeviceIntelligenceService  HealthAnalysis  |
   |        +--> TracerouteService --> TopologyService                           |
   |        +--> DiagnosticService     EventService     PersistenceService       |
   +------------------------+--------------------------------------------------+
   |  repository / entity   H2 (default) or MySQL                                |
   +-----------------------------------------------------------------------------+
        OS commands (read-only): ping, tracert/traceroute, arp / Get-NetNeighbor, Get-NetRoute, nbtstat
```

## Scan pipeline (`DeviceDiscoveryService`)
1. `ScopeService.resolveScopes()` - only approved CIDRs (throws `ScopeViolationException` otherwise).
2. Build targets. FULL = every host in scope. REFRESH = devices already known.
3. Rate-controlled ping sweep (fixed thread pool, per-probe timeout). Known devices that miss get two retries so one lost packet is not "offline".
4. Read the neighbour table; fresh entries that did not answer ping become **ARP_ONLY**.
5. Resolve names in parallel with hard timeouts (reverse DNS, then NetBIOS for Windows-looking hosts).
6. Merge into the registry: vendor hint, classification, latency history, state transitions -> events, health analysis.
7. Persist (best-effort), broadcast `SCAN_COMPLETE`.

`MonitoringService` then traces routes (FULL only), runs network diagnostics, builds the topology and saves a snapshot.

## Design decisions
* **Read-only by construction**: only ping/traceroute/DNS/ARP-table reads. No writes to any device.
* **Commands are argument lists**, never shell strings, and every IP is validated first - no command injection.
* **Persistence is best-effort**: if the DB is down, scanning and the live dashboard keep working.
* **Device state is only mutated inside `DeviceDiscoveryService`**; everything else sees immutable `DeviceResponse` snapshots.
* **Two-speed monitoring** keeps a /16 practical: slow full sweeps, fast refresh of known hosts.
* **Honesty over impressiveness**: heuristics are labelled; topology edges carry `evidence` and `confidence`.
