#!/bin/bash

# =============================================
# XuMan Cloud Docker 部署脚本
# 基于 PostgreSQL
# =============================================

set -e

echo "=========================================="
echo "  XuMan Cloud Docker 部署工具"
echo "=========================================="
echo ""

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查 Docker 和 Docker Compose
check_dependencies() {
    echo -e "${YELLOW}[1/6] 检查依赖...${NC}"
    
    if ! command -v docker &> /dev/null; then
        echo -e "${RED}错误: 未安装 Docker${NC}"
        exit 1
    fi
    
    if ! command -v docker-compose &> /dev/null; then
        echo -e "${RED}错误: 未安装 Docker Compose${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}✓ Docker 和 Docker Compose 已安装${NC}"
}

# 创建必要的目录
create_directories() {
    echo -e "${YELLOW}[2/6] 创建必要的目录...${NC}"
    
    mkdir -p logs
    mkdir -p data/postgres
    mkdir -p data/redis
    mkdir -p data/nacos
    mkdir -p data/files
    
    echo -e "${GREEN}✓ 目录创建完成${NC}"
}

# 复制 Dockerfile 到各个模块
copy_dockerfiles() {
    echo -e "${YELLOW}[3/6] 复制 Dockerfile...${NC}"
    
    modules=(
        "xuman-gateway"
        "xuman-auth"
        "xuman-modules/xuman-system"
        "xuman-modules/xuman-tenant"
        "xuman-modules/xuman-workflow"
        "xuman-modules/xuman-job"
        "xuman-modules/xuman-open"
        "xuman-modules/xuman-gen"
        "xuman-modules/xuman-file"
        "xuman-visual/xuman-monitor"
    )
    
    for module in "${modules[@]}"; do
        if [ -d "$module" ]; then
            cp docker/Dockerfile "$module/"
            echo "  → $module"
        fi
    done
    
    echo -e "${GREEN}✓ Dockerfile 复制完成${NC}"
}

# 复制 application-docker.yml 到各个模块
copy_configs() {
    echo -e "${YELLOW}[4/6] 复制配置文件...${NC}"
    
    for module in "${modules[@]}"; do
        if [ -d "$module/src/main/resources" ]; then
            cp docker/config/application-docker.yml "$module/src/main/resources/"
            echo "  → $module"
        fi
    done
    
    echo -e "${GREEN}✓ 配置文件复制完成${NC}"
}

# 构建镜像
build_images() {
    echo -e "${YELLOW}[5/6] 构建 Docker 镜像...${NC}"
    echo -e "${YELLOW}提示: 首次构建可能需要 10-20 分钟${NC}"
    
    docker-compose build --parallel
    
    echo -e "${GREEN}✓ 镜像构建完成${NC}"
}

# 启动服务
start_services() {
    echo -e "${YELLOW}[6/6] 启动服务...${NC}"
    
    # 先启动中间件
    echo "  → 启动中间件 (PostgreSQL, Redis, Nacos)..."
    docker-compose up -d postgres redis nacos
    
    # 等待 Nacos 启动完成
    echo "  → 等待 Nacos 启动..."
    sleep 30
    
    # 启动业务服务
    echo "  → 启动业务服务..."
    docker-compose up -d
    
    echo -e "${GREEN}✓ 服务启动完成${NC}"
}

# 显示服务状态
show_status() {
    echo ""
    echo "=========================================="
    echo "  服务状态"
    echo "=========================================="
    docker-compose ps
    
    echo ""
    echo "=========================================="
    echo "  访问地址"
    echo "=========================================="
    echo "  网关服务:     http://localhost:8080"
    echo "  Nacos 控制台: http://localhost:8848/nacos"
    echo "    用户名: xuman"
    echo "    密码:   xuman@2026"
    echo ""
    echo "  PostgreSQL:   localhost:5432"
    echo "    数据库: xuman"
    echo "    用户名: xuman"
    echo "    密码:   xuman@2026"
    echo ""
    echo "  Redis:        localhost:6379"
    echo "    密码:   xuman@2026"
    echo "=========================================="
}

# 主函数
main() {
    case "${1:-deploy}" in
        deploy)
            check_dependencies
            create_directories
            copy_dockerfiles
            copy_configs
            build_images
            start_services
            show_status
            ;;
        
        start)
            echo "启动所有服务..."
            docker-compose start
            show_status
            ;;
        
        stop)
            echo "停止所有服务..."
            docker-compose stop
            ;;
        
        restart)
            echo "重启所有服务..."
            docker-compose restart
            show_status
            ;;
        
        logs)
            docker-compose logs -f ${2:-}
            ;;
        
        clean)
            echo -e "${RED}警告: 这将删除所有容器和数据卷！${NC}"
            read -p "确认继续? (yes/no): " confirm
            if [ "$confirm" = "yes" ]; then
                docker-compose down -v
                rm -rf data logs
                echo -e "${GREEN}✓ 清理完成${NC}"
            fi
            ;;
        
        rebuild)
            echo "重新构建镜像..."
            docker-compose build --no-cache ${2:-}
            ;;
        
        *)
            echo "用法: $0 {deploy|start|stop|restart|logs|clean|rebuild}"
            echo ""
            echo "命令说明:"
            echo "  deploy   - 完整部署（首次使用）"
            echo "  start    - 启动服务"
            echo "  stop     - 停止服务"
            echo "  restart  - 重启服务"
            echo "  logs     - 查看日志（可指定服务名）"
            echo "  clean    - 清理所有数据"
            echo "  rebuild  - 重新构建镜像（可指定服务名）"
            echo ""
            echo "示例:"
            echo "  $0 deploy          # 首次部署"
            echo "  $0 logs gateway    # 查看网关日志"
            echo "  $0 rebuild job     # 重新构建任务服务"
            exit 1
            ;;
    esac
}

main "$@"
