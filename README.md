# NetScope

**Real-time network discovery, topology visualization and diagnostics platform.**
Read-only. Permission-based. Java 21 · Spring Boot · JPA (H2 / MySQL) · WebSocket · React · Vite.

NetScope finds the devices it can observe inside an *authorized* network scope, measures their health (ping, packet loss,
jitter), draws the topology from real data, tells you what is wrong in plain language, and updates live in the browser.

## What you get

| Area | What it does |
|---|---|
| Discovery | Local interface, subnet, default gateway (from the **routing table**), ARP table, ICMP sweep, reverse-DNS + NetBIOS names |
| Intelligence | MAC vendor *hints*, device type with the evidence for it, OS family from TTL - all labelled as heuristics |
| Topology | Force-directed graph: subnets as hubs, router hops from **traceroute**, solid = observed / dashed = inferred / dotted = assumed |
| Health | 0-100 score per device, issues (latency, loss, jitter, flapping, blocked ping, offline) with a written diagnosis and next step |
| Monitoring | Full sweep every N minutes + fast refresh of known devices, events (new/online/offline/returned/MAC changed/gateway changed...) |
| Diagnostics | Gateway, DNS, internet, interface checks; per-device deep test with optional route trace |
| Persistence | Devices, observations (latency history), events, scan sessions, topology snapshots in H2 (default) or MySQL |
| Wi-Fi survey | Passive list of nearby access points: signal, channel, band, security (flags open/WEP), maker hint, channel congestion, plain-language insights |
| Live UI | WebSocket pushes scan progress and events; no manual refresh |
| **Network Zones** (multi-subnet) | Add routed subnets (labs, servers, other VLANs) as authorized *zones*. Layer-3 monitoring (ICMP + TCP, optional nmap), bounded concurrency, failure-threshold debounce, per-zone dashboard, cross-subnet incident correlation - see `docs/zones.md` |

## Quick start (Windows 11, project root `Z:\NetScope\NetScope`)

**Extract** the zip so `pom.xml` sits directly in `Z:\NetScope\NetScope` (no nested `NetScope` folder). If an older version of the
backend exists, delete `src\main\java\com\netscope` first so no old classes conflict.

**Terminal 1 - backend** (Java 21 + Maven 3.9):
```powershell
cd Z:\NetScope\NetScope
mvn spring-boot:run
```
Open **http://localhost:8080** - the built dashboard is already included, so this alone is enough.

**Terminal 2 - frontend dev server** (optional, for editing the UI; Node 18+):
```powershell
cd Z:\NetScope\NetScope\frontend
npm install
npm run dev
```
Open **http://localhost:5173**. When you are done editing, `npm run build` writes the UI into `src/main/resources/static`.

Default behaviour (`netscope.scope.mode=AUTO`): scans only the subnet your computer is connected to. Nothing to configure.

## Scanning the college network (with written permission)

1. Get the address blocks and written approval from the network owner (see `docs/security.md`).
2. Edit `src/main/resources/application-college.properties`:
   ```properties
   netscope.scope.cidrs=10.114.0.0/16
   netscope.scope.authorized-by=<name / designation / date of the permission>
   ```
3. Start with that profile:
   ```powershell
   mvn spring-boot:run "-Dspring-boot.run.profiles=college"
   ```
NetScope refuses public ranges, refuses scopes larger than `max-targets`, and refuses to scan beyond the local subnet unless
`authorization-confirmed=true`. The scope is shown in the UI header and written to the audit log at startup.

Expect: a /24 usually finishes in 10-20 seconds; a /16 (65k addresses) takes roughly 10-30 minutes for the first sweep (progress is shown live),
then only known devices are re-checked every 30 s. Run it from a **wired** port on the network you want to observe for the most complete view.

## What it can and cannot see (important for your demo)

* MAC addresses exist only for devices on **your own Layer-2 segment/VLAN**. Devices behind routers have IPs, names and ping data but no MAC.
* Devices that block ping still appear as **"ARP only"** when they are on your segment.
* Routed subnets are placed using real **traceroute** hops. Physical switch/AP/cable connections are **not** drawn - that needs authorized
  SNMP/LLDP data (roadmap step 14). The UI says "Observed / inferred topology" everywhere for this reason.
* AP/client isolation, VLANs, firewalls and sleeping devices can hide things. That is a limit of observation, not proof of absence.

## Database

Default: embedded H2 file database in `./data` (MySQL-compatible mode) - zero setup.
MySQL: `scripts\setup-database.ps1`, then `mvn spring-boot:run "-Dspring-boot.run.profiles=mysql"`.

## Tests

```powershell
mvn test
```
34 unit tests cover IP/CIDR/MAC handling, ping/ARP/route/traceroute/NetBIOS/netsh/nmcli parsing and the health rules.

## Project layout

```
pom.xml  README.md
src/main/java/com/netscope/{controller,service,service/zone,model,entity,repository,dto,dto/zone,zone,websocket,scheduler,config,util,exception}
src/main/resources/{application*.properties, oui.csv, static/}
src/test/java/...            frontend/ (React + Vite)      database/   docs/   scripts/   android/ (planned)
```

Docs: `docs/architecture.md` · `networking.md` · `api.md` · `topology.md` · `security.md` · `deployment.md` · `zones.md` (multi-subnet)
