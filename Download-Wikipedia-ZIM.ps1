# Download and join the 45 verified Hebrew Wikipedia ZIM parts from GitHub Actions.
# No GitHub token is needed: the repository is public.
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'Continue'
$logPath = Join-Path $PSScriptRoot 'wikipedia-zim-download.log'
try { Start-Transcript -Path $logPath -Force | Out-Null } catch {}
Write-Host ("תיקיית העבודה: {0}" -f $PSScriptRoot) -ForegroundColor Gray
Write-Host ("יומן פעולות: {0}" -f $logPath) -ForegroundColor Gray
try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch {}
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$repo = 'd0548448174-ai/app'
$runId = 40120005126
$apiHeaders = @{ 'User-Agent' = 'Wikipedia-ZIM-Downloader'; 'Accept' = 'application/vnd.github+json' }
$finalPath = Join-Path $PSScriptRoot 'wikipedia_he_all_nopic_2026-07a.zim'
$tempDir = Join-Path $PSScriptRoot 'wikipedia-he-download-temp'
$partialPath = "$finalPath.partial"
$expectedSize = [int64]3355684354
$expectedWholeHash = '0d2b0e9454d7459441b90cf5d492fe4e9aec19872454b616418fd358cba46fd8'

function Download-File([string]$Url, [string]$Path) {
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        try {
            if (Test-Path $Path) { Remove-Item -LiteralPath $Path -Force }
            Invoke-WebRequest -Uri $Url -OutFile $Path -Headers $apiHeaders -UseBasicParsing -MaximumRedirection 10
            if ((Test-Path $Path) -and (Get-Item -LiteralPath $Path).Length -gt 1000) { return }
            throw 'הקובץ שהתקבל קטן מדי.'
        } catch {
            if ($attempt -eq 3) { throw }
            Write-Host "ההורדה נכשלה זמנית; מנסה שוב ($attempt/3)..." -ForegroundColor Yellow
            Start-Sleep -Seconds 3
        }
    }
}

