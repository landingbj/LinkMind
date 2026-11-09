[CmdletBinding()]
param(
    [string]$BoxHost = "192.168.3.41",
    [string]$SshUser = "digimeta",
    [string]$SshKey,
    [int]$Samples = 5,
    [int]$InputCharacters = 400,
    [int]$Port = 8891,
    [string]$OutputDirectory = ""
)

$ErrorActionPreference = "Stop"

if (-not $OutputDirectory) {
    $OutputDirectory = Join-Path $PSScriptRoot "..\outputs\llama-embedding-benchmark"
}

if ($Samples -lt 3) {
    throw "Samples must be at least 3."
}

$sshOptions = @("-o", "ConnectTimeout=10", "-o", "StrictHostKeyChecking=accept-new")
if ($SshKey) {
    $sshOptions += @("-i", $SshKey)
}

function Invoke-BoxSsh {
    param([Parameter(Mandatory)][string]$Command)

    $output = & ssh @sshOptions "$SshUser@$BoxHost" $Command 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Remote command failed: $Command`n$output"
    }
    return ($output | Out-String).Trim()
}

function Get-PercentValue {
    param([string]$Value)
    return [double]::Parse(
        $Value.Trim().TrimEnd("%"),
        [Globalization.CultureInfo]::InvariantCulture
    )
}

function Get-ContainerRssMiB {
    param([Parameter(Mandatory)][string]$Container)

    $line = Invoke-BoxSsh "pid=`$(docker inspect --format '{{.State.Pid}}' $Container); awk '/VmRSS:/ {print `$2}' /proc/`$pid/status"
    return [math]::Round(([double]$line.Trim()) / 1024, 1)
}

function Wait-EmbeddingServer {
    param([Parameter(Mandatory)][string]$Url)

    for ($attempt = 1; $attempt -le 30; $attempt++) {
        try {
            Invoke-RestMethod -Method Get -Uri "$Url/health" -TimeoutSec 3 | Out-Null
            return
        } catch {
            Start-Sleep -Milliseconds 500
        }
    }
    throw "Embedding server did not become ready: $Url"
}

function Invoke-Embedding {
    param(
        [Parameter(Mandatory)][string]$Url,
        [Parameter(Mandatory)][string]$Model,
        [Parameter(Mandatory)][string]$Text
    )

    $body = @{ model = $Model; input = $Text } | ConvertTo-Json -Compress
    $stopwatch = [Diagnostics.Stopwatch]::StartNew()
    $result = Invoke-RestMethod -Method Post -Uri "$Url/v1/embeddings" `
        -ContentType "application/json" -Body $body -TimeoutSec 180
    $stopwatch.Stop()
    if ($result.data[0].embedding.Count -ne 1024) {
        throw "Unexpected embedding dimension: $($result.data[0].embedding.Count)"
    }
    return $stopwatch.Elapsed.TotalSeconds
}

function Get-Percentile {
    param([double[]]$Values, [double]$Percentile)

    $sorted = @($Values | Sort-Object)
    $index = [math]::Ceiling($Percentile * $sorted.Count) - 1
    return $sorted[[math]::Max(0, [math]::Min($index, $sorted.Count - 1))]
}

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$csvPath = Join-Path $OutputDirectory "llama-embedding-benchmark-$timestamp.csv"
$reportPath = Join-Path $OutputDirectory "llama-embedding-benchmark-$timestamp.md"

$image = "ghcr.io/ggml-org/llama.cpp:server"
$container = "embedding-benchmark-$timestamp".ToLowerInvariant()
$serverUrl = "http://$BoxHost`:$Port"
# Keep the script ASCII-only so Windows PowerShell 5 can run it without a BOM.
# U+4EBA is a CJK character tokenized by both target models.
$sourceText = [string]::new([char]0x4EBA, $InputCharacters)

$models = @(
    [pscustomobject]@{
        Name = "bge-m3-q8_0"
        Alias = "bge-m3"
        ModelPath = "/var/lib/bge-m3/bge-m3-q8_0.gguf"
    },
    [pscustomobject]@{
        Name = "bge-large-zh-v1.5-q8_0"
        Alias = "bge-large-zh-v1.5"
        ModelPath = "/var/lib/bge-large-zh-v1.5/bge-large-zh-v1.5-q8_0.gguf"
    }
)

$profiles = @(
    [pscustomobject]@{ Name = "t2-passive-poll0"; Threads = 2; Passive = $true; Poll = 0 },
    [pscustomobject]@{ Name = "t3-default-poll50"; Threads = 3; Passive = $false; Poll = $null },
    [pscustomobject]@{ Name = "t3-passive-poll0"; Threads = 3; Passive = $true; Poll = 0 },
    [pscustomobject]@{ Name = "t4-passive-poll0"; Threads = 4; Passive = $true; Poll = 0 }
)

