FROM registry.cn-hangzhou.aliyuncs.com/xzy0112/eclipse-temurin:21-jre-alpine

ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200" \
    TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    SERVER_PORT=9208

RUN apk add --no-cache tzdata curl && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

WORKDIR /app

COPY target/han-ai.jar app.jar

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:${SERVER_PORT:-9208}/actuator/health || exit 1

EXPOSE 9208

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
