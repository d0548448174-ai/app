@echo off
setlocal
chcp 65001 >nul
title הורדת ויקיפדיה אופליין
echo.
echo כלי הורדה אוטומטי לויקיפדיה העברית המלאה.
echo מוריד את כלי העזר, את כל החלקים, בודק ומחבר לקובץ ZIM אחד.
echo דרושים לפחות 4 GB פנויים וחיבור שמאפשר גישה ל-GitHub.
echo.
where curl.exe >nul 2>&1
if errorlevel 1 (
  echo שגיאה: לא נמצא curl.exe ב-Windows. לא בוצעה הורדה.
  echo.
  pause
  exit /b 1
)
set "SCRIPT=%~dp0Download-Wikipedia-ZIM.ps1"
echo [שלב 1 מתוך 2] מוריד את כלי העזר מגיטאב...
curl.exe --fail --location --show-error --retry 3 "https://raw.githubusercontent.com/d0548448174-ai/app/main/Download-Wikipedia-ZIM.ps1" --output "%SCRIPT%"
if errorlevel 1 (
  echo.
  echo שגיאה: לא הצלחתי להוריד את כלי העזר.
  echo בדוק אם הרשת או נטפרי חוסמים raw.githubusercontent.com.
  echo.
  pause
  exit /b 1
)
if not exist "%SCRIPT%" (
  echo שגיאה: קובץ כלי העזר לא נוצר.
  pause
  exit /b 1
)
echo.
echo [שלב 2 מתוך 2] מפעיל את ההורדה. ייתכן שייקח זמן; ההתקדמות תוצג בחלון.
echo.
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT%"
set "RC=%ERRORLEVEL%"
if not "%RC%"=="0" (
  echo.
  echo התוכנית הסתיימה עם שגיאה. בדוק את הקובץ wikipedia-zim-download.log בתיקייה הזו.
)
echo.
echo חלון זה נשאר פתוח כדי שתוכל לקרוא את ההודעה.
pause
exit /b %RC%
