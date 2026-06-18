# Start the backend (Spring Boot, profile 'local' - H2 in-memory).
# Loads GEMINI_API_KEY from .env, then runs mvnw spring-boot:run.
# Usage:  .\run-backend.ps1
$ErrorActionPreference = 'Stop'

# --- Load .env ---
$envFile = Join-Path $PSScriptRoot '.env'
if (-not (Test-Path $envFile)) {
    Write-Error ".env not found in $PSScriptRoot - copy .env.example to .env and fill it in"
}
Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#')) {
        $name, $value = $line -split '=', 2
        [Environment]::SetEnvironmentVariable($name.Trim(), $value.Trim(), 'Process')
    }
}

# --- Run ---
Set-Location (Join-Path $PSScriptRoot 'backend')
Write-Host "Backend: profile local (H2) -> http://localhost:8080" -ForegroundColor Cyan
& .\mvnw spring-boot:run "-Dspring-boot.run.profiles=local"
