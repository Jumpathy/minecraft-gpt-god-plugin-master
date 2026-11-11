@echo off
rem GPTGOD easy setup - Windows launcher.
rem
rem This file only starts setup.ps1 (in this same folder), which does the work. Read setup.ps1 first
rem if you like: it is plain text.
rem
rem -ExecutionPolicy Bypass applies to this one PowerShell process only. It lets Windows run the
rem unsigned setup.ps1 without changing any system setting. No administrator rights are used.
rem
rem Options are passed through, e.g.:  setup-windows.bat --dry-run
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup.ps1" %*
echo.
pause
