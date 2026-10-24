@echo off
chcp 65001 >nul
title הורדת ויקיפדיה אופליין
echo מוריד ומחבר אוטומטית את קובצי ויקיפדיה העברית.
echo דרושים לפחות 4 GB פנויים וחיבור לרשת שמאפשר GitHub.
echo אין צורך להוריד את 45 החלקים ידנית.
echo.
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0Download-Wikipedia-ZIM.ps1"
