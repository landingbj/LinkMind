function Install-LinkMind {
    $ErrorActionPreference = "Stop"

    # Check for JDK/JRE 8 runtime availability
    # Use cmd.exe for version probing to avoid PowerShell command-resolution differences.
    $javaVersionLine = $null
    try {
        $javaVersionRaw = & cmd.exe /d /c "java -version 2>&1"
        if ($LASTEXITCODE -eq 0 -and $javaVersionRaw) {
            $javaVersionLine = ($javaVersionRaw | Select-Object -First 1 | ForEach-Object { $_.ToString().Trim() })
        }
    } catch {}

    if (-not $javaVersionLine) {
        Write-Host "Error: JDK 8 is required but java runtime was not found."
        Write-Host "Please install JDK 8 and make sure 'java -version' works in this terminal."
        return
    }

    if ($javaVersionLine -notmatch '"1\.8(\.|_)?') {
        Write-Host "Error: JDK 8 is required, but current java version is: $javaVersionLine"
        Write-Host "Please switch JAVA_HOME/PATH to JDK 8 and retry."
        return
    }

    $linkMindDir = Join-Path $HOME "LinkMind"
    $jarName = "LinkMind.jar"
    $downloadsHost = "ai.linkmind.top"
    $downloadUrl = "https://$downloadsHost/installer/LinkMind.jar"
    $popularSkillsUrl = "https://$downloadsHost/installer/popular_skills.zip"
    # $downloadUrl = "http://localhost:8000/LinkMind.jar"
    # $popularSkillsUrl = "http://localhost:8000/popular_skills.zip"
    $jarPath = Join-Path $linkMindDir $jarName

    # 1. Ensure LinkMind directory exists
    if (-not (Test-Path $linkMindDir)) {
        New-Item -ItemType Directory -Path $linkMindDir | Out-Null
        Write-Host "Created directory: $linkMindDir"
    } else {
        Write-Host "Directory already exists: $linkMindDir"
    }

    # 2-3. Download jar to a temp file with progress and retry, then move to target
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

    $tempFile = Join-Path ([System.IO.Path]::GetTempPath()) "LinkMind_$([System.Guid]::NewGuid().ToString('N')).jar"
    $maxRetries = 3

    for ($attempt = 1; $attempt -le $maxRetries; $attempt++) {
        try {
            if ($attempt -eq 1) {
                Write-Host "Downloading $downloadUrl ..."
            } else {
                Write-Host "Retry $attempt/$maxRetries ..."
            }

            $webRequest = [System.Net.HttpWebRequest]::Create($downloadUrl)
            $webRequest.Timeout = 60000
            $webRequest.ReadWriteTimeout = 600000
            $webRequest.KeepAlive = $true

            $totalRead = 0
            if ((Test-Path $tempFile) -and $attempt -gt 1) {
                $totalRead = (Get-Item $tempFile).Length
                $webRequest.AddRange([int]$totalRead)
                Write-Host "  Resuming from $([math]::Round($totalRead / 1MB, 2)) MB ..."
            }

            $response = $webRequest.GetResponse()
            $totalBytes = $response.ContentLength + $totalRead
            $responseStream = $response.GetResponseStream()

            if ($totalRead -gt 0) {
                $fileStream = [System.IO.File]::Open($tempFile, [System.IO.FileMode]::Append)
            } else {
                $fileStream = [System.IO.File]::Create($tempFile)
            }

            $buffer = New-Object byte[] 65536
            $bytesRead = 0
            $lastPercent = -1
            $sw = [System.Diagnostics.Stopwatch]::StartNew()

            while (($bytesRead = $responseStream.Read($buffer, 0, $buffer.Length)) -gt 0) {
                $fileStream.Write($buffer, 0, $bytesRead)
                $totalRead += $bytesRead

                if ($totalBytes -gt 0) {
                    $percent = [math]::Floor($totalRead * 100 / $totalBytes)
                    if ($percent -ne $lastPercent) {
                        $lastPercent = $percent
                        $elapsedSec = $sw.Elapsed.TotalSeconds
                        $speed = if ($elapsedSec -gt 0) { $totalRead / $elapsedSec } else { 0 }
                        $speedMB = [math]::Round($speed / 1MB, 2)
                        $downloadedMB = [math]::Round($totalRead / 1MB, 2)
                        $totalMB = [math]::Round($totalBytes / 1MB, 2)
                        Write-Host -NoNewline ("`r  Progress: $percent%  ($downloadedMB / $totalMB MB)  Speed: $speedMB MB/s   ")
                    }
                } else {
                    $downloadedMB = [math]::Round($totalRead / 1MB, 2)
                    Write-Host -NoNewline ("`r  Downloaded: $downloadedMB MB   ")
                }
            }

            Write-Host ""
            $fileStream.Close()
            $responseStream.Close()
            $response.Close()

            break
        } catch {
            Write-Host ""
            Write-Host "Warning: Download interrupted - $($_.Exception.Message)"
            if ($fileStream) { try { $fileStream.Close() } catch {} }
            if ($responseStream) { try { $responseStream.Close() } catch {} }
            if ($response) { try { $response.Close() } catch {} }

            if ($attempt -ge $maxRetries) {
                Write-Host "Error: Failed to download after $maxRetries attempts."
                if (Test-Path $tempFile) {
                    Remove-Item $tempFile -Force -ErrorAction SilentlyContinue
                }
                return
            }
            $waitSec = $attempt * 3
            Write-Host "  Waiting $waitSec seconds before retrying..."
            Start-Sleep -Seconds $waitSec
        }
    }

    Copy-Item -Path $tempFile -Destination $jarPath -Force
    if (Test-Path $tempFile) {
        Remove-Item $tempFile -Force -ErrorAction SilentlyContinue
    }
    Write-Host "Download complete: $jarPath"

    # 4-5. Ask user questions and run InstallerUtil
    function Read-YesNo($prompt) {
        $answer = Read-Host "$prompt (yes/no) [no]"
        $answer = $answer.Trim().ToLower()
        return ($answer -eq "yes" -or $answer -eq "y")
    }

    function Read-RuntimeChoice {
        while ($true) {
            Write-Host "Runtime Choice:"
            Write-Host "  1) as Agent Mate"
            Write-Host "  2) as Agent Server"
            $answer = (Read-Host "Please choose [1]").Trim().ToLower()
            if ([string]::IsNullOrEmpty($answer) -or $answer -eq "1" -or $answer -eq "mate") {
                return "mate"
            }
            if ($answer -eq "2" -or $answer -eq "server") {
                return "server"
            }
            Write-Host "Invalid choice. Please enter 1 or 2."
        }
    }

    $runtimeChoice = Read-RuntimeChoice
    $skillsRoot = ""
    $injectAgent = 0
    $deerFlowPath = ""

    function Read-InjectAgentChoice {
        while ($true) {
            Write-Host "Inject Agent Framework:"
            Write-Host "  1) openclaw"
            Write-Host "  2) deer-flow"
            Write-Host "  3) hermes"
            $answer = (Read-Host "Please choose [1]").Trim().ToLower()
            if ([string]::IsNullOrEmpty($answer) -or $answer -eq "1" -or $answer -eq "openclaw") {
                return 1
            }
            if ($answer -eq "2" -or $answer -eq "deer-flow" -or $answer -eq "deerflow") {
                return (1 -shl 1)
            }
            if ($answer -eq "3" -or $answer -eq "hermes") {
                return (1 -shl 2)
            }
            Write-Host "Invalid choice. Please enter 1, 2 or 3."
        }
    }

    function Read-DeerFlowPath {
        while ($true) {
            $answer = (Read-Host "Please enter deer-flow install directory").Trim()
            if ([string]::IsNullOrEmpty($answer)) {
                Write-Host "deer-flow install directory is required."
                continue
            }
            if (-not (Test-Path $answer -PathType Container)) {
                Write-Host "Directory does not exist: $answer"
                continue
            }
            return $answer
        }
    }

    if ($runtimeChoice -eq "mate") {
        $injectAgent = Read-InjectAgentChoice
        if ($injectAgent -eq (1 -shl 1)) {
            $deerFlowPath = Read-DeerFlowPath
        }
    }

    if ($runtimeChoice -eq "server") {
        $popularSkillsZip = Join-Path $linkMindDir "popular_skills.zip"
        $skillsRoot = Join-Path (Join-Path $linkMindDir "skills") "popular_skills"
        Write-Host "Downloading $popularSkillsUrl ..."
        Invoke-WebRequest -Uri $popularSkillsUrl -OutFile $popularSkillsZip -UseBasicParsing
        if (Test-Path $skillsRoot) {
            Remove-Item -Path $skillsRoot -Recurse -Force
        }
        New-Item -ItemType Directory -Path $skillsRoot | Out-Null
        Expand-Archive -Path $popularSkillsZip -DestinationPath $skillsRoot -Force
    }

    # $exportToOpenClaw = Read-YesNo "Would you like to inject LinkMind into OpenClaw?"
    # $importFromOpenClaw = Read-YesNo "Would you like to import OpenClaw configurations into LinkMind?"
    #
    # $exportVal = if ($exportToOpenClaw) { "true" } else { "false" }
    # $importVal = if ($importFromOpenClaw) { "true" } else { "false" }

    Write-Host "Running installer..."
    # "--export-to-openclaw=$exportVal"
    # "--import-from-openclaw=$importVal"
    java -cp $jarPath ai.starter.InstallerUtil "--runtime-choice=$runtimeChoice" "--skills-root=$skillsRoot" "--inject-agent=$injectAgent" "--deer-flow-path=$deerFlowPath"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Error: Installer exited with code $LASTEXITCODE"
        return
    }

    # 6. Success message
    Write-Host ""
    Write-Host "LinkMind installed successfully!"
    Write-Host ""

    # 7. Optionally start LinkMind
    $startNow = Read-YesNo "Would you like to start LinkMind now?"
    if ($startNow) {
        Set-Location $linkMindDir
        java -jar $jarName "--enable-sync=false"
    } else {
        Write-Host "You can start LinkMind later by running:"
        Write-Host "  cd $linkMindDir && java -jar $jarName --enable-sync=false"
    }
}

Install-LinkMind
