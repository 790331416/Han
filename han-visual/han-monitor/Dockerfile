# 微服务 Dockerfile (监控中心)
# 使用预构建的 JAR 文件

FROM registry.cn-hangzhou.aliyuncs.com/xzy0112/eclipse-temurin:21-jre-alpine

# 设置环境变量
ENV JAVA_OPTS="-Xms256m -Xmx256m -XX:+UseG1GC -XX:MaxGCPauseMillis=200" \
    TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    SERVER_PORT=9208

# 安装必要工具
RUN apk add --no-cache tzdata curl && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

WORKDIR /app

# 复制预构建的 JAR 文件
COPY target/han-monitor.jar app.jar

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:${SERVER_PORT:-9208}/actuator/health || exit 1

# 暴露端口
EXPOSE 9208

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
