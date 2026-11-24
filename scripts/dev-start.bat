@echo off
rem ============================================================
rem NEXUS 本地一键启动（Windows cmd 版）
rem 用法：双击或在 cmd 中运行 scripts\dev-start.bat
rem 说明：后端 local+mock（H2 内存库，无需云凭据）+ 前端 next dev
rem       日志与 PID 写入 scripts\.dev\；停止用 dev-stop.bat
rem ============================================================
setlocal EnableExtensions
set "ROOT=%~dp0.."
set "DEV_DIR=%ROOT%\scripts\.dev"
set "JAR=%ROOT%\nexus-backend\target\nexus-backend-0.1.0-SNAPSHOT.jar"
set "MVN=D:\hong_meng\tools\apache-maven-3.9.9\bin\mvn.cmd"
if not exist "%DEV_DIR%" mkdir "%DEV_DIR%"

echo [1/4] 构建后端（local + mock）...
pushd "%ROOT%\nexus-backend"
call "%MVN%" -q clean package
if errorlevel 1 (
  echo [ERROR] 后端构建失败
  popd
  exit /b 1
)
popd

echo [2/4] 启动后端...
start "nexus-backend" /D "%ROOT%\nexus-backend" cmd /c "java -jar "%JAR%" > "%DEV_DIR%\backend.log" 2>&1"

echo [3/4] 等待后端健康检查...
:WAIT
powershell -NoProfile -Command "try { $r = Invoke-RestMethod -Uri 'http://localhost:8080/actuator/health' -TimeoutSec 2; if ($r.status -eq 'UP') { exit 0 } else { exit 1 } } catch { exit 1 }"
if errorlevel 1 (
  timeout /t 1 >nul
  goto WAIT
)

echo [4/4] 启动前端...
start "nexus-frontend" /D "%ROOT%\nexus-frontend" cmd /c "npm run dev > "%DEV_DIR%\frontend.log" 2>&1"

echo.
echo ==== NEXUS 本地环境已启动 ====
echo 前端：http://localhost:3000
echo 后端：http://localhost:8080/actuator/health
echo 停止：scripts\dev-stop.bat
endlocal
