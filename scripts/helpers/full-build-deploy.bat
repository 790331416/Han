@echo off
chcp 65001 >nul 2>&1
setlocal enabledelayedexpansion

set "PROJECT_DIR=%~dp0"
set "PROJECT_DIR=%PROJECT_DIR:~0,-1%"
set "REGISTRY=registry.cn-hangzhou.aliyuncs.com/xzy0112"

echo ========================================================
echo Han Cloud - Full Build and Deploy
echo ========================================================
echo.

if not "%~1"=="" (
    set "ENV_CHOICE=%~1"
    goto :skip_prompt
)
echo Select deployment environment:
echo   [1] Large  - All services
echo   [2] Medium - Core services
echo   [3] Small  - Minimal services
echo.
set /p ENV_CHOICE=Enter option (1-3): 
:skip_prompt

if "%ENV_CHOICE%"=="1" goto :env_large
if "%ENV_CHOICE%"=="2" goto :env_medium
if "%ENV_CHOICE%"=="3" goto :env_small
echo ERROR: Invalid option
pause
exit /b 1

:env_large
set ENV_TYPE=LARGE
set ENV_NAME=Large
set "MAVEN_MODULES=han-gateway,han-auth,han-modules/han-system,han-modules/han-file,han-modules/han-job,han-modules/han-open,han-modules/han-tenant,han-modules/han-workflow,han-visual/han-monitor"
set UI_BUILD=1
goto :env_done

:env_medium
set ENV_TYPE=MEDIUM
set ENV_NAME=Medium
set "MAVEN_MODULES=han-gateway,han-auth,han-modules/han-system,han-modules/han-job"
set UI_BUILD=1
goto :env_done

:env_small
set ENV_TYPE=SMALL
set ENV_NAME=Small
set "MAVEN_MODULES=han-gateway,han-auth,han-modules/han-system"
set UI_BUILD=0
goto :env_done

:env_done
echo.
echo Selected: %ENV_NAME%
echo Modules:  %MAVEN_MODULES%
echo.

REM ========================================================
REM Step 1: Setup Java Environment
REM ========================================================
echo [Step 1/8] Setup Java Environment...
echo --------------------------------------------------------

REM 工具链统一走 D:\source（见工作区规范），不使用 Program Files 下的副本
set "JAVA_HOME=D:\source\java\jdk-21.0.10"
set "MAVEN_HOME=D:\source\maven\apache-maven-3.9.12"
set "NODE_HOME=D:\source\nodejs"
set "COREPACK_SHIM_HOME=D:\source\nodejs\node_modules\corepack\shims"
set "MAVEN_CMD=%MAVEN_HOME%\bin\mvn.cmd"
set "MAVEN_OPTS=-Dmaven.repo.local=D:\source\maven\.m2\repository"
set "NPM_CONFIG_CACHE=D:\source\nvm\npm-cache"
set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%NODE_HOME%;%COREPACK_SHIM_HOME%;%PATH%"

echo JAVA_HOME: %JAVA_HOME%
echo MAVEN_HOME: %MAVEN_HOME%
echo.

"%JAVA_HOME%\bin\java.exe" -version
if errorlevel 1 (
    echo ERROR: Java not found
    pause
    exit /b 1
)
echo.

call "%MAVEN_CMD%" -version
if errorlevel 1 (
    echo ERROR: Maven not found
    pause
    exit /b 1
)
echo.

REM ========================================================
REM Step 2: Maven Build Backend
REM ========================================================
echo [Step 2/8] Maven Build Backend...
echo --------------------------------------------------------
echo.

cd /d "%PROJECT_DIR%"
call "%MAVEN_CMD%" clean package -DskipTests -B -pl %MAVEN_MODULES% -am
if errorlevel 1 (
    echo Maven Build FAILED
    pause
    exit /b 1
)

echo.
echo Maven Build SUCCESS
echo.

REM ========================================================
REM Step 3: Verify JAR Files
REM ========================================================
echo [Step 3/8] Verify JAR Files...
echo --------------------------------------------------------

set JAR_COUNT=0
set EXPECTED_JAR=0

call :check_jar "%PROJECT_DIR%\han-gateway\target\han-gateway.jar" "Gateway"
call :check_jar "%PROJECT_DIR%\han-auth\target\han-auth.jar" "Auth"
call :check_jar "%PROJECT_DIR%\han-modules\han-system\target\han-system.jar" "System"

