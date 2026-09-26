# Deployment

## Development
Backend `mvn spring-boot:run` (8080) + frontend `npm run dev` (5173, proxies `/api` and `/ws`).

## Single jar
```powershell
cd Z:\NetScope\NetScope
.\scripts\build-frontend.ps1        # React -> src/main/resources/static
mvn -DskipTests package
java -jar target\netscope-1.0.0.jar
```
Open http://localhost:8080.

## Profiles
| Profile | Effect |
|---|---|
| (default) | H2 file DB in `./data`, scope AUTO |
| `mysql` | MySQL at localhost:3306, user/password from `NETSCOPE_DB_USER` / `NETSCOPE_DB_PASSWORD` |
| `college` | scope CONFIGURED + higher concurrency (edit the CIDRs first) |
Combine: `-Dspring-boot.run.profiles=college,mysql`.

## Sizing
Observations are stored on full scans (and on state changes), pruned after `netscope.retention.observation-days` (default 7).
Vendor list: download https://standards-oui.ieee.org/oui/oui.csv and set `netscope.oui.file` for full coverage.

## Troubleshooting
* "Cannot reach the NetScope backend": start the backend first; check port 8080 is free.
* No gateway/DNS on Windows: run `powershell -Command "Get-NetRoute -DestinationPrefix 0.0.0.0/0"` to check the OS cmdlets work.
* Scanning blocked banner: read the message - it names the exact property to fix.
* Few devices found: you may be on a Wi-Fi with client isolation; try a wired port on the target VLAN.
