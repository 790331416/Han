# 多阶段构建 Dockerfile 模板
# 适用于所有 XuMan 微服务模块

# 阶段1: 构建
FROM registry.cn-hangzhou.aliyuncs.com/xzy0112/maven:4.0.0-rc-5-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# 复制 pom.xml 文件（利用 Docker 缓存）
COPY pom.xml .
COPY xuman-common ./xuman-common
COPY xuman-starter ./xuman-starter
COPY xuman-api ./xuman-api

# 下载依赖（这一层会被缓存）
RUN mvn dependency:go-offline -B

# 复制源码
COPY . .

# 构建项目（跳过测试）
RUN mvn clean package -DskipTests -B

# 阶段2: 运行
FROM registry.cn-hangzhou.aliyuncs.com/xzy0112/eclipse-temurin:21-jre-alpine

# 设置环境变量
ENV JAVA_OPTS="-Xms512m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200" \
    TZ=Asia/Shanghai \
    LANG=C.UTF-8

# 安装必要工具
RUN apk add --no-cache tzdata curl && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

WORKDIR /app

# 从构建阶段复制 jar 包
ARG JAR_FILE=target/*.jar
COPY --from=builder /build/${JAR_FILE} app.jar

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:${SERVER_PORT:-8080}/actuator/health || exit 1

# 暴露端口（会被 docker-compose 覆盖）
EXPOSE 8080

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
