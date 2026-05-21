FROM registry.cn-hangzhou.aliyuncs.com/xzy0112/eclipse-temurin:21-jre-alpine

ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses=false -Dnetworkaddress.cache.ttl=60 -Dnetworkaddress.cache.negative.ttl=0" \
    TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    SERVER_PORT=9209

RUN apk add --no-cache tzdata curl && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

WORKDIR /app

COPY target/han-aivideo.jar app.jar

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:${SERVER_PORT:-9209}/actuator/health || exit 1

EXPOSE 9209

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
