# Networking notes

## Gateway
`Get-NetRoute -AddressFamily IPv4 -DestinationPrefix '0.0.0.0/0'`, effective metric = route metric + interface metric, lowest wins.
The gateway MAC comes from the neighbour table (NetScope pings the gateway first so the entry exists).
Linux uses `ip route show default`; macOS uses `route -n get default`.

## Why ping via the OS instead of `InetAddress.isReachable`
Without administrator rights Java falls back to a TCP probe on port 7, which almost nothing answers. The OS `ping` uses real ICMP.
A reply only counts if the line comes from the target IP **and** contains `TTL=` (Windows prints "Destination host unreachable" with exit code 0).

## ARP vs ICMP
Pinging a device on your subnet makes your OS ARP for it first. A PC whose firewall drops ICMP still answers ARP, so it appears
in the neighbour table. NetScope shows that as **ARP only**. Only *fresh* entries (Reachable/Delay/Probe) count; Stale entries do not.

## What is filtered out
Loopback, `0.0.0.0`, multicast (224/4), broadcast/reserved (240/4), link-local (169.254/16), multicast/broadcast/zero MACs,
and any `x.x.x.0` / `x.x.x.255` address from sweeps.

## Heuristics (always labelled as such)
* **Vendor**: MAC prefix (OUI). Randomized (locally administered) MACs - typical for phones - have no vendor.
* **OS family**: reply TTL 64 -> Linux/Android/iOS/macOS, 128 -> Windows, 255 -> network equipment (minus hops).
* **Device type**: hostname keywords, vendor family, TTL - the reason is stored and shown next to the type.

## Names
Reverse DNS first. For hosts whose TTL looks like Windows, `nbtstat -A` reads the NetBIOS machine name (Windows only).

## Health score
Start at 100. Critical latency -40, high -20, packet loss up to -50, jitter -10, flapping -15. Offline = 0, ARP-only = 70 (status "limited").
Thresholds are configurable under `netscope.health.*`.

## Events
`DEVICE_NEW`, `DEVICE_ONLINE`, `DEVICE_OFFLINE`, `DEVICE_RETURNED` (offline longer than `returned-after-seconds`), `GATEWAY_CHANGED`,
`IP_CHANGED`, `MAC_CHANGED` (possible IP conflict / ARP spoofing), `LATENCY_CHANGED`, `NETWORK_INTERFACE_DOWN`, `SCAN_COMPLETED`.
The very first full scan is a silent baseline (no `DEVICE_NEW` flood).
