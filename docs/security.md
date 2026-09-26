# Security model

## What NetScope does and does not do
Does: ICMP echo, traceroute, reverse DNS, NetBIOS name query, reads the local ARP/route tables.
Does **not**: change any device, read files/accounts, capture or intercept traffic, guess passwords, exploit anything,
port-scan, or scan public address space.

## Wi-Fi survey (`/api/wifi`)
Passive only: it reads the list of access points the adapter already hears (`netsh wlan show networks` / `nmcli dev wifi list`).
It never joins, probes, deauthenticates or captures traffic, and it cannot see the clients of other networks. On Windows 11 the OS
may require Location permission before it returns that list.

## Scope gate (`ScopeService`)
* `AUTO` (default): only the subnet of the active interface.
* `CONFIGURED`: CIDRs from `netscope.scope.cidrs`. Only RFC1918 + 100.64/10 accepted. Anything outside the local subnet needs
  `netscope.scope.authorization-confirmed=true`. Scopes above `max-targets` (default 70 000) are refused.
* Every diagnostic/trace request for an IP is checked against the scope. The scope cannot be changed from the browser.
* The approved scope and `authorized-by` are written to the log at startup and shown in the UI.

## Before scanning a college network
Get **written** permission from whoever owns/operates it (network administrator / HOD / IT department). Keep the letter.
Agree the address ranges, the time window and the rate. Tell them the tool is read-only; share `docs/security.md`.
Start with one /24, then widen. Lower `netscope.scan.concurrency` if they ask for a gentler footprint.

## Hardening defaults
* Server binds to `127.0.0.1` - nobody else on the LAN can open your dashboard.
* WebSocket/CORS origins are restricted to the dev server.
* OS commands run as argument lists (no shell) after strict IPv4 validation.
* No credentials are stored; the MySQL password comes from an environment variable.

## Before exposing it on a network
There is **no authentication yet** (roadmap). Do not set `server.address=0.0.0.0` until you add login (Spring Security) and HTTPS.

## Network Zones (multi-subnet) authorization
Every zone besides the auto-detected LOCAL one starts **unauthorized** and cannot be scanned until an administrator
ticks "I have written permission" and names who approved it (`authorizedBy`, written to the event log at creation
time). Zone CIDRs are restricted to private ranges (`10/8`, `172.16/12`, `192.168/16`, `100.64/10`) of `/16` or
smaller - the same public-address refusal and RFC1918 rule as the existing `ScopeService`, enforced independently
for zones by `ZoneValidator`. Routed zones are checked with ICMP + TCP-connect only (never ARP, which cannot cross
a router) and, optionally, a plain `nmap -sT` connect scan restricted to the ports you configured - no exploitation,
no authentication bypass, no content is read from any service. See `docs/zones.md` for the full model.
