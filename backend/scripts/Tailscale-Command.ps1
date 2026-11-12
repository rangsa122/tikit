function Invoke-TailscaleUtf8 {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory=$true)][string]$ExecutablePath,
        [Parameter(Mandatory=$true)][string[]]$Arguments,
        [switch]$AsJson,
        [ValidateRange(1,300)][int]$TimeoutSeconds = 30
    )

    # Windows PowerShell 5.1 can decode native UTF-8 output as CP949.
    # Read the redirected process streams directly without changing the user's console encoding.
    foreach ($argument in $Arguments) {
        if ($argument -match '["\r\n]' -or $argument.EndsWith('\')) {
            throw 'Unsupported command argument.'
        }
    }
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $ExecutablePath
    $startInfo.Arguments = ($Arguments | ForEach-Object { '"' + $_ + '"' }) -join ' '
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.StandardOutputEncoding = [Text.UTF8Encoding]::new($false)
    $startInfo.StandardErrorEncoding = [Text.UTF8Encoding]::new($false)
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    try {
        if (-not $process.Start()) { throw 'Unable to start Tailscale.' }
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        $completed = $process.WaitForExit($TimeoutSeconds * 1000)
        if (-not $completed) {
            $process.Kill()
            $process.WaitForExit()
        }
        $stdout = $stdoutTask.GetAwaiter().GetResult()
        $stderr = $stderrTask.GetAwaiter().GetResult()
        if (-not $completed) {
            if (-not $AsJson -and $stdout) { Write-Host $stdout.TrimEnd() }
            throw 'Tailscale timed out. Complete any account setup shown above, then retry.'
        }
        if ($process.ExitCode -ne 0) {
            if ($AsJson) { throw 'Cannot read Tailscale status. Check that Tailscale is running and accessible to this Windows user.' }
            if ($stderr) { Write-Host $stderr.TrimEnd() }
            throw 'Tailscale command failed.'
        }
        if ($AsJson) {
            try { return ($stdout | ConvertFrom-Json -ErrorAction Stop) }
            catch { throw 'Tailscale returned invalid UTF-8 JSON. Raw account details were omitted.' }
        }
        if ($stdout) { Write-Host $stdout.TrimEnd() }
        if ($stderr) { Write-Host $stderr.TrimEnd() }
    } finally {
        $process.Dispose()
    }
}
