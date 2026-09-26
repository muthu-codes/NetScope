# Starts the Spring Boot backend (http://localhost:8080). Run from any folder:
#   powershell -ExecutionPolicy Bypass -File .\scripts\start-backend.ps1
#   powershell -ExecutionPolicy Bypass -File .\scripts\start-backend.ps1 -Profile college
#   powershell -ExecutionPolicy Bypass -File .\scripts\start-backend.ps1 -Profile mysql
param([string]$Profile = "")

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $root

if ($Profile -ne "") {
    Write-Host "Starting NetScope backend with profile '$Profile' ..." -ForegroundColor Cyan
    mvn spring-boot:run "-Dspring-boot.run.profiles=$Profile"
} else {
    Write-Host "Starting NetScope backend ..." -ForegroundColor Cyan
    mvn spring-boot:run
}
