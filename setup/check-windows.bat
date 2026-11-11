@echo off
rem GPTGOD checklist - Windows launcher. Installs and changes nothing: it only runs the
rem read-only final check of setup.ps1 (same folder) and prints green/red lines with fixes.
rem -ExecutionPolicy Bypass applies to this one PowerShell process only.
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup.ps1" --check %*
echo.
pause