if "%ENV_TYPE%"=="MEDIUM" call :check_jar "%PROJECT_DIR%\han-modules\han-job\target\han-job.jar" "Job"

if "%ENV_TYPE%"=="LARGE" call :check_jar_large

echo.
echo JAR files: !JAR_COUNT! / !EXPECTED_JAR!
if !JAR_COUNT! NEQ !EXPECTED_JAR! echo WARNING: Some JARs missing!
echo.

REM ========================================================
REM Step 4: pnpm Build Frontend
REM ========================================================
echo [Step 4/8] pnpm Build Frontend...
echo --------------------------------------------------------

REM han-ui 是 pnpm workspace 工程（han-ui/Dockerfile 用 corepack prepare pnpm
REM 加 pnpm install --frozen-lockfile，锁文件是 pnpm-lock.yaml）。用 npm install
REM 会忽略 pnpm 锁文件重新解析依赖，产出的 dist 与 CI 产物不可比。
if "%UI_BUILD%"=="0" goto :skip_ui
if not exist "%PROJECT_DIR%\han-ui\package.json" goto :skip_ui

cd /d "%PROJECT_DIR%\han-ui"
echo Preparing pnpm via corepack...
call corepack enable
call corepack prepare pnpm@10.17.1 --activate
if errorlevel 1 (
    echo ERROR: corepack prepare pnpm FAILED
    cd /d "%PROJECT_DIR%"
    exit /b 1
)

echo Installing dependencies...
call pnpm install --frozen-lockfile
if errorlevel 1 (
    echo ERROR: pnpm install FAILED
    cd /d "%PROJECT_DIR%"
    exit /b 1
)

echo Building frontend...
call pnpm build
if errorlevel 1 (
    echo ERROR: Frontend build FAILED
    cd /d "%PROJECT_DIR%"
    exit /b 1
)

echo Frontend Build SUCCESS
cd /d "%PROJECT_DIR%"
goto :ui_done

:skip_ui
set UI_BUILD=0
echo Skipping frontend build (not selected for this environment).
:ui_done
echo.

REM ========================================================
REM Step 5: Docker Image Build
REM ========================================================
echo [Step 5/8] Docker Image Build...
echo --------------------------------------------------------
echo.

docker network inspect han-network >nul 2>&1
if errorlevel 1 docker network create han-network

echo Stopping old containers...
echo   han-ui han-monitor han-workflow han-tenant han-open han-job han-file han-system han-auth han-gateway
if not defined HAN_BUILD_DEPLOY_CONFIRM (
    set /p STOP_CONFIRM=Type yes to stop and remove the containers above: 
    if not "!STOP_CONFIRM!"=="yes" (
        echo Aborted by user.
        exit /b 1
    )
)
for %%s in (han-ui han-monitor han-workflow han-tenant han-open han-job han-file han-system han-auth han-gateway) do (
    docker stop %%s 2>nul
    docker rm %%s 2>nul
)
echo.

echo Building Gateway image...
cd /d "%PROJECT_DIR%\han-gateway"
docker build -t han-gateway:latest .
if errorlevel 1 goto :docker_error

echo Building Auth image...
cd /d "%PROJECT_DIR%\han-auth"
docker build -t han-auth:latest .
if errorlevel 1 goto :docker_error

echo Building System image...
cd /d "%PROJECT_DIR%\han-modules\han-system"
docker build -t han-system:latest .
if errorlevel 1 goto :docker_error

cd /d "%PROJECT_DIR%"

if "%ENV_TYPE%"=="MEDIUM" call :build_medium
if "%ENV_TYPE%"=="LARGE" call :build_large

if "%UI_BUILD%"=="1" (
    echo Building UI image...
    cd /d "%PROJECT_DIR%\han-ui"
    docker build -t han-ui:latest .
    cd /d "%PROJECT_DIR%"
)

echo.
echo Docker Image Build SUCCESS
echo.

REM ========================================================
REM Step 5.5: Push to Alibaba Cloud Registry (opt-in)
REM ========================================================
echo [Step 5.5/8] Push to Alibaba Cloud Registry...
echo --------------------------------------------------------
echo.

