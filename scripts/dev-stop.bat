@echo off
rem ============================================================
rem NEXUS 本地一键停止（Windows cmd 版）
rem 用法：双击或在 cmd 中运行 scripts\dev-stop.bat
rem ============================================================
setlocal EnableExtensions

echo [1/2] 结束后端（java -jar nexus-backend）...
for /f "tokens=1" %%p in ('wmic process where "name='java.exe' and CommandLine like '%%nexus-backend%%'" get ProcessId ^| findstr /r "[0-9]"') do (
  taskkill /PID %%p /F >nul 2>&1 && echo       已结束 PID %%p
)

echo [2/2] 结束前端（next dev / node）...
for /f "tokens=1" %%p in ('wmic process where "name='node.exe' and CommandLine like '%%nexus-frontend%%'" get ProcessId ^| findstr /r "[0-9]"') do (
  taskkill /PID %%p /F >nul 2>&1 && echo       已结束 PID %%p
)

echo 完成。日志保留在 scripts\.dev\
endlocal
