# Network Zones (multi-subnet monitoring)

NetScope's original discovery is single-subnet: ARP + ICMP on the network your computer is directly connected to.
Network Zones adds monitoring of **other, routed** subnets (labs, VLANs, server networks) without touching that
existing code path. Nothing here replaces local discovery - it is a second, separate layer on top of it.

## Core idea

```
NetScope
   |
   +-- Local discovery (unchanged): ARP + ICMP on the directly-connected subnet
   |
   +-- Network Zones (new): Layer-3 monitoring of OTHER, administrator-declared subnets
        ICMP + TCP connect checks, optional nmap, bounded concurrency, failure-threshold debounce
```

ARP is Layer 2 and does not cross a router, so a routed zone is **never** ARP-scanned. Only ICMP echo and plain
TCP connect checks are used (plus an optional nmap pass - see below). This is why a device's MAC address is shown
as "not available from monitoring host" for anything outside your own segment: getting it would need
infrastructure-level data (SNMP/LLDP from the switches themselves), which is a possible future enhancement, not
something a laptop can produce on its own.

## A zone

| Field | Meaning |
|---|---|
| `name`, `cidr`, `description` | administrator-facing identification. CIDR must be a private range (`10/8`, `172.16/12`, `192.168/16`, `100.64/10`) and `/16` or smaller. |
| `zoneType` | `LOCAL` (auto-added, thin view over existing discovery), `ROUTED`, `SERVER`, `MANAGEMENT`, `CUSTOM` |
| `authorized` / `authorizedBy` | a zone cannot be scanned until you tick "I have written permission" and name who approved it - this is written to the event log |
| `gateway` | optional; used for path/incident correlation ("gateway reachable, zone unreachable" vs "gateway also silent") |
| `methods` | `ICMP`, `TCP`, or both |
| `tcpPorts` | up to 10 ports checked with a plain TCP connect (no banner grab, no exploitation, no authentication) |
| `useNmap` | if nmap is installed, allow the "nmap scan" button to run a `-sT -Pn` connect scan across the whole zone for open-port/service detail |
| `maxConcurrency`, `timeoutMs`, `maxDevices`, `intervalSeconds`, `failureThreshold` | rate control - see below |

The **LOCAL** zone is added automatically at startup from the same interface/gateway data the existing dashboard
already shows. It is a read-only view (it does not re-scan); everything else must be added explicitly by an
administrator.

## Rate control & the "one timeout doesn't mean DOWN" rule

- `maxConcurrency` bounds how many probes are ever in flight for a zone at once (default 20) - `BoundedRunner`
  uses a semaphore + virtual threads, so a large zone never launches thousands of probes at once.
- `failureThreshold` (default 3) drives `HostStateMachine`: one dropped probe leaves the previous state alone
  ("possible failure"), a second consecutive failure marks the device DEGRADED, and only `failureThreshold`
  consecutive failures marks it DOWN. Any single success immediately clears the counter.

## Route/path information

When a zone scan finishes, NetScope traces to the zone's gateway (or, if none is configured, to any device that
just answered). The result is stored as an **observed path** and shown in the zone's topology entry and in
`GET /api/network/zones/routes` (the raw OS routing table, for reference - it is informational only and is not
itself an authorization list).

**A traceroute hop is not proof of physical cabling.** The UI and API always label edges by how they were derived:

| Label | Meaning |
|---|---|
| OBSERVED | measured directly (this host to the first gateway hop; a LOCAL zone's direct connection) |
| INFERRED | derived from the order of traceroute hops - a Layer-3 path, not a cable diagram |
| CONFIGURED | a gateway you typed in yourself |

## Cross-subnet incidents

`IncidentCorrelator` looks at all zones together:

- **ZONE_UNREACHABLE** - no device in a zone answered. If the zone's gateway is reachable, the note says so
  explicitly ("possible zone-level issue... a timeout alone cannot tell which").
- **SHARED_DEPENDENCY** - two or more unhealthy zones trace through the same upstream hop. This is reported as
  "possible common dependency: X. This is correlation, not proof that it caused the problem." - NetScope never
  claims a single upstream device *caused* an outage from traceroute correlation alone.

## Optional nmap integration

If `nmap` is on PATH, `NmapAvailability` detects it once at startup (`nmap -V`) and the zones dashboard shows its
version. The "nmap scan" button on a zone runs:

```
nmap -Pn -sT -p <configured ports> --max-retries 1 --host-timeout <timeoutMs>ms -T4 -oX - <zone CIDR>
```

This is a plain TCP-connect scan (`-sT`, no raw sockets/root needed) limited to the ports you configured for that
zone, with a bounded XML parse (`NmapParser`) merging hostnames/open ports back into the zone's device list. If
nmap is not installed, the button returns a clear message and NetScope's own ICMP/TCP checks continue to work
exactly as before - nmap is additive, never required.

## API summary

See `docs/api.md` for the full table; the zone endpoints all live under `/api/network/zones`.

## What is intentionally NOT included yet

- Physical switch/AP topology (needs SNMP/LLDP - same limitation as the existing single-subnet topology, see
  `docs/topology.md`).
- Long-term latency/availability history charts per zone device (only the live state + one snapshot is kept for
  now; `zone_device` is upserted per scan rather than appending a time series).
- A full admin settings screen for the *global* defaults described in the original spec (per-zone settings cover
  the same ground today via the Add/Edit Zone form).
