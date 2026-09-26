# OPTIONAL: prepares MySQL 8 for NetScope (the default H2 database needs no setup).
#   powershell -ExecutionPolicy Bypass -File .\scripts\setup-database.ps1 -RootPassword "yourMySqlRootPassword" -AppPassword "choose-a-password"
# Then run the backend with the mysql profile:
#   $env:NETSCOPE_DB_USER = "netscope"; $env:NETSCOPE_DB_PASSWORD = "choose-a-password"
#   .\scripts\start-backend.ps1 -Profile mysql
param(
    [Parameter(Mandatory = $true)][string]$RootPassword,
    [Parameter(Mandatory = $true)][string]$AppPassword,
    [string]$MysqlExe = "mysql"
)

$sql = @"
CREATE DATABASE IF NOT EXISTS netscope CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'netscope'@'localhost' IDENTIFIED BY '$AppPassword';
GRANT ALL PRIVILEGES ON netscope.* TO 'netscope'@'localhost';
FLUSH PRIVILEGES;
"@

$sql | & $MysqlExe -u root "-p$RootPassword"
if ($LASTEXITCODE -eq 0) {
    Write-Host "MySQL database 'netscope' and user 'netscope' are ready." -ForegroundColor Green
} else {
    Write-Host "MySQL setup failed. Is MySQL running and is 'mysql' on your PATH?" -ForegroundColor Red
}
