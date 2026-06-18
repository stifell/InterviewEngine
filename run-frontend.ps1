# Start the frontend (Vite dev server).
# Usage:  .\run-frontend.ps1
$ErrorActionPreference = 'Stop'

Set-Location (Join-Path $PSScriptRoot 'frontend')
if (-not (Test-Path 'node_modules')) {
    Write-Host "node_modules not found - installing dependencies (npm install)..." -ForegroundColor Yellow
    npm install
}
Write-Host "Frontend: Vite -> http://localhost:5173" -ForegroundColor Cyan
npm run dev
