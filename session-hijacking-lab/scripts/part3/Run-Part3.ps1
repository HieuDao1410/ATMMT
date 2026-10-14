[CmdletBinding()]
param([string]$AnalyzeRunId)

$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$toolsImage = 'session-lab-packet-tools:local'
$javaSource = Join-Path $PSScriptRoot 'Part3Lab.java'
$certificatePath = Join-Path $projectDirectory '.local/tls/localhost.cer'
if (-not (Test-Path -LiteralPath $certificatePath)) { throw 'Create the local HTTPS certificate first; see README.' }
if (($env:LAB_PORT -and $env:LAB_PORT -ne '8080') -or ($env:LAB_HTTPS_PORT -and $env:LAB_HTTPS_PORT -ne '8443')) {
    throw 'This lab script expects the documented host ports 8080 and 8443. Use a terminal without alternate LAB_PORT settings.'
}
Get-Command java,docker -ErrorAction Stop | Out-Null
if ($AnalyzeRunId -and $AnalyzeRunId -notmatch '^run-[a-f0-9]{12}$') { throw 'Invalid local run identifier.' }
$runId = if ($AnalyzeRunId) { $AnalyzeRunId } else { 'run-' + [Guid]::NewGuid().ToString('N').Substring(0, 12) }
$privateDirectory = Join-Path $projectDirectory ".local/part3/$runId"
$publishedEvidenceDirectory = Join-Path $projectDirectory "docs/minh-chung/phan3/$runId"
$evidenceDirectory = Join-Path $privateDirectory 'public'
if ($AnalyzeRunId) {
    if (-not (Test-Path -LiteralPath (Join-Path $evidenceDirectory 'events.tsv'))) { throw 'No client events for the requested run.' }
    if (Test-Path -LiteralPath $publishedEvidenceDirectory) { throw 'This run is already published; refusing to overwrite it.' }
} else {
    New-Item -ItemType Directory -Path $privateDirectory,$evidenceDirectory | Out-Null
}
$utf8 = [System.Text.UTF8Encoding]::new($false)
$activeCaptures = [System.Collections.Generic.List[string]]::new()

function Invoke-Docker {
    param([string[]]$Arguments)
    & docker @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Docker command failed: $($Arguments[0]). Check the local run artifacts." }
}

function Start-LabCapture {
    param([string]$Name, [string]$Container, [string]$File, [int]$Port)
    # Capture only the app's network namespace; never use host networking or privileged mode.
    # Debian tcpdump initializes file ownership and uid/gid even with -Z root.
    Invoke-Docker -Arguments @('run','-d','--name',$Name,'--cap-drop','ALL','--cap-add','NET_RAW',
        '--cap-add','SETUID','--cap-add','SETGID','--cap-add','CHOWN',
        '--security-opt','no-new-privileges','--network',"container:$Container",'--mount',
        "type=bind,source=$privateDirectory,target=/captures",$toolsImage,
        'tcpdump','-Z','root','-p','-i','eth0','-U','-s','0','-w',"/captures/$File",'tcp','port',"$Port") | Out-Null
    $activeCaptures.Add($Name)
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        $running = (Invoke-Docker -Arguments @('inspect','--format','{{.State.Running}}',$Name) | Out-String).Trim()
        $captureStatus = (& docker logs $Name 2>&1 | Out-String)
        if ($running -eq 'true' -and $captureStatus -match 'listening\s+on\s+eth0') { return }
        if ($running -ne 'true') {
            [IO.File]::WriteAllText((Join-Path $privateDirectory "$Name-startup.log"), $captureStatus, $utf8)
            throw 'Capture process exited before readiness; see the private startup log.'
        }
        Start-Sleep -Milliseconds 200
    }
    $captureStatus = (& docker logs $Name 2>&1 | Out-String)
    [IO.File]::WriteAllText((Join-Path $privateDirectory "$Name-startup.log"), $captureStatus, $utf8)
    throw 'Capture did not become ready; diagnostics kept private and no lab traffic generated for this phase.'
}

