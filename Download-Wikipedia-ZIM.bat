@echo off
chcp 65001 >nul
title הורדת ויקיפדיה אופליין
echo.
echo כלי הורדה אוטומטי לויקיפדיה העברית המלאה.
echo מוריד את כלי העזר ואז את כל החלקים, בודק תקינות ומחבר לקובץ ZIM אחד.
echo דרושים לפחות 4 GB פנויים וחיבור לרשת שמאפשר GitHub.
echo.
set "SCRIPT=%~dp0Download-Wikipedia-ZIM.ps1"
echo מוריד את כלי העזר...
curl.exe --fail --location --silent --show-error --retry 3 "https://raw.githubusercontent.com/d0548448174-ai/app/main/Download-Wikipedia-ZIM.ps1" --output "%SCRIPT%"
if errorlevel 1 (
  echo.
  echo לא הצלחתי להוריד את כלי העזר מ-GitHub. בדוק שהרשת מאפשרת raw.githubusercontent.com.
  pause
  exit /b 1
)
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT%"
