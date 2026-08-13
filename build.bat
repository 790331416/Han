@echo off
REM =============================================
REM Han Cloud 全量打包（Windows）
REM 工具链固定在 D:\source 下，不使用 Program Files / AppData 里的副本。
REM 已装好的环境可以直接改用外部 JAVA_HOME / MAVEN_HOME 覆盖下面两行。
REM =============================================
setlocal

if not defined JAVA_HOME set "JAVA_HOME=D:\source\java\jdk-21.0.10"
if not defined MAVEN_HOME set "MAVEN_HOME=D:\source\maven\apache-maven-3.9.12"

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo ERROR: JAVA_HOME invalid: %JAVA_HOME%
    exit /b 1
)
if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
    echo ERROR: MAVEN_HOME invalid: %MAVEN_HOME%
    exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%"
echo JAVA_HOME=%JAVA_HOME%
echo MAVEN_HOME=%MAVEN_HOME%
java -version

REM 以脚本所在目录为仓库根，不再写死某台机器的绝对路径
cd /d "%~dp0"

call mvn clean package -DskipTests %*
set "EXIT_CODE=%ERRORLEVEL%"
echo EXIT_CODE=%EXIT_CODE%
exit /b %EXIT_CODE%
