# Topology model

NetScope shows **observed / inferred** topology. It never claims physical connections it cannot prove.

| Edge | Evidence | Confidence | Drawn |
|---|---|---|---|
| local host -> gateway | routing table | OBSERVED | solid |
| gateway -> subnet on the same LAN | devices' MACs in the ARP table | OBSERVED | solid |
| device -> subnet hub (same LAN) | ARP | OBSERVED | solid |
| device -> subnet hub (routed) | IP addressing | INFERRED | dashed |
| router hop -> router hop -> subnet | traceroute | INFERRED | dashed |
| gateway -> routed subnet with no trace | none | ASSUMED | dotted, amber |
| gateway -> Internet | live probe succeeded | CONCEPTUAL | dotted, only when reachable |

Nodes: `LOCAL_HOST`, `GATEWAY`, `ROUTER` (a hop that forwarded traceroute packets), `SUBNET` (a /24 hub with device counts and worst health),
devices (`COMPUTER`, `MOBILE`, `PRINTER`, `NETWORK_DEVICE`, `DEVICE`, `UNKNOWN`), `INTERNET`.

One representative device per routed /24 is traced (`netscope.topology.*`), refreshed every 15 minutes.

## Not included yet
Switch / access-point / cable topology. It needs authorized management data (SNMP, LLDP/CDP, controller APIs) - roadmap step 14.
The graph model already has room for it: add nodes/edges with `evidence=LLDP|SNMP`, `confidence=OBSERVED`.

## Multi-subnet zones
The single-subnet graph above is unchanged. `GET /api/network/zones/topology` returns a **separate** multi-zone
graph (host -> gateway hops -> zones) with the same evidence discipline, using three edge types instead of four:
OBSERVED, INFERRED (traceroute hop order - not cabling), and CONFIGURED (an administrator-entered gateway). See
`docs/zones.md`.
