@echo off
REM =============================================
REM XuMan Cloud Docker 部署脚本 (Windows 版本)
REM 基于 PostgreSQL
REM =============================================

echo ==========================================
echo   XuMan Cloud Docker 部署工具
echo ==========================================
echo.

REM 检查参数
if "%1"=="" (
    set ACTION=deploy
) else (
    set ACTION=%1
)

REM 主逻辑
if "%ACTION%"=="deploy" goto DEPLOY
if "%ACTION%"=="start" goto START
if "%ACTION%"=="stop" goto STOP
if "%ACTION%"=="restart" goto RESTART
if "%ACTION%"=="logs" goto LOGS
if "%ACTION%"=="clean" goto CLEAN
if "%ACTION%"=="rebuild" goto REBUILD
goto USAGE

:DEPLOY
    echo [1/6] 检查 Docker...
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
    echo [√] Docker 和 Docker Compose 已安装
    echo.
    
    echo [2/6] 创建目录...
    if not exist logs mkdir logs
    if not exist data\postgres mkdir data\postgres
    if not exist data\redis mkdir data\redis
    if not exist data\nacos mkdir data\nacos
    if not exist data\files mkdir data\files
    echo [√] 目录创建完成
    echo.
    
    echo [3/6] 复制 Dockerfile...
    copy /Y docker\Dockerfile xuman-gateway\ >nul
    copy /Y docker\Dockerfile xuman-auth\ >nul
    copy /Y docker\Dockerfile xuman-modules\xuman-system\ >nul
    copy /Y docker\Dockerfile xuman-modules\xuman-tenant\ >nul
    copy /Y docker\Dockerfile xuman-modules\xuman-workflow\ >nul
    copy /Y docker\Dockerfile xuman-modules\xuman-job\ >nul
    copy /Y docker\Dockerfile xuman-modules\xuman-open\ >nul
    copy /Y docker\Dockerfile xuman-modules\xuman-gen\ >nul
    copy /Y docker\Dockerfile xuman-modules\xuman-file\ >nul
    copy /Y docker\Dockerfile xuman-visual\xuman-monitor\ >nul
    echo [√] Dockerfile 复制完成
    echo.
    
    echo [4/6] 复制配置文件...
    copy /Y docker\config\application-docker.yml xuman-gateway\src\main\resources\ >nul 2>&1
    copy /Y docker\config\application-docker.yml xuman-auth\src\main\resources\ >nul 2>&1
    copy /Y docker\config\application-docker.yml xuman-modules\xuman-system\src\main\resources\ >nul 2>&1
    copy /Y docker\config\application-docker.yml xuman-modules\xuman-tenant\src\main\resources\ >nul 2>&1
    copy /Y docker\config\application-docker.yml xuman-modules\xuman-workflow\src\main\resources\ >nul 2>&1
    copy /Y docker\config\application-docker.yml xuman-modules\xuman-job\src\main\resources\ >nul 2>&1
    copy /Y docker\config\application-docker.yml xuman-modules\xuman-open\src\main\resources\ >nul 2>&1
    copy /Y docker\config\application-docker.yml xuman-modules\xuman-gen\src\main\resources\ >nul 2>&1
    copy /Y docker\config\application-docker.yml xuman-modules\xuman-file\src\main\resources\ >nul 2>&1
    copy /Y docker\config\application-docker.yml xuman-visual\xuman-monitor\src\main\resources\ >nul 2>&1
    echo [√] 配置文件复制完成
    echo.
    
    echo [5/6] 构建镜像...
    echo 提示: 首次构建可能需要 10-20 分钟
    docker-compose build --parallel
    if errorlevel 1 (
        echo 错误: 镜像构建失败
        exit /b 1
    )
    echo [√] 镜像构建完成
    echo.
    
    echo [6/6] 启动服务...
    echo 启动中间件 (PostgreSQL, Redis, Nacos)...
    docker-compose up -d postgres redis nacos
    
    echo 等待 Nacos 启动...
    timeout /t 30 /nobreak >nul
    
    echo 启动业务服务...
    docker-compose up -d
    echo [√] 服务启动完成
    echo.
    
    goto SHOW_STATUS

:START
    echo 启动所有服务...
    docker-compose start
    goto SHOW_STATUS

:STOP
    echo 停止所有服务...
    docker-compose stop
    goto END

:RESTART
    echo 重启所有服务...
    docker-compose restart
    goto SHOW_STATUS

:LOGS
    if "%2"=="" (
        docker-compose logs -f
    ) else (
        docker-compose logs -f %2
    )
    goto END

:CLEAN
    echo 警告: 这将删除所有容器和数据卷！
    set /p CONFIRM="确认继续? (yes/no): "
    if /i "%CONFIRM%"=="yes" (
        docker-compose down -v
        rmdir /s /q data 2>nul
        rmdir /s /q logs 2>nul
        echo [√] 清理完成
    ) else (
        echo 操作已取消
    )
    goto END

:REBUILD
    echo 重新构建镜像...
    if "%2"=="" (
        docker-compose build --no-cache
    ) else (
        docker-compose build --no-cache %2
    )
    goto END

:SHOW_STATUS
    echo.
    echo ==========================================
    echo   服务状态
    echo ==========================================
    docker-compose ps
    
    echo.
    echo ==========================================
    echo   访问地址
    echo ==========================================
    echo   网关服务:     http://localhost:8080
    echo   Nacos 控制台: http://localhost:8848/nacos
    echo     用户名: xuman
    echo     密码:   xuman@2026
    echo.
    echo   PostgreSQL:   localhost:5432
    echo     数据库: xuman
    echo     用户名: xuman
    echo     密码:   xuman@2026
    echo.
    echo   Redis:        localhost:6379
    echo     密码:   xuman@2026
    echo ==========================================
    goto END

:USAGE
    echo 用法: %0 {deploy^|start^|stop^|restart^|logs^|clean^|rebuild}
    echo.
    echo 命令说明:
    echo   deploy   - 完整部署（首次使用）
    echo   start    - 启动服务
    echo   stop     - 停止服务
    echo   restart  - 重启服务
    echo   logs     - 查看日志（可指定服务名）
    echo   clean    - 清理所有数据
    echo   rebuild  - 重新构建镜像（可指定服务名）
    echo.
    echo 示例:
    echo   %0 deploy          # 首次部署
    echo   %0 logs gateway    # 查看网关日志
    echo   %0 rebuild job     # 重新构建任务服务
    exit /b 1

:END
    exit /b 0
