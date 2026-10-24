@echo off
setlocal
if /i not "%~1"=="__RUNNING" (
  start "Hebrew Wikipedia downloader" "%ComSpec%" /k ""%~f0" __RUNNING"
  exit /b
)
chcp 65001 >nul
title Hebrew Wikipedia ZIM download
echo.
echo Hebrew Wikipedia offline downloader
echo It will download, verify and join 45 parts automatically.
echo At least 4 GB free disk space and access to GitHub are required.
echo.
where curl.exe >nul 2>&1
if errorlevel 1 (
  echo ERROR: curl.exe was not found on this Windows computer.
  echo.
  pause
  exit /b 1
)
set "SCRIPT=%~dp0Download-Wikipedia-ZIM.ps1"
echo Step 1 of 2: Downloading the helper script from GitHub...
curl.exe --fail --location --show-error --retry 3 "https://raw.githubusercontent.com/d0548448174-ai/app/main/Download-Wikipedia-ZIM.ps1" --output "%SCRIPT%"
if errorlevel 1 (
  echo.
  echo ERROR: Could not download the helper script.
  echo The network filter may block raw.githubusercontent.com.
  echo.
  pause
  exit /b 1
)
if not exist "%SCRIPT%" (
  echo ERROR: The helper script was not created.
  pause
  exit /b 1
)
echo.
echo Step 2 of 2: Starting the download. Progress will appear below.
echo.
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT%"
set "RC=%ERRORLEVEL%"
if not "%RC%"=="0" (
  echo.
  echo The downloader stopped with an error.
  echo If present, read wikipedia-zim-download.log in this folder.
)
echo.
echo This window will stay open. You can read the message above.
pause
exit /b %RC%
