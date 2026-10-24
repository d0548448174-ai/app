# Downloads public Hebrew Wikipedia ZIM parts, verifies SHA-256, and joins them.
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'Continue'
$logPath = Join-Path $PSScriptRoot 'wikipedia-zim-download.log'
try { Start-Transcript -Path $logPath -Force | Out-Null } catch {}

$releaseBase = 'https://github.com/d0548448174-ai/app/releases/download/wikipedia-he-nopic-2026-07a'
$finalPath = Join-Path $PSScriptRoot 'wikipedia_he_all_nopic_2026-07a.zim'
$tempDir = Join-Path $PSScriptRoot 'wikipedia-he-download-temp'
$partialPath = "$finalPath.partial"
$expectedSize = [int64]3355684354
$expectedWholeHash = '0d2b0e9454d7459441b90cf5d492fe4e9aec19872454b616418fd358cba46fd8'

function Download-File([string]$Url, [string]$Path, [string]$ExpectedHash = '') {
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        try {
            if (Test-Path -LiteralPath $Path) { Remove-Item -LiteralPath $Path -Force }
            Write-Host "Downloading: $([System.IO.Path]::GetFileName($Path)) (attempt $attempt/3)" -ForegroundColor Cyan
            & curl.exe --fail --location --show-error --retry 2 --retry-delay 2 --connect-timeout 30 --max-time 1200 --output $Path $Url
            if ($LASTEXITCODE -ne 0) { throw "curl.exe returned exit code $LASTEXITCODE" }
            if (-not (Test-Path -LiteralPath $Path) -or (Get-Item -LiteralPath $Path).Length -lt 1000) {
                throw 'The downloaded file is missing or too small.'
            }
            if ($ExpectedHash) {
                $actualHash = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
                if ($actualHash -ne $ExpectedHash.ToLowerInvariant()) {
                    throw "SHA-256 mismatch for $([System.IO.Path]::GetFileName($Path))."
                }
            }
            return
        } catch {
            Write-Host "Download attempt failed: $($_.Exception.Message)" -ForegroundColor Yellow
            if ($attempt -eq 3) { throw }
            Start-Sleep -Seconds 3
        }
    }
}

$scriptFailed = $false
try {
    Write-Host 'Hebrew Wikipedia offline downloader' -ForegroundColor Green
    Write-Host "Working folder: $PSScriptRoot"
    Write-Host "Log file: $logPath"
    if (-not (Get-Command curl.exe -ErrorAction SilentlyContinue)) { throw 'curl.exe was not found. Please use a supported Windows 10/11 computer.' }
    if (Test-Path -LiteralPath $finalPath) { throw "The destination file already exists: $finalPath. Rename or move it before trying again." }

    $driveRoot = [System.IO.Path]::GetPathRoot($PSScriptRoot)
    $drive = [System.IO.DriveInfo]::new($driveRoot)
    if ($drive.AvailableFreeSpace -lt 4GB) { throw 'At least 4 GB of free space is needed on this drive.' }

    if (Test-Path -LiteralPath $tempDir) { Remove-Item -LiteralPath $tempDir -Recurse -Force }
    if (Test-Path -LiteralPath $partialPath) { Remove-Item -LiteralPath $partialPath -Force }
    New-Item -ItemType Directory -Path $tempDir | Out-Null

    $manifestPath = Join-Path $tempDir 'SHA256SUMS.txt'
    Download-File "$releaseBase/SHA256SUMS.txt" $manifestPath
    $hashes = @{}
    foreach ($line in Get-Content -LiteralPath $manifestPath -Encoding UTF8) {
        if ($line -match '^([0-9a-fA-F]{64})\s+(?:parts/)?(wikipedia-he-nopic-\d{2}\.part)$') {
            $hashes[$matches[2].ToLowerInvariant()] = $matches[1].ToLowerInvariant()
        }
    }
    if ($hashes.Count -ne 45) { throw "The checksum manifest should contain 45 parts; found $($hashes.Count)." }

    New-Item -ItemType File -Path $partialPath | Out-Null
    for ($i = 0; $i -lt 45; $i++) {
        $partName = ('wikipedia-he-nopic-{0:D2}.part' -f $i)
        $partPath = Join-Path $tempDir $partName
        if (-not $hashes.ContainsKey($partName)) { throw "Checksum for $partName is missing." }

        Write-Host ""
        Write-Host ("Part {0}/45: {1}" -f ($i + 1), $partName) -ForegroundColor Cyan
        Download-File "$releaseBase/$partName" $partPath $hashes[$partName]

        $inputStream = [System.IO.File]::OpenRead($partPath)
        $outputStream = [System.IO.File]::Open($partialPath, [System.IO.FileMode]::Append, [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
        try { $inputStream.CopyTo($outputStream) } finally { $outputStream.Dispose(); $inputStream.Dispose() }
        Remove-Item -LiteralPath $partPath -Force
    }

    Write-Host ''
    Write-Host 'Checking the completed ZIM file...' -ForegroundColor Cyan
    $actualSize = (Get-Item -LiteralPath $partialPath).Length
    if ($actualSize -ne $expectedSize) { throw "Unexpected final size: $actualSize bytes." }
    $fileStream = [System.IO.File]::OpenRead($partialPath)
    try {
        $magic = New-Object byte[] 4
        if ($fileStream.Read($magic, 0, 4) -ne 4 -or [System.BitConverter]::ToString($magic) -ne '5A-49-4D-04') {
            throw 'The final file does not have the expected ZIM header.'
        }
    } finally { $fileStream.Dispose() }
    $wholeHash = (Get-FileHash -LiteralPath $partialPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($wholeHash -ne $expectedWholeHash) { throw 'The final SHA-256 check failed; the file will not be marked complete.' }

    Move-Item -LiteralPath $partialPath -Destination $finalPath
    Remove-Item -LiteralPath $tempDir -Recurse -Force
    Write-Host ''
    Write-Host 'SUCCESS! Complete, verified ZIM file created:' -ForegroundColor Green
    Write-Host $finalPath -ForegroundColor Green
    Write-Host 'Open Kiwix and select this ZIM file.' -ForegroundColor Green
} catch {
    $scriptFailed = $true
    Write-Host ''
    Write-Host "STOPPED: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Details are saved in: $logPath" -ForegroundColor Yellow
} finally {
    try { Stop-Transcript | Out-Null } catch {}
}
if ($scriptFailed) { exit 1 }
