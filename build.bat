@echo off
set "JAVA_HOME=D:\Program Files\Java\jdk-21.0.10"
set "PATH=%JAVA_HOME%\bin;D:\Program Files\apache-maven-3.9.12\bin;%PATH%"
echo JAVA_HOME=%JAVA_HOME%
java -version
cd /d D:\code\Han
mvn clean package -DskipTests
echo EXIT_CODE=%ERRORLEVEL%
