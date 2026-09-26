# REST + WebSocket API

Base URL `http://localhost:8080`. All endpoints are read-only except the monitoring controls (POST).

| Method & path | Description |
|---|---|
| `GET /api/network/configuration` | hostname, active interface, IP/mask/CIDR, gateway (+MAC), DNS, IPv6, warnings |
| `GET /api/network/interfaces` | every interface, active one marked |
| `GET /api/network/active` | the active interface (404 if none) |
| `GET /api/network/scope` | the authorized scan scope, validity, estimated duration |
| `GET /api/network/summary` | configuration + device stats + monitoring status + scope + overall network status |
| `GET /api/devices?q=&state=&type=&health=&subnet=` | devices with health, issues, diagnosis, latency history |
| `GET /api/devices/{ip}` / `/{ip}/history` | one device / stored latency observations |
| `GET /api/devices/subnets` | per-subnet summary |
| `GET /api/topology` | nodes, edges (with evidence + confidence), summary, notes, disclaimer |
| `GET /api/topology/traces` | raw traceroute results |
| `GET /api/diagnostics/ping?ip=&count=` | multi-packet ping (IP must be in scope) |
| `GET /api/diagnostics/network` | run network-wide checks now (10-20 s) |
| `GET /api/diagnostics/network/last` | last result (204 if none) |
| `GET /api/diagnostics/device?ip=&count=&trace=` | deep test of one device |
| `GET /api/events?limit=&type=&severity=` | newest first |
| `GET /api/wifi` | passive Wi-Fi survey: current connection, nearby access points, channel usage, insights (1-2 s) |
| `GET /api/monitoring/status` | scan state, progress, timings, last error |
| `POST /api/monitoring/start` `/stop` | pause / resume automatic monitoring |
| `POST /api/monitoring/scan?mode=FULL\|REFRESH` | start a scan (202) or 409 if one is running |
| `GET /actuator/health` | liveness |

## Network Zones (multi-subnet) - `/api/network/zones`
Full description: `docs/zones.md`.

| Endpoint | Notes |
|---|---|
| `GET /api/network/zones` | list all zones with live counts |
| `POST /api/network/zones` | create; body validated (private CIDR, `/16` or smaller, authorization required for non-LOCAL) |
| `GET` / `PUT` / `DELETE /api/network/zones/{id}` | read / update / delete (LOCAL zone cannot be deleted) |
| `POST /api/network/zones/{id}/enable?value=` | enable/disable |
| `POST /api/network/zones/{id}/scan` | start a background scan (202); 409 if disabled/unauthorized |
| `GET /api/network/zones/{id}/scan-status` | progress for the running scan, if any |
| `POST /api/network/zones/{id}/nmap-scan` | optional deeper nmap pass (only if nmap is installed and the zone allows it) |
| `GET /api/network/zones/{id}/devices` | devices discovered in that zone |
| `GET /api/network/zones/overview` | dashboard aggregate: zones, devices, online/degraded/offline, services, active incidents |
| `GET /api/network/zones/incidents` | cross-subnet incidents (evidence-based, see `docs/zones.md`) |
| `GET /api/network/zones/topology` | multi-zone graph: nodes + OBSERVED/INFERRED/CONFIGURED edges |
| `GET /api/network/zones/routes` | host routing table (informational) |

Errors follow the same `{ "timestamp", "status", "error", "message" }` shape; invalid zone input is `400`, scanning a
disabled/unauthorized zone is `409`.

WebSocket adds one message type: `ZONE_SCAN_COMPLETE` `{zoneId}` (clients then re-fetch the zone list/overview).

Errors are JSON: `{ "timestamp", "status", "error", "message" }`. `403 SCOPE_VIOLATION` = outside the authorized scope.

## WebSocket `/ws/events`
Messages: `{ "type", "at", "payload" }`

| type | payload |
|---|---|
| `HELLO` | greeting |
| `SCAN_PROGRESS` | `{running, mode, phase, done, total, startedAt}` |
| `SCAN_COMPLETE` | scan result summary (clients then re-fetch devices/topology) |
| `SCAN_ERROR` | `{message}` |
| `EVENT` | a network event |