function Stop-LabCapture {
    param([string]$Name, [string]$LogName)
    Invoke-Docker -Arguments @('stop','--time','5',$Name) | Out-Null
    $captureLog = & docker logs $Name 2>&1 | Out-String
    [IO.File]::WriteAllText((Join-Path $privateDirectory $LogName), $captureLog, $utf8)
    Invoke-Docker -Arguments @('rm',$Name) | Out-Null
    $activeCaptures.Remove($Name) | Out-Null
}

function Read-Packets {
    param([string]$File,[int]$Port,[string]$Protocol)
    $rawJson = Invoke-Docker -Arguments @('run','--rm','--network','none','--cap-drop','ALL',
        '--security-opt','no-new-privileges','--mount',"type=bind,source=$privateDirectory,target=/captures,readonly",
        $toolsImage,'tshark','-n','-r',"/captures/$File",'-d',"tcp.port==$Port,$Protocol",
        '-o','tls.keylog_file:','-T','json','-e','frame.number','-e','tcp.stream','-e','http.request.method',
        '-e','http.request.uri','-e','http.cookie','-e','http.response.code','-e','tls.record.content_type','-e','tls.record.opaque_type')
    $jsonText = $rawJson -join "`n"
    [IO.File]::WriteAllText((Join-Path $privateDirectory "$File.json"), $jsonText, $utf8)
    try { return @($jsonText | ConvertFrom-Json) } catch { throw "Could not parse packet metadata for $File. Raw JSON is kept private." }
}

function Add-LabEvent {
    param([string]$Id,[string]$Observation,[string]$Evidence)
    $timestamp = [DateTimeOffset]::Now.ToString('o')
    $row = "$Id`tPASS`t$timestamp`t$Observation`t$Evidence`n"
    [IO.File]::AppendAllText((Join-Path $evidenceDirectory 'events.tsv'), $row, $utf8)
    Write-Output "PASS ${Id}: $Observation"
}

function Invoke-LabClient {
    param([string]$Mode)
    $clientArguments = @($javaSource,$Mode,$privateDirectory,$evidenceDirectory)
    if ($Mode -eq 'https') { $clientArguments += $certificatePath }
    & java @clientArguments 2>&1 | Tee-Object -FilePath (Join-Path $evidenceDirectory 'client-log.txt') -Append
    if ($LASTEXITCODE -ne 0) { throw "Lab client phase failed: $Mode" }
}

