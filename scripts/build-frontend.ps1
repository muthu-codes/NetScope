# Builds the React app into src/main/resources/static so ONE Spring Boot process serves the dashboard on :8080.
$root = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location (Join-Path $root "frontend")

if (-not (Test-Path "node_modules")) { npm install }
npm run build
Write-Host "Done. Restart the backend and open http://localhost:8080" -ForegroundColor Green