REM docs/03-部署手册.md:220 与 :391、docs/05-运维与95环境手册.md:126 都明确规定
REM 后端和前端正式镜像只能由 GitHub Actions 构建推送，开发机不得替代 CI 构建。
REM 这一步会覆盖生产 ACR 的 :latest，默认关闭；确需隔离环境自测时显式设置
REM HAN_ALLOW_PROD_PUSH=1 再执行。
if not defined HAN_ALLOW_PROD_PUSH (
    echo SKIPPED - pushing to %REGISTRY% is CI-only.
    echo           Set HAN_ALLOW_PROD_PUSH=1 to override for an isolated environment.
    echo.
    goto :push_done
)

echo WARNING: pushing :latest to %REGISTRY% from a developer machine.
docker tag han-gateway:latest %REGISTRY%/han-gateway:latest
docker tag han-auth:latest %REGISTRY%/han-auth:latest
docker tag han-system:latest %REGISTRY%/han-system:latest
docker push %REGISTRY%/han-gateway:latest
docker push %REGISTRY%/han-auth:latest
docker push %REGISTRY%/han-system:latest

if "%ENV_TYPE%"=="MEDIUM" call :push_medium
if "%ENV_TYPE%"=="LARGE" call :push_large

if "%UI_BUILD%"=="1" (
    docker tag han-ui:latest %REGISTRY%/han-ui:latest
    docker push %REGISTRY%/han-ui:latest
)

echo.
echo Image Push SUCCESS
echo.

:push_done

REM ========================================================
REM Step 6: Docker Container Deploy
REM ========================================================
echo [Step 6/8] Docker Container Deploy...
echo --------------------------------------------------------
echo.

REM 口令一律由环境变量注入，不再在脚本里写明文
if not defined DB_PASSWORD (
    echo ERROR: DB_PASSWORD is not set. Export it before running this script.
    exit /b 1
)
if "%ENV_TYPE%"=="LARGE" (
    if not defined MONITOR_ADMIN_USERNAME (
        echo ERROR: MONITOR_ADMIN_USERNAME is not set; han-monitor refuses to start without it.
        exit /b 1
    )
    if not defined MONITOR_ADMIN_PASSWORD (
        echo ERROR: MONITOR_ADMIN_PASSWORD is not set; han-monitor refuses to start without it.
        exit /b 1
    )
)

docker run -d --name han-gateway --network han-network -p 8080:8080 -e NACOS_SERVER_ADDR=han-nacos:8848 -e REDIS_HOST=han-redis han-gateway:latest
docker run -d --name han-auth --network han-network -p 9200:9200 -e NACOS_SERVER_ADDR=han-nacos:8848 -e REDIS_HOST=han-redis han-auth:latest
docker run -d --name han-system --network han-network -p 9201:9201 -e NACOS_SERVER_ADDR=han-nacos:8848 -e REDIS_HOST=han-redis -e DB_URL=jdbc:postgresql://han-postgres:5432/han?useUnicode=true -e DB_USER=han -e DB_PASSWORD=%DB_PASSWORD% han-system:latest

if "%ENV_TYPE%"=="MEDIUM" docker run -d --name han-job --network han-network -p 9204:9204 han-job:latest

if "%ENV_TYPE%"=="LARGE" call :deploy_large

if "%UI_BUILD%"=="1" docker run -d --name han-ui --network han-network -p 80:80 han-ui:latest

echo.
echo Docker Container Deploy SUCCESS
echo.

REM ========================================================
REM Step 7: Wait and Check
REM ========================================================
echo [Step 7/8] Waiting 30s for services to start...
timeout /t 30 /nobreak >nul

echo.
echo [Step 8/8] Service Status Check...
echo --------------------------------------------------------
echo.
docker ps --filter "name=han-" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

echo.
echo ========================================================
echo Deploy Complete! Environment: %ENV_NAME%
echo ========================================================
echo.
echo Service URLs:
echo   Gateway:  http://localhost:8080
echo   Auth:     http://localhost:9200
echo   System:   http://localhost:9201
if "%ENV_TYPE%"=="MEDIUM" echo   Job:      http://localhost:9204
if "%ENV_TYPE%"=="LARGE" (
    echo   Tenant:   http://localhost:9202
    echo   Workflow: http://localhost:9203
    echo   Job:      http://localhost:9204
    echo   Open:     http://localhost:9205
    echo   File:     http://localhost:9207
    echo   Monitor:  http://localhost:9100
)
if "%UI_BUILD%"=="1" echo   Frontend: http://localhost
echo.
echo Registry: %REGISTRY%
echo.

goto :end