$scriptFailed = $false
try {
    Write-Host 'בודק את קובצי GitHub...' -ForegroundColor Cyan
    if (Test-Path $finalPath) { throw "כבר קיים קובץ בשם $finalPath. שנה את שמו או העבר אותו לפני ההרצה." }
    if (Test-Path $tempDir) { Remove-Item -LiteralPath $tempDir -Recurse -Force }
    if (Test-Path $partialPath) { Remove-Item -LiteralPath $partialPath -Force }
    New-Item -ItemType Directory -Path $tempDir | Out-Null

    $driveRoot = [System.IO.Path]::GetPathRoot($PSScriptRoot)
    $drive = [System.IO.DriveInfo]::new($driveRoot)
    if ($drive.AvailableFreeSpace -lt 4GB) { throw 'צריך לפחות 4 GB פנויים בכונן שבו נמצא הקובץ.' }

    $apiUrl = "https://api.github.com/repos/$repo/actions/runs/$runId/artifacts?per_page=100"
    $listing = Invoke-RestMethod -Uri $apiUrl -Headers $apiHeaders -Method Get
    $active = @($listing.artifacts | Where-Object { -not $_.expired })
    $parts = @($active | Where-Object { $_.name -match '^wikipedia-he-nopic-part-\d{2}$' } | Sort-Object { [int]($_.name -replace '.*-','') })
    $checks = @($active | Where-Object { $_.name -eq 'wikipedia-he-nopic-checksums' })
    if ($parts.Count -ne 45) { throw "GitHub לא החזיר את כל 45 החלקים (נמצאו $($parts.Count)). ייתכן שהקבצים פגו תוקף." }
    if ($checks.Count -ne 1) { throw 'קובץ בדיקת התקינות לא נמצא ב-GitHub.' }

    Write-Host 'מוריד קובץ בדיקת תקינות קטן...' -ForegroundColor Cyan
    $checkZip = Join-Path $tempDir 'checksums.zip'
    Download-File $checks[0].archive_download_url $checkZip
    $checkArchive = [System.IO.Compression.ZipFile]::OpenRead($checkZip)
    try {
        $manifestEntry = @($checkArchive.Entries | Where-Object { $_.Name -eq 'SHA256SUMS.txt' })
        if ($manifestEntry.Count -ne 1) { throw 'קובץ SHA256SUMS.txt לא נמצא בארכיון.' }
        $manifestPath = Join-Path $tempDir 'SHA256SUMS.txt'
        $manifestStream = $manifestEntry[0].Open()
        $manifestOut = [System.IO.File]::Create($manifestPath)
        try { $manifestStream.CopyTo($manifestOut) } finally { $manifestOut.Dispose(); $manifestStream.Dispose() }
    } finally { $checkArchive.Dispose() }
    Remove-Item -LiteralPath $checkZip -Force

    $hashes = @{}
    foreach ($line in Get-Content -LiteralPath $manifestPath -Encoding UTF8) {
        if ($line -match '^([0-9a-fA-F]{64})\s+.*(wikipedia-he-nopic-\d{2}\.part)$') {
            $hashes[$matches[2].ToLowerInvariant()] = $matches[1].ToLowerInvariant()
        }
    }
    if ($hashes.Count -ne 45) { throw "קובץ בדיקת התקינות לא מכיל 45 חתימות (נמצאו $($hashes.Count))." }

    New-Item -ItemType File -Path $partialPath | Out-Null
    $index = 0
    foreach ($artifact in $parts) {
        $number = [int]($artifact.name -replace '.*-','')
        $partName = ('wikipedia-he-nopic-{0:D2}.part' -f $number)
        $zipPath = Join-Path $tempDir 'part.zip'
        $partPath = Join-Path $tempDir $partName
        $index++
        Write-Host ("[{0}/45] מוריד ומוודא את חלק {1:D2}..." -f $index, $number) -ForegroundColor Cyan
        Download-File $artifact.archive_download_url $zipPath

        $archive = [System.IO.Compression.ZipFile]::OpenRead($zipPath)
        try {
            $entry = @($archive.Entries | Where-Object { $_.Name -eq $partName })
            if ($entry.Count -ne 1) { throw "החלק $partName לא נמצא בתוך קובץ ההורדה." }
            $entryStream = $entry[0].Open()
            $partOut = [System.IO.File]::Create($partPath)
            try { $entryStream.CopyTo($partOut) } finally { $partOut.Dispose(); $entryStream.Dispose() }
        } finally { $archive.Dispose() }
        Remove-Item -LiteralPath $zipPath -Force

        $actualHash = (Get-FileHash -LiteralPath $partPath -Algorithm SHA256).Hash.ToLowerInvariant()
        if (-not $hashes.ContainsKey($partName) -or $actualHash -ne $hashes[$partName]) {
            throw "בדיקת התקינות נכשלה עבור $partName. הקובץ הסופי לא יישמר."
        }

        $inputStream = [System.IO.File]::OpenRead($partPath)
        $outputStream = [System.IO.File]::Open($partialPath, [System.IO.FileMode]::Append, [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
        try { $inputStream.CopyTo($outputStream) } finally { $outputStream.Dispose(); $inputStream.Dispose() }
        Remove-Item -LiteralPath $partPath -Force
    }

    Write-Host 'מבצע בדיקה סופית של הקובץ...' -ForegroundColor Cyan
    $actualSize = (Get-Item -LiteralPath $partialPath).Length
    if ($actualSize -ne $expectedSize) { throw "גודל הקובץ לא תואם: $actualSize בתים." }
    $wholeHash = (Get-FileHash -LiteralPath $partialPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($wholeHash -ne $expectedWholeHash) { throw 'בדיקת SHA-256 הסופית נכשלה. הקובץ לא ייחשב תקין.' }

    Move-Item -LiteralPath $partialPath -Destination $finalPath
    Remove-Item -LiteralPath $tempDir -Recurse -Force
    Write-Host ''
    Write-Host 'הצלחה! הקובץ המלא נוצר ונבדק:' -ForegroundColor Green
    Write-Host $finalPath -ForegroundColor Green
    Write-Host 'כעת אפשר לפתוח את Kiwix ולבחור את קובץ ה-ZIM הזה.' -ForegroundColor Green
} catch {
    $scriptFailed = $true
    Write-Host ''
    Write-Host "הפעולה נעצרה: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host 'פרטי השגיאה נשמרים בקובץ wikipedia-zim-download.log שבתיקייה הזו.' -ForegroundColor Yellow
    Write-Host 'אם זו חסימת רשת/NetFree, אל תכבה את הגנת Chrome; בדוק אם הגישה ל-GitHub מותרת.' -ForegroundColor Yellow
} finally {
    try { Stop-Transcript | Out-Null } catch {}
    Write-Host ''
    Read-Host 'לחץ Enter לסגירה'
}
if ($scriptFailed) { exit 1 }
