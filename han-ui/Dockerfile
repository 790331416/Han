FROM node:22-alpine AS builder

ENV PNPM_HOME="/pnpm"
ENV PATH="${PNPM_HOME}:${PATH}"

RUN corepack enable && corepack prepare pnpm@10.17.1 --activate

WORKDIR /app

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile

COPY . .
RUN pnpm build

# 运行时基础镜像默认沿用现有国内镜像源，保证网络受限环境下构建不中断。
# 该地址位于个人命名空间，可用性与镜像内容都不受项目控制；具备条件时用构建参数
# 覆盖为官方或企业私有仓库并锁 digest，例如：
#   docker build --build-arg NGINX_IMAGE=nginx:1.29-alpine-slim .
ARG NGINX_IMAGE=registry.cn-hangzhou.aliyuncs.com/xzy0112/nginx:1.29-alpine-slim
FROM ${NGINX_IMAGE}

COPY --from=builder /app/dist/ /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
RUN chmod -R a+rX /usr/share/nginx/html

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO- http://127.0.0.1:80/ || exit 1

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
