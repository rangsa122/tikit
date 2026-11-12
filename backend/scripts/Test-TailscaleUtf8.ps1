$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Tailscale-Command.ps1')

$shellExe = Join-Path $PSHOME 'powershell.exe'
if (-not (Test-Path -LiteralPath $shellExe)) { $shellExe = (Get-Process -Id $PID).Path }
$expectedName = ([string][char]0xD55C) + [char]0xAE00 + ' user ' + [char]::ConvertFromUtf32(0x1F600)
$fixtureJson = @{ User = @{ DisplayName = $expectedName }; BackendState = 'Running' } | ConvertTo-Json -Compress
$childSource = '[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false); [Console]::Write(''' + $fixtureJson + ''')'
$encodedCommand = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($childSource))
$previousConsoleEncoding = [Console]::OutputEncoding
$previousPipeEncoding = $OutputEncoding
try {
    foreach ($codePage in @(949,65001)) {
        [Console]::OutputEncoding = [Text.Encoding]::GetEncoding($codePage)
        $OutputEncoding = [Text.Encoding]::GetEncoding($codePage)
        $result = Invoke-TailscaleUtf8 -ExecutablePath $shellExe -Arguments @('-NoProfile','-EncodedCommand',$encodedCommand) -AsJson
        if ($result.User.DisplayName -cne $expectedName -or $result.BackendState -ne 'Running') {
            throw 'UTF-8 process output was corrupted.'
        }
        Write-Host "PASS: Korean and emoji JSON under console code page $codePage"
    }
    $invalidSource = '[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false); [Console]::Write(''RAW_JSON_SENTINEL'')'
    $invalidCommand = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($invalidSource))
    $rejected = $false
    try {
        Invoke-TailscaleUtf8 -ExecutablePath $shellExe -Arguments @('-NoProfile','-EncodedCommand',$invalidCommand) -AsJson | Out-Null
    } catch {
        $rejected = $true
        if ($_.Exception.Message.Contains('RAW_JSON_SENTINEL')) { throw 'Raw JSON leaked into the error message.' }
    }
    if (-not $rejected) { throw 'Invalid JSON was accepted.' }
    Write-Host 'PASS: invalid JSON is rejected without dumping raw account details'
} finally {
    [Console]::OutputEncoding = $previousConsoleEncoding
    $OutputEncoding = $previousPipeEncoding
}
