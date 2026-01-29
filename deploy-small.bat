@echo off
REM =============================================
REM XuMan Cloud 小型部署脚本 (Windows)
REM 适用场景: 个人开发、学习演示
REM =============================================

echo ==========================================
echo   XuMan Cloud 小型部署工具（极简版）
echo   中间件: PostgreSQL + Redis + Nacos
echo   服务: Gateway + Auth + System + Job
echo ==========================================
echo.

if "%1"=="" (
    set ACTION=deploy
) else (
    set ACTION=%1
)

if "%ACTION%"=="deploy" goto DEPLOY
if "%ACTION%"=="start" goto START
if "%ACTION%"=="stop" goto STOP
if "%ACTION%"=="restart" goto RESTART
if "%ACTION%"=="logs" goto LOGS
if "%ACTION%"=="clean" goto CLEAN
goto USAGE

:DEPLOY
    echo [1/5] 检查 Docker...
    docker --version >nul 2>&1
    if errorlevel 1 (
        echo 错误: 未安装 Docker
        exit /b 1
    )
    docker-compose --version >nul 2>&1
    if errorlevel 1 (
        echo 错误: 未安装 Docker Compose
        exit /b 1
    )
    echo [√] Docker 已安装
    echo.
    
    echo [2/5] 创建数据目录...
    if not exist logs mkdir logs
    if not exist data\postgres-small mkdir data\postgres-small
    if not exist data\redis-small mkdir data\redis-small
    if not exist data\nacos-small mkdir data\nacos-small
    echo [√] 目录创建完成
    echo.
    
    echo [3/5] 准备构建文件...
    if exist docker\Dockerfile (
        copy /Y docker\Dockerfile xuman-gateway\ >nul
        copy /Y docker\Dockerfile xuman-auth\ >nul
        copy /Y docker\Dockerfile xuman-modules\xuman-system\ >nul
        copy /Y docker\Dockerfile xuman-modules\xuman-job\ >nul
    )
    if exist docker\config\application-docker.yml (
        copy /Y docker\config\application-docker.yml xuman-gateway\src\main\resources\ >nul 2>&1
        copy /Y docker\config\application-docker.yml xuman-auth\src\main\resources\ >nul 2>&1
        copy /Y docker\config\application-docker.yml xuman-modules\xuman-system\src\main\resources\ >nul 2>&1
        copy /Y docker\config\application-docker.yml xuman-modules\xuman-job\src\main\resources\ >nul 2>&1
    )
    echo [√] 文件准备完成
    echo.
    
    echo [4/5] 启动服务...
    echo 提示: 首次启动需要构建镜像，可能需要 5-10 分钟
    echo.
    echo 启动中间件 (PostgreSQL, Redis, Nacos)...
    docker-compose -f docker-compose-small.yml up -d postgres redis nacos
    
    echo 等待 Nacos 启动...
    timeout /t 30 /nobreak >nul
    
    echo 启动业务服务 (Gateway, Auth, System, Job)...
    docker-compose -f docker-compose-small.yml up -d
    echo [√] 服务启动完成
    echo.
    
    echo [5/5] 检查服务状态...
    timeout /t 10 /nobreak >nul
    goto SHOW_STATUS

:START
    echo 启动小型环境...
    docker-compose -f docker-compose-small.yml start
    goto SHOW_STATUS

:STOP
    echo 停止小型环境...
    docker-compose -f docker-compose-small.yml stop
    goto END

:RESTART
    echo 重启小型环境...
    docker-compose -f docker-compose-small.yml restart
    goto SHOW_STATUS

:LOGS
    if "%2"=="" (
        docker-compose -f docker-compose-small.yml logs -f
    ) else (
        docker-compose -f docker-compose-small.yml logs -f %2
    )
    goto END

:CLEAN
    echo 警告: 这将删除所有容器和数据！
    set /p CONFIRM="确认继续? (yes/no): "
    if /i "%CONFIRM%"=="yes" (
        docker-compose -f docker-compose-small.yml down -v
        rmdir /s /q data\postgres-small 2>nul
        rmdir /s /q data\redis-small 2>nul
        rmdir /s /q data\nacos-small 2>nul
        rmdir /s /q logs 2>nul
        echo [√] 清理完成
    )
    goto END

:SHOW_STATUS
    echo.
    docker-compose -f docker-compose-small.yml ps
    echo.
    echo ==========================================
    echo   部署完成！
    echo ==========================================
    echo   访问地址:
    echo     网关服务:     http://localhost:8080
    echo     Nacos 控制台: http://localhost:8848/nacos
    echo       用户名: xuman
    echo       密码:   xuman@2026
    echo.
    echo   数据库连接:
    echo     PostgreSQL:   localhost:5432
    echo       数据库: xuman
    echo       用户名: xuman
    echo       密码:   xuman@2026
    echo.
    echo   Redis 连接:
    echo     地址: localhost:6379
    echo     密码: xuman@2026
    echo.
    echo   资源占用:
    echo     CPU:  约 2 核
    echo     内存: 约 3-4 GB
    echo ==========================================
    echo.
    echo   常用命令:
    echo     查看日志: deploy-small.bat logs [服务名]
    echo     停止服务: deploy-small.bat stop
    echo     启动服务: deploy-small.bat start
    echo     删除服务: deploy-small.bat clean
    echo ==========================================
    goto END

:USAGE
    echo 用法: %0 {deploy^|start^|stop^|restart^|logs^|clean}
    echo.
    echo 命令说明:
    echo   deploy   - 完整部署（首次使用）
    echo   start    - 启动服务
    echo   stop     - 停止服务
    echo   restart  - 重启服务
    echo   logs     - 查看日志（可指定服务名）
    echo   clean    - 清理所有数据
    exit /b 1

:END
    exit /b 0
