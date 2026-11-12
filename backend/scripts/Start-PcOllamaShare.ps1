param([ValidateRange(1024,65535)][int]$Port = 11434)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Tailscale-Command.ps1')
$tailscaleCommand = Get-Command tailscale -ErrorAction SilentlyContinue
$tailscale = if ($tailscaleCommand) { $tailscaleCommand.Source } else { Join-Path $env:ProgramFiles 'Tailscale/tailscale.exe' }
if (-not (Test-Path -LiteralPath $tailscale)) { throw 'Install Tailscale and sign in first.' }

$status = Invoke-TailscaleUtf8 -ExecutablePath $tailscale -Arguments @('status','--json') -AsJson
if ($status.BackendState -ne 'Running') { throw 'Tailscale is not connected. Sign in to the Tailscale app, then run this script again.' }
$ipv4 = @($status.Self.TailscaleIPs | Where-Object { $_ -match '^\d+\.\d+\.\d+\.\d+$' }) | Select-Object -First 1
if (-not $ipv4) { throw 'No Tailscale IPv4 address is available.' }

# Confirm both models exist locally before enabling the private proxy.
& (Join-Path $PSScriptRoot 'Test-SharedOllama.ps1') -BaseUrl 'http://127.0.0.1:11434'

# Preserve other services and refuse to overwrite an unrelated handler on this port.
$serve = Invoke-TailscaleUtf8 -ExecutablePath $tailscale -Arguments @('serve','status','--json') -AsJson
$existingTcp = if ($serve.TCP) { $serve.TCP.PSObject.Properties | Where-Object { $_.Name -eq "$Port" } } else { $null }
$existingWeb = if ($serve.Web) { @($serve.Web.PSObject.Properties | Where-Object { $_.Name.EndsWith(":$Port") }) } else { @() }
if ($existingTcp -and $existingWeb.Count -eq 0 -and
        ($existingTcp.Value.TCPForward -replace '^tcp://','') -ne '127.0.0.1:11434') {
    throw "Port $Port already has a different Tailscale service."
}
foreach ($web in $existingWeb) {
    $handlers = @($web.Value.Handlers.PSObject.Properties)
    if ($handlers.Count -ne 1 -or $handlers[0].Name -ne '/' -or
            $handlers[0].Value.Proxy -ne 'http://127.0.0.1:11434') {
        throw "Port $Port already has a different Tailscale service. Choose another -Port."
    }
}

# Migrate only a verified Ollama HTTP handler created by an earlier script version.
if ($existingWeb.Count -gt 0) {
    Invoke-TailscaleUtf8 -ExecutablePath $tailscale -Arguments @('serve',"--http=$Port",'off')
}

# TCP forwarding accepts the host's Tailscale IP without changing Ollama's loopback binding.
# Serve is tailnet-only. Do not use Funnel or rebind Ollama to a public interface.
Invoke-TailscaleUtf8 -ExecutablePath $tailscale -Arguments @('serve','--bg',"--tcp=$Port",'tcp://127.0.0.1:11434') -TimeoutSeconds 60

$sharedUrl = "http://${ipv4}:$Port"
& (Join-Path $PSScriptRoot 'Test-SharedOllama.ps1') -BaseUrl $sharedUrl
Write-Host ''
Write-Host "Team Ollama URL: $sharedUrl" -ForegroundColor Green
Write-Host 'Teammates must connect through Tailscale with access to this PC.'
Write-Host 'Set RAG_OLLAMA_BASE_URL to the Team Ollama URL and RAG_QA_PROTOTYPE_ENABLED=true in each backend, then restart it.'
Write-Host 'This PC and Ollama must stay running while teammates use the service.'