try {
    if (-not $AnalyzeRunId) {
    Push-Location $projectDirectory
    try {
        Invoke-Docker -Arguments @('compose','up','-d') | Out-Null
        Invoke-Docker -Arguments @('compose','-f','compose.https.yaml','up','-d') | Out-Null
        $httpContainer = (Invoke-Docker -Arguments @('compose','ps','-q','app') | Out-String).Trim()
        $httpsContainer = (Invoke-Docker -Arguments @('compose','-f','compose.https.yaml','ps','-q','app') | Out-String).Trim()
    } finally { Pop-Location }
    if (-not $httpContainer -or -not $httpsContainer) { throw 'Both application containers must be running.' }
    $toolVersion = (Invoke-Docker -Arguments @('run','--rm','--network','none',$toolsImage,'tshark','--version') | Select-Object -First 1)
    $sourceCommit = (& git -C $projectDirectory rev-parse HEAD | Out-String).Trim()
    $metadata = [ordered]@{
        run_id=$runId; started_at=[DateTimeOffset]::Now.ToString('o'); source_commit=$sourceCommit
        capture_interface='eth0 in each application container network namespace'
        http_url='http://127.0.0.1:8080'; https_url='https://127.0.0.1:8443'
        capture_filters=@('tcp port 8080','tcp port 8443'); tls_decryption_keys_provided=$false
        tshark_version=$toolVersion; raw_artifacts_location=".local/part3/$runId"
    }
    [IO.File]::WriteAllText((Join-Path $evidenceDirectory 'run.json'), ($metadata | ConvertTo-Json), $utf8)
    Write-Output "Lab run: $runId"

    $seedCapture = "sessionlab-seed-$runId"
    Start-LabCapture -Name $seedCapture -Container $httpContainer -File 'http-seed.pcap' -Port 8080
    Invoke-LabClient -Mode 'seed-http'
    Stop-LabCapture -Name $seedCapture -LogName 'capture-http-seed.log'

    $seedPackets = @(Read-Packets -File 'http-seed.pcap' -Port 8080 -Protocol 'http')
    $markerLine = Get-Content -LiteralPath (Join-Path $privateDirectory 'http-state.properties') | Where-Object { $_.StartsWith('marker=') }
    $marker = $markerLine.Substring('marker='.Length)
    $wirePacket = $seedPackets | Where-Object {
        $packetLayers = $_._source.layers
        (@($packetLayers.'http.request.uri')[0] -like "*lab_run=$marker*") -and $packetLayers.'http.cookie'
    } | Select-Object -First 1
    if (-not $wirePacket) { throw 'No marker-matched HTTP cookie found in the actual capture; replay is not attempted.' }
    $wireHeader = @($wirePacket._source.layers.'http.cookie')[0]
    $wireMatch = [regex]::Match($wireHeader, '(?:^|;\s*)JSESSIONID=([^;\s]+)')
    if (-not $wireMatch.Success) { throw 'Captured request has no JSESSIONID cookie.' }
    [IO.File]::WriteAllText((Join-Path $privateDirectory 'captured-http-cookie.txt'), $wireMatch.Groups[1].Value, $utf8)
    $sourceFrame = @($wirePacket._source.layers.'frame.number')[0]
    Add-LabEvent -Id 'T03' -Observation "HTTP cookie decoded from actual seed capture, frame $sourceFrame; value redacted" -Evidence 'http-seed.pcap'

    $replayCapture = "sessionlab-replay-$runId"
    Start-LabCapture -Name $replayCapture -Container $httpContainer -File 'http-replay.pcap' -Port 8080
    Invoke-LabClient -Mode 'replay-http'
    Stop-LabCapture -Name $replayCapture -LogName 'capture-http-replay.log'

    $tlsCapture = "sessionlab-tls-$runId"
    Start-LabCapture -Name $tlsCapture -Container $httpsContainer -File 'https.pcap' -Port 8443
    Invoke-LabClient -Mode 'https'
    Stop-LabCapture -Name $tlsCapture -LogName 'capture-https.log'
    }

    Invoke-Docker -Arguments @('run','--rm','--network','none','--mount',"type=bind,source=$privateDirectory,target=/captures",$toolsImage,
        'mergecap','-F','pcap','-w','/captures/http.pcap','/captures/http-seed.pcap','/captures/http-replay.pcap') | Out-Null
    $httpPackets = @(Read-Packets -File 'http.pcap' -Port 8080 -Protocol 'http')
    $tlsPackets = @(Read-Packets -File 'https.pcap' -Port 8443 -Protocol 'tls')
    $tlsRecords = @($tlsPackets | Where-Object { $_._source.layers.'tls.record.content_type' -or $_._source.layers.'tls.record.opaque_type' })
    # TLS 1.3 uses an outer opaque type 23; encrypted handshake records can share this type.
    $tlsApplicationRecords = @($tlsPackets | Where-Object {
        (@($_._source.layers.'tls.record.content_type') -contains '23') -or
        (@($_._source.layers.'tls.record.opaque_type') -contains '23')
    })
    $decodedTlsCookies = @($tlsPackets | Where-Object { $_._source.layers.'http.cookie' })
    $tlsStateLine = Get-Content -LiteralPath (Join-Path $privateDirectory 'https-state.properties') | Where-Object { $_.StartsWith('session=') }
    $tlsSession = $tlsStateLine.Substring('session='.Length)
    $rawTlsAscii = [Text.Encoding]::ASCII.GetString([IO.File]::ReadAllBytes((Join-Path $privateDirectory 'https.pcap')))
    if ($tlsRecords.Count -eq 0 -or $tlsApplicationRecords.Count -eq 0 -or $decodedTlsCookies.Count -ne 0 -or $rawTlsAscii.Contains($tlsSession)) {
        throw 'TLS capture did not satisfy the positive-traffic / no-plaintext-cookie checks.'
    }
    Add-LabEvent -Id 'T04' -Observation "TLS frames=$($tlsRecords.Count); frames containing outer type 23=$($tlsApplicationRecords.Count); decoded cookie headers=0; known cookie absent from raw bytes" -Evidence 'https.pcap; no decryption keys; outer type does not identify the encrypted inner content'

    $packetRows = [System.Collections.Generic.List[string]]::new()
    $packetRows.Add("capture`tframe`ttcp_stream`tmethod`turi`tcookie`thttp_status`ttls_content_type`ttls_opaque_type")
    foreach ($captureInfo in @(@{name='http.pcap';packets=$httpPackets},@{name='https.pcap';packets=$tlsPackets})) {
        foreach ($packet in $captureInfo.packets) {
            $layers = $packet._source.layers
            if (-not $layers.'http.request.method' -and -not $layers.'http.response.code' -and -not $layers.'tls.record.content_type' -and -not $layers.'tls.record.opaque_type') { continue }
            $safeCookie = if ($layers.'http.cookie') { 'JSESSIONID=<redacted>' } else { '' }
            $packetRows.Add((@($captureInfo.name, @($layers.'frame.number')[0], @($layers.'tcp.stream')[0],
                @($layers.'http.request.method')[0], @($layers.'http.request.uri')[0], $safeCookie,
                @($layers.'http.response.code')[0], (@($layers.'tls.record.content_type') -join ','),
                (@($layers.'tls.record.opaque_type') -join ',')) -join "`t"))
        }
    }
    [IO.File]::WriteAllLines((Join-Path $evidenceDirectory 'packets-redacted.tsv'), $packetRows, $utf8)
    $captureManifest = @('http.pcap','http-seed.pcap','http-replay.pcap','https.pcap') | ForEach-Object {
        $capturePath = Join-Path $privateDirectory $_
        [ordered]@{file=$_;bytes=(Get-Item -LiteralPath $capturePath).Length;sha256=(Get-FileHash -LiteralPath $capturePath -Algorithm SHA256).Hash.ToLowerInvariant()}
    }
    [IO.File]::WriteAllText((Join-Path $evidenceDirectory 'capture-manifest.json'), ($captureManifest | ConvertTo-Json), $utf8)
    $captureStatistics = [System.Collections.Generic.List[string]]::new()
    foreach ($logFile in Get-ChildItem -LiteralPath $privateDirectory -Filter 'capture-*.log') {
        $statisticsText = Get-Content -LiteralPath $logFile.FullName -Raw
        if ($statisticsText -notmatch '0\s+packets\s+dropped\s+by\s+kernel') {
            throw 'Capture drop count was not confirmed as zero; inspect private capture logs before marking the run complete.'
        }
        $captureStatistics.Add($logFile.Name)
        foreach ($statisticsLine in ($statisticsText -split "`r?`n")) {
            if ($statisticsLine -match '\d+\s+packets\s+(captured|received\s+by\s+filter|dropped\s+by\s+kernel)') {
                $captureStatistics.Add($statisticsLine.Trim())
            }
        }
    }
    [IO.File]::WriteAllLines((Join-Path $evidenceDirectory 'capture-statistics.txt'), $captureStatistics, $utf8)
    $requiredCases = @('T01','T02','T03','T04','T05','T06','T07','T08','T09-HTTP','T09-HTTPS')
    $caseLines = Get-Content -LiteralPath (Join-Path $evidenceDirectory 'events.tsv')
    foreach ($requiredCase in $requiredCases) {
        if (-not ($caseLines | Where-Object { $_.StartsWith("$requiredCase`tPASS`t") })) { throw "Missing successful case: $requiredCase" }
    }
    [IO.File]::WriteAllText((Join-Path $evidenceDirectory 'COMPLETE.txt'), "All T01-T09 cases passed; both transports rotated session IDs. Raw pcaps remain private.`n", $utf8)
    # Publish only a fully successful run. Partial results stay under ignored .local/.
    if (Test-Path -LiteralPath $publishedEvidenceDirectory) { throw 'Refusing to overwrite published evidence.' }
    New-Item -ItemType Directory -Path $publishedEvidenceDirectory | Out-Null
    Get-ChildItem -LiteralPath $evidenceDirectory -File | ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $publishedEvidenceDirectory $_.Name)
    }
    Write-Output "COMPLETE: $publishedEvidenceDirectory"
    Write-Output "PRIVATE PCAPS: $privateDirectory"
} finally {
    foreach ($ownedCapture in $activeCaptures.ToArray()) {
        & docker rm -f $ownedCapture 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) { Write-Warning "Could not clean up owned capture container: $ownedCapture" }
    }
}
