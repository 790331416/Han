#!/bin/bash

# =============================================
# XuMan Cloud 小型部署脚本
# 适用场景: 个人开发、学习演示
# =============================================

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo "=========================================="
echo "  XuMan Cloud 小型部署工具（极简版）"
echo "  中间件: PostgreSQL + Redis + Nacos"
echo "  服务: Gateway + Auth + System + Job"
echo "=========================================="
echo ""

# 检查 Docker
check_docker() {
    echo -e "${YELLOW}[1/5] 检查 Docker...${NC}"
    
    if ! command -v docker &> /dev/null; then
        echo -e "${RED}错误: 未安装 Docker${NC}"
        exit 1
    fi
    
    if ! command -v docker-compose &> /dev/null; then
        echo -e "${RED}错误: 未安装 Docker Compose${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}✓ Docker 已安装${NC}"
}

# 创建目录
create_dirs() {
    echo -e "${YELLOW}[2/5] 创建数据目录...${NC}"
    
    mkdir -p logs
    mkdir -p data/postgres-small
    mkdir -p data/redis-small
    mkdir -p data/nacos-small
    
    echo -e "${GREEN}✓ 目录创建完成${NC}"
}

# 复制 Dockerfile
copy_files() {
    echo -e "${YELLOW}[3/5] 准备构建文件...${NC}"
    
    # 复制 Dockerfile
    [ -f docker/Dockerfile ] && cp docker/Dockerfile xuman-gateway/ || echo "  → xuman-gateway/Dockerfile 已存在"
    [ -f docker/Dockerfile ] && cp docker/Dockerfile xuman-auth/ || echo "  → xuman-auth/Dockerfile 已存在"
    [ -f docker/Dockerfile ] && cp docker/Dockerfile xuman-modules/xuman-system/ || echo "  → xuman-system/Dockerfile 已存在"
    [ -f docker/Dockerfile ] && cp docker/Dockerfile xuman-modules/xuman-job/ || echo "  → xuman-job/Dockerfile 已存在"
    
    # 复制配置文件
    [ -f docker/config/application-docker.yml ] && cp docker/config/application-docker.yml xuman-gateway/src/main/resources/ || echo "  → Gateway 配置已存在"
    [ -f docker/config/application-docker.yml ] && cp docker/config/application-docker.yml xuman-auth/src/main/resources/ || echo "  → Auth 配置已存在"
    [ -f docker/config/application-docker.yml ] && cp docker/config/application-docker.yml xuman-modules/xuman-system/src/main/resources/ || echo "  → System 配置已存在"
    [ -f docker/config/application-docker.yml ] && cp docker/config/application-docker.yml xuman-modules/xuman-job/src/main/resources/ || echo "  → Job 配置已存在"
    
    echo -e "${GREEN}✓ 文件准备完成${NC}"
}

# 启动服务
start_services() {
    echo -e "${YELLOW}[4/5] 启动服务...${NC}"
    echo -e "${YELLOW}提示: 首次启动需要构建镜像，可能需要 5-10 分钟${NC}"
    echo ""
    
    # 先启动中间件
    echo "  → 启动中间件 (PostgreSQL, Redis, Nacos)..."
    docker-compose -f docker-compose-small.yml up -d postgres redis nacos
    
    # 等待中间件就绪
    echo "  → 等待 Nacos 启动..."
    sleep 30
    
    # 启动业务服务
    echo "  → 启动业务服务 (Gateway, Auth, System, Job)..."
    docker-compose -f docker-compose-small.yml up -d
    
    echo -e "${GREEN}✓ 服务启动完成${NC}"
}

# 显示状态
show_status() {
    echo -e "${YELLOW}[5/5] 检查服务状态...${NC}"
    echo ""
    
    sleep 10
    
    docker-compose -f docker-compose-small.yml ps
    
    echo ""
    echo "=========================================="
    echo "  部署完成！"
    echo "=========================================="
    echo "  访问地址:"
    echo "    网关服务:     http://localhost:8080"
    echo "    Nacos 控制台: http://localhost:8848/nacos"
    echo "      用户名: xuman"
    echo "      密码:   xuman@2026"
    echo ""
    echo "  数据库连接:"
    echo "    PostgreSQL:   localhost:5432"
    echo "      数据库: xuman"
    echo "      用户名: xuman"
    echo "      密码:   xuman@2026"
    echo ""
    echo "  Redis 连接:"
    echo "    地址: localhost:6379"
    echo "    密码: xuman@2026"
    echo ""
    echo "  资源占用:"
    echo "    CPU:  约 2 核"
    echo "    内存: 约 3-4 GB"
    echo "=========================================="
    echo ""
    echo "  常用命令:"
    echo "    查看日志: docker-compose -f docker-compose-small.yml logs -f [服务名]"
    echo "    停止服务: docker-compose -f docker-compose-small.yml stop"
    echo "    启动服务: docker-compose -f docker-compose-small.yml start"
    echo "    删除服务: docker-compose -f docker-compose-small.yml down -v"
    echo "=========================================="
}

# 主函数
main() {
    case "${1:-deploy}" in
        deploy)
            check_docker
            create_dirs
            copy_files
            start_services
            show_status
            ;;
        
        start)
            echo "启动小型环境..."
            docker-compose -f docker-compose-small.yml start
            show_status
            ;;
        
        stop)
            echo "停止小型环境..."
            docker-compose -f docker-compose-small.yml stop
            ;;
        
        restart)
            echo "重启小型环境..."
            docker-compose -f docker-compose-small.yml restart
            show_status
            ;;
        
        logs)
            docker-compose -f docker-compose-small.yml logs -f ${2:-}
            ;;
        
        clean)
            echo -e "${RED}警告: 这将删除所有容器和数据！${NC}"
            read -p "确认继续? (yes/no): " confirm
            if [ "$confirm" = "yes" ]; then
                docker-compose -f docker-compose-small.yml down -v
                rm -rf data/postgres-small data/redis-small data/nacos-small logs
                echo -e "${GREEN}✓ 清理完成${NC}"
            fi
            ;;
        
        *)
            echo "用法: $0 {deploy|start|stop|restart|logs|clean}"
            echo ""
            echo "命令说明:"
            echo "  deploy   - 完整部署（首次使用）"
            echo "  start    - 启动服务"
            echo "  stop     - 停止服务"
            echo "  restart  - 重启服务"
            echo "  logs     - 查看日志（可指定服务名）"
            echo "  clean    - 清理所有数据"
            exit 1
            ;;
    esac
}

main "$@"
