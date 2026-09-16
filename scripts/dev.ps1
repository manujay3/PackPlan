param(
    [ValidateSet('up', 'down', 'status', 'logs', 'check')]
    [string]$Action = 'up'
)
$ErrorActionPreference = 'Stop'
$dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
$dockerPath = if ($dockerCommand) { $dockerCommand.Source } else { 'C:\Program Files\Docker\Docker\resources\bin\docker.exe' }
if (-not (Test-Path -LiteralPath $dockerPath)) { throw 'Docker Desktop was not found. Install it and open a new terminal.' }

Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    switch ($Action) {
        'up' { & $dockerPath compose up --build -d --wait --wait-timeout 180 }
        'down' { & $dockerPath compose down }
        'status' { & $dockerPath compose ps }
        'logs' { & $dockerPath compose logs --tail 80 }
        'check' {
            $app = Invoke-RestMethod http://localhost:5173/api/status -TimeoutSec 10
            $database = Invoke-RestMethod http://localhost:5173/api/database/status -TimeoutSec 10
            if ($app.application -ne 'PackPlan' -or $app.status -ne 'UP' -or $database.status -ne 'UP') {
                throw 'Application or database is not ready.'
            }
            Write-Host 'PASS: frontend proxy -> Spring Boot -> Neo4j. Catalog import remains pending.'
            return
        }
    }
    if ($LASTEXITCODE -ne 0) { throw "Docker command failed (exit $LASTEXITCODE). Ensure Docker Desktop is running; try the logs action." }
    if ($Action -eq 'up') { Write-Host 'PackPlan: http://localhost:5173 | Neo4j: http://localhost:7474/browser/' }
} finally {
    Pop-Location
}