REM ========================================================
REM Subroutines
REM ========================================================
:check_jar
set /a EXPECTED_JAR+=1
if exist "%~1" (
    echo   [OK] %~2
    set /a JAR_COUNT+=1
) else (
    echo   [MISSING] %~2 - %~1
)
goto :eof

:check_jar_large
call :check_jar "%PROJECT_DIR%\han-modules\han-file\target\han-file.jar" "File"
call :check_jar "%PROJECT_DIR%\han-modules\han-job\target\han-job.jar" "Job"
call :check_jar "%PROJECT_DIR%\han-modules\han-open\target\han-open.jar" "Open"
call :check_jar "%PROJECT_DIR%\han-modules\han-tenant\target\han-tenant.jar" "Tenant"
call :check_jar "%PROJECT_DIR%\han-modules\han-workflow\target\han-workflow.jar" "Workflow"
call :check_jar "%PROJECT_DIR%\han-visual\han-monitor\target\han-monitor.jar" "Monitor"
goto :eof

:build_medium
echo Building Job image...
cd /d "%PROJECT_DIR%\han-modules\han-job"
docker build -t han-job:latest .
if errorlevel 1 goto :docker_error
cd /d "%PROJECT_DIR%"
goto :eof

:build_large
echo Building File image...
cd /d "%PROJECT_DIR%\han-modules\han-file"
docker build -t han-file:latest .
if errorlevel 1 goto :docker_error
echo Building Job image...
cd /d "%PROJECT_DIR%\han-modules\han-job"
docker build -t han-job:latest .
if errorlevel 1 goto :docker_error
echo Building Open image...
cd /d "%PROJECT_DIR%\han-modules\han-open"
docker build -t han-open:latest .
if errorlevel 1 goto :docker_error
echo Building Tenant image...
cd /d "%PROJECT_DIR%\han-modules\han-tenant"
docker build -t han-tenant:latest .
if errorlevel 1 goto :docker_error
echo Building Workflow image...
cd /d "%PROJECT_DIR%\han-modules\han-workflow"
docker build -t han-workflow:latest .
if errorlevel 1 goto :docker_error
echo Building Monitor image...
cd /d "%PROJECT_DIR%\han-visual\han-monitor"
docker build -t han-monitor:latest .
if errorlevel 1 goto :docker_error
cd /d "%PROJECT_DIR%"
goto :eof

:push_medium
docker tag han-job:latest %REGISTRY%/han-job:latest
docker push %REGISTRY%/han-job:latest
goto :eof

:push_large
docker tag han-file:latest %REGISTRY%/han-file:latest
docker tag han-job:latest %REGISTRY%/han-job:latest
docker tag han-open:latest %REGISTRY%/han-open:latest
docker tag han-tenant:latest %REGISTRY%/han-tenant:latest
docker tag han-workflow:latest %REGISTRY%/han-workflow:latest
docker tag han-monitor:latest %REGISTRY%/han-monitor:latest
docker push %REGISTRY%/han-file:latest
docker push %REGISTRY%/han-job:latest
docker push %REGISTRY%/han-open:latest
docker push %REGISTRY%/han-tenant:latest
docker push %REGISTRY%/han-workflow:latest
docker push %REGISTRY%/han-monitor:latest
goto :eof

:deploy_large
docker run -d --name han-tenant --network han-network -p 9202:9202 han-tenant:latest
docker run -d --name han-workflow --network han-network -p 9203:9203 han-workflow:latest
docker run -d --name han-job --network han-network -p 9204:9204 han-job:latest
docker run -d --name han-open --network han-network -p 9205:9205 han-open:latest
docker run -d --name han-file --network han-network -p 9207:9207 han-file:latest
REM han-monitor 的镜像端口已从 9208 统一为 9100（与 han-ai 的 9208 冲突），
REM 且凭据改为强制环境变量注入，未配置即启动失败。
docker run -d --name han-monitor --network han-network -p 9100:9100 -e MONITOR_ADMIN_USERNAME=%MONITOR_ADMIN_USERNAME% -e MONITOR_ADMIN_PASSWORD=%MONITOR_ADMIN_PASSWORD% han-monitor:latest
goto :eof

:docker_error
cd /d "%PROJECT_DIR%"
echo.
echo Docker Image Build FAILED
echo Check: JAR files, Dockerfiles, Docker daemon
pause
exit /b 1

:end
echo.
echo All Steps Complete!
echo.
pause