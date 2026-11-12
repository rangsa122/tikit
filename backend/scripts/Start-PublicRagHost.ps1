param([switch]$LocalOnly)

$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$localUrl = 'http://127.0.0.1:18081'
. (Join-Path $PSScriptRoot 'Tailscale-Command.ps1')

function Get-HostHealth {
    try { Invoke-RestMethod -Uri "$localUrl/health" -TimeoutSec 2 } catch { $null }
}

function Assert-HttpStatus([string]$Url, [int]$Expected) {
    $actual = 0
    try { $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 5; $actual = [int]$response.StatusCode }
    catch { if ($_.Exception.Response) { $actual = [int]$_.Exception.Response.StatusCode } else { throw } }
    if ($actual -ne $Expected) { throw "Unexpected endpoint status ($actual instead of $Expected)." }
}

$models = Invoke-RestMethod -Uri 'http://127.0.0.1:11434/api/tags' -TimeoutSec 5
foreach ($model in @('qwen3-embedding:8b', 'qwen3:8b')) {
    if ($model -notin @($models.models.name)) { throw "Required host model is missing: $model" }
}

$health = Get-HostHealth
if (-not $health) {
    $stdout = Join-Path $projectRoot 'build/rag-public-host.stdout.log'
    $stderr = Join-Path $projectRoot 'build/rag-public-host.stderr.log'
    [IO.Directory]::CreateDirectory((Join-Path $projectRoot 'build')) | Out-Null
    $previousOllamaUrl = $env:RAG_OLLAMA_BASE_URL
    $previousGradleHome = $env:GRADLE_USER_HOME
    try {
        $env:RAG_OLLAMA_BASE_URL = 'http://127.0.0.1:11434'
        if (-not $env:GRADLE_USER_HOME) {
            $env:GRADLE_USER_HOME = Join-Path ([Environment]::GetFolderPath('UserProfile')) '.gradle'
        }
        Write-Host 'Starting the RAG host in the background. Logs: build/rag-public-host.stdout.log'
        $process = Start-Process -FilePath $env:ComSpec -ArgumentList '/d','/c','gradlew.bat runRagPublicHost' `
                -WorkingDirectory $projectRoot -WindowStyle Hidden -RedirectStandardOutput $stdout `
                -RedirectStandardError $stderr -PassThru
    } finally {
        $env:RAG_OLLAMA_BASE_URL = $previousOllamaUrl
        $env:GRADLE_USER_HOME = $previousGradleHome
    }
    [IO.File]::WriteAllText((Join-Path $projectRoot 'build/rag-public-host.pid'), [string]$process.Id)
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        Start-Sleep -Seconds 2
        $health = Get-HostHealth
        if ($health) { break }
        if ($process.HasExited) { throw "RAG host exited. Check $stderr and $stdout." }
    }
}
if (-not $health -or $health.service -ne 'tikit-rag-host') { throw 'RAG-only host did not become ready on port 18081.' }

# Verify authentication and route isolation before enabling the public HTTPS tunnel.
Assert-HttpStatus "$localUrl/api/v1/rag/questions" 401
Assert-HttpStatus "$localUrl/api/tags" 404
Write-Host 'RAG-only host is ready; anonymous inference is blocked and Ollama management is not exposed.'
if ($LocalOnly) { return }

$tailscale = Join-Path $env:ProgramFiles 'Tailscale/tailscale.exe'
if (-not (Test-Path -LiteralPath $tailscale)) { throw 'The host PC requires Tailscale. Teammates do not need it.' }
$status = Invoke-TailscaleUtf8 -ExecutablePath $tailscale -Arguments @('status','--json') -AsJson
if ($status.BackendState -ne 'Running' -or -not $status.Self.DNSName) { throw 'Sign in to Tailscale on the host PC first.' }
$publicUrl = 'https://' + $status.Self.DNSName.TrimEnd('.')

# Refuse to overwrite any unrelated service already on HTTPS port 443.
$serve = Invoke-TailscaleUtf8 -ExecutablePath $tailscale -Arguments @('serve','status','--json') -AsJson
$tcp443 = if ($serve.TCP) { $serve.TCP.PSObject.Properties | Where-Object { $_.Name -eq '443' } } else { $null }
$web443 = if ($serve.Web) { @($serve.Web.PSObject.Properties | Where-Object { $_.Name.EndsWith(':443') }) } else { @() }
if ($tcp443 -and $web443.Count -eq 0) { throw 'Port 443 already has an unrelated service.' }
foreach ($web in $web443) {
    $handlers = @($web.Value.Handlers.PSObject.Properties)
    if ($handlers.Count -ne 1 -or $handlers[0].Name -ne '/' -or $handlers[0].Value.Proxy -ne $localUrl) {
        throw 'Port 443 already has an unrelated service.'
    }
}

Invoke-TailscaleUtf8 -ExecutablePath $tailscale -Arguments @('funnel','--bg','--https=443',$localUrl) -TimeoutSeconds 60
$publicHealth = Invoke-RestMethod -Uri "$publicUrl/health" -TimeoutSec 30
if ($publicHealth.service -ne 'tikit-rag-host') { throw 'The public URL is not reaching the RAG-only host.' }
Assert-HttpStatus "$publicUrl/api/v1/rag/questions" 401
Assert-HttpStatus "$publicUrl/api/tags" 404
Write-Host "Public RAG API: $publicUrl/api/v1/rag/questions" -ForegroundColor Green
Write-Host 'Teammates only need the updated backend and their usual project login. No Ollama, VPN or new AI environment variables.'
Write-Host 'The host and all team backends must use the existing shared project JWT_SECRET.'
Write-Host 'This PC, Ollama and the RAG host must stay running.'
