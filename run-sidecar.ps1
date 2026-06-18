# Start the Python sidecar (ASR + diarization + prosody).
# Loads settings/secrets from .env, then launches uvicorn.
# Usage:  .\run-sidecar.ps1
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
Set-Location (Join-Path $PSScriptRoot 'sidecar')
Write-Host "Sidecar: WHISPER_DEVICE=$env:WHISPER_DEVICE COMPUTE=$env:WHISPER_COMPUTE_TYPE -> http://0.0.0.0:8001" -ForegroundColor Cyan
& .\.venv\Scripts\uvicorn.exe main:app --host 0.0.0.0 --port 8001
