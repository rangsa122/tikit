[CmdletBinding(SupportsShouldProcess=$true)]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
. (Join-Path $PSScriptRoot 'Tailscale-Command.ps1')

# Match the actual Java main class and this checkout, not a possibly reused launcher PID.
$projectPrefix = $projectRoot + [IO.Path]::DirectorySeparatorChar
$hosts = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | Where-Object {
    $_.CommandLine -and
    $_.CommandLine.IndexOf($projectPrefix, [StringComparison]::OrdinalIgnoreCase) -ge 0 -and
    $_.CommandLine -match '(?<![\w.])com\.tikit\.rag\.host\.RagPublicHostMain(?![\w.])'
})

$tailscale = Join-Path $env:ProgramFiles 'Tailscale/tailscale.exe'
if (Test-Path -LiteralPath $tailscale) {
    $serve = Invoke-TailscaleUtf8 -ExecutablePath $tailscale -Arguments @('serve','status','--json') -AsJson
    $web443 = if ($serve.Web) { @($serve.Web.PSObject.Properties | Where-Object { $_.Name.EndsWith(':443') }) } else { @() }
    foreach ($web in $web443) {
        $handlers = @($web.Value.Handlers.PSObject.Properties)
        if ($handlers.Count -eq 1 -and $handlers[0].Name -eq '/' -and
                $handlers[0].Value.Proxy -eq 'http://127.0.0.1:18081') {
            if ($PSCmdlet.ShouldProcess($web.Name, 'Disable the public RAG HTTPS connection')) {
                Invoke-TailscaleUtf8 -ExecutablePath $tailscale -Arguments @('funnel','--https=443','off')
                Write-Host 'Public RAG HTTPS connection disabled.'
            }
        }
    }
}

foreach ($hostProcess in $hosts) {
    if ($PSCmdlet.ShouldProcess("RAG host PID $($hostProcess.ProcessId)", 'Stop this checkout RAG-only server')) {
        Stop-Process -Id $hostProcess.ProcessId -ErrorAction Stop
        Write-Host "RAG-only server stopped: PID $($hostProcess.ProcessId)"
    }
}
if ($hosts.Count -eq 0) { Write-Host 'No RAG-only Java server was found for this checkout.' }
if (-not $WhatIfPreference) { Write-Host 'Ollama and Tailscale remain installed and running. Use Start-PublicRagHost.ps1 to start sharing again.' }
