# 多阶段构建 Dockerfile 模板
#
# 【仅作参考模板，不参与任何构建】
# CI（.github/workflows/full-app-image.yml、ai-image.yml）与
# build.sh / build.ps1 / scripts/helpers/full-build-deploy.bat 全部使用各模块
# 自带的 Dockerfile（预构建 jar 加 COPY target/xxx.jar），没有任何地方引用本文件。
# 真要启用需要先补 .dockerignore：下面的 COPY . . 会把 .git、target、
# node_modules 全部塞进构建上下文。

# 阶段1: 构建
FROM registry.cn-hangzhou.aliyuncs.com/xzy0112/maven:4.0.0-rc-5-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# 复制 pom.xml 文件（利用 Docker 缓存）
COPY pom.xml .
COPY han-common ./han-common
COPY han-starter ./han-starter
COPY han-api ./han-api

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

# 非 root 运行：镜像默认 UID 0，任一服务被 RCE 后攻击者在容器内即 root
RUN addgroup -S han && adduser -S han -G han

WORKDIR /app

# 从构建阶段复制 jar 包
ARG JAR_FILE=target/*.jar
COPY --from=builder /build/${JAR_FILE} app.jar
RUN chown -R han:han /app

USER han

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:${SERVER_PORT:-8080}/actuator/health || exit 1

# 暴露端口（会被 docker-compose 覆盖）
EXPOSE 8080

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
