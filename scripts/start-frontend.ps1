# Starts the React dev server (http://localhost:5173). The backend must be running on port 8080.
$root = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location (Join-Path $root "frontend")

if (-not (Test-Path "node_modules")) {
    Write-Host "Installing frontend dependencies (first run only) ..." -ForegroundColor Cyan
    npm install
}
npm run dev