$results = [System.Collections.Generic.List[object]]::new()
try {
    foreach ($model in $models) {
        $modelFileBytes = [int64](Invoke-BoxSsh "stat -c %s $($model.ModelPath)")
        foreach ($profile in $profiles) {
            Write-Host "Starting $($model.Name) / $($profile.Name)"
            Invoke-BoxSsh "docker rm -f $container >/dev/null 2>&1 || true" | Out-Null
            # These are Linux paths consumed by the remote Docker daemon.
            # Do not use Windows Split-Path because it changes '/' to '\\'.
            $lastSlash = $model.ModelPath.LastIndexOf("/")
            $modelDirectory = $model.ModelPath.Substring(0, $lastSlash)
            $modelFile = $model.ModelPath.Substring($lastSlash + 1)
            $passive = if ($profile.Passive) { "-e OMP_WAIT_POLICY=PASSIVE" } else { "" }
            $poll = if ($null -ne $profile.Poll) { "--poll $($profile.Poll)" } else { "" }
            $command = "docker run -d --rm --name $container -p $($Port):8080 $passive -v $($modelDirectory):/models:ro $image --model /models/$modelFile --alias $($model.Alias) --host 0.0.0.0 --port 8080 --embedding --no-ui --metrics --device none --embd-normalize 2 --threads $($profile.Threads) --threads-batch $($profile.Threads) $poll --parallel 1 --ctx-size 512 --batch-size 512 --ubatch-size 512 --cache-ram 0"
            Write-Host $command
            Invoke-BoxSsh $command | Out-Null
            Wait-EmbeddingServer -Url $serverUrl

            # Warm model pages and JIT-independent request paths without counting it.
            Invoke-Embedding -Url $serverUrl -Model $model.Alias -Text $sourceText | Out-Null
            $durations = [System.Collections.Generic.List[double]]::new()
            $peakCpu = 0.0
            for ($run = 1; $run -le $Samples; $run++) {
                $request = Start-Job -ScriptBlock {
                    param($url, $modelName, $textPayload)
                    $body = @{ model = $modelName; input = $textPayload } | ConvertTo-Json -Compress
                    $watch = [Diagnostics.Stopwatch]::StartNew()
                    $response = Invoke-RestMethod -Method Post -Uri "$url/v1/embeddings" -ContentType "application/json" -Body $body -TimeoutSec 180
                    $watch.Stop()
                    if ($response.data[0].embedding.Count -ne 1024) { throw "unexpected dimensions" }
                    $watch.Elapsed.TotalSeconds
                } -ArgumentList $serverUrl, $model.Alias, $sourceText
                while ($request.State -in @("NotStarted", "Running")) {
                    $stats = Invoke-BoxSsh "docker stats --no-stream --format '{{.CPUPerc}}' $container"
                    $peakCpu = [math]::Max($peakCpu, (Get-PercentValue $stats))
                    Start-Sleep -Milliseconds 180
                }
                Wait-Job $request | Out-Null
                $durations.Add([double](Receive-Job $request))
                Remove-Job $request
            }
            $rssMiB = Get-ContainerRssMiB -Container $container
            $mean = ($durations | Measure-Object -Average).Average
            $result = [pscustomobject]@{
                Model = $model.Name
                Profile = $profile.Name
                Threads = $profile.Threads
                Poll = if ($null -eq $profile.Poll) { "default(50)" } else { $profile.Poll }
                OmpWaitPolicy = if ($profile.Passive) { "PASSIVE" } else { "default" }
                ContextTokens = 512
                InputCharacters = $InputCharacters
                Samples = $Samples
                MeanSeconds = [math]::Round($mean, 3)
                P95Seconds = [math]::Round((Get-Percentile -Values $durations.ToArray() -Percentile 0.95), 3)
                MinSeconds = [math]::Round(($durations | Measure-Object -Minimum).Minimum, 3)
                MaxSeconds = [math]::Round(($durations | Measure-Object -Maximum).Maximum, 3)
                PeakCpuPercent = [math]::Round($peakCpu, 1)
                ProcessRssMiB = $rssMiB
                ModelDiskMiB = [math]::Round($modelFileBytes / 1MB, 1)
            }
            $results.Add($result)
            Write-Host ("{0,-25} {1,-20} mean={2:N3}s peak-cpu={3:N1}% rss={4:N1}MiB" -f $result.Model, $result.Profile, $result.MeanSeconds, $result.PeakCpuPercent, $result.ProcessRssMiB)
        }
    }
} finally {
    try { Invoke-BoxSsh "docker rm -f $container >/dev/null 2>&1 || true" | Out-Null } catch { Write-Warning $_ }
}

$results | Export-Csv -NoTypeInformation -Encoding utf8 -Path $csvPath
$fastest = $results | Sort-Object MeanSeconds | Select-Object -First 1
$lowestResource = $results | Sort-Object ProcessRssMiB, PeakCpuPercent, MeanSeconds | Select-Object -First 1

$table = $results | Sort-Object Model, MeanSeconds | ConvertTo-Csv -NoTypeInformation | ConvertFrom-Csv |
    Format-Table Model, Profile, MeanSeconds, P95Seconds, PeakCpuPercent, ProcessRssMiB, ModelDiskMiB -AutoSize | Out-String
$report = @(
    "# llama.cpp Embedding Benchmark"
    ""
    "- Timestamp: $(Get-Date -Format s)"
    "- Box: $BoxHost"
    "- Input: $InputCharacters Chinese characters"
    "- Context: 512 tokens; one request at a time; $Samples measured runs after one warm-up"
    "- Peak CPU: sampled from docker stats while each measured request was in flight."
    "- RSS: process RSS from docker top; this is used because cgroup memory figures can undercount mmap model pages."
    ""
    "## Results"
    ""
    '```'
    $table.TrimEnd()
    '```'
    ""
    "## Recommendations"
    ""
    "- Lowest mean latency: **$($fastest.Model)** / **$($fastest.Profile)** at **$($fastest.MeanSeconds)s**."
    "- Lowest measured resource profile: **$($lowestResource.Model)** / **$($lowestResource.Profile)** at **$($lowestResource.PeakCpuPercent)%** sampled peak CPU and **$($lowestResource.ProcessRssMiB) MiB** RSS."
    "- Keep --parallel 1: this benchmark measures one request at a time; raising server slots would trade queueing for CPU contention on the four-core N95."
) -join [Environment]::NewLine
Set-Content -Path $reportPath -Value $report -Encoding utf8

Write-Host "CSV: $csvPath"
Write-Host "Report: $reportPath"
