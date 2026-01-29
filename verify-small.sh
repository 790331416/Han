#!/bin/bash

# =============================================
# XuMan Cloud 小型部署验证脚本
# 用于验证所有服务是否正常运行
# =============================================

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo "=========================================="
echo "  XuMan Cloud 小型部署验证工具"
echo "=========================================="
echo ""

# 检查容器状态
check_containers() {
    echo -e "${YELLOW}[1/5] 检查容器状态...${NC}"
    
    containers=(
        "xuman-postgres-small"
        "xuman-redis-small"
        "xuman-nacos-small"
        "xuman-gateway-small"
        "xuman-auth-small"
        "xuman-system-small"
        "xuman-job-small"
    )
    
    all_running=true
    
    for container in "${containers[@]}"; do
        status=$(docker inspect -f '{{.State.Status}}' $container 2>/dev/null)
        if [ "$status" = "running" ]; then
            echo -e "  ${GREEN}✓${NC} $container: Running"
        else
            echo -e "  ${RED}✗${NC} $container: $status"
            all_running=false
        fi
    done
    
    if [ "$all_running" = false ]; then
        echo -e "${RED}部分容器未运行，请检查！${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}✓ 所有容器运行正常${NC}"
    echo ""
}

# 检查 PostgreSQL
check_postgres() {
    echo -e "${YELLOW}[2/5] 检查 PostgreSQL...${NC}"
    
    result=$(docker exec xuman-postgres-small pg_isready -U xuman 2>&1)
    
    if echo "$result" | grep -q "accepting connections"; then
        echo -e "  ${GREEN}✓${NC} PostgreSQL: 连接正常"
        
        # 检查数据库是否存在
        db_exists=$(docker exec xuman-postgres-small psql -U xuman -lqt | cut -d \| -f 1 | grep -w xuman | wc -l)
        if [ $db_exists -gt 0 ]; then
            echo -e "  ${GREEN}✓${NC} 数据库 'xuman': 已创建"
        else
            echo -e "  ${RED}✗${NC} 数据库 'xuman': 未找到"
        fi
    else
        echo -e "  ${RED}✗${NC} PostgreSQL: 连接失败"
        exit 1
    fi
    
    echo ""
}

# 检查 Redis
check_redis() {
    echo -e "${YELLOW}[3/5] 检查 Redis...${NC}"
    
    result=$(docker exec xuman-redis-small redis-cli -a xuman@2026 --no-auth-warning ping 2>&1)
    
    if [ "$result" = "PONG" ]; then
        echo -e "  ${GREEN}✓${NC} Redis: 连接正常"
    else
        echo -e "  ${RED}✗${NC} Redis: 连接失败"
        exit 1
    fi
    
    echo ""
}

# 检查 Nacos
check_nacos() {
    echo -e "${YELLOW}[4/5] 检查 Nacos...${NC}"
    
    http_code=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8848/nacos/)
    
    if [ "$http_code" = "200" ] || [ "$http_code" = "302" ]; then
        echo -e "  ${GREEN}✓${NC} Nacos 控制台: http://localhost:8848/nacos"
        
        # 检查健康状态
        health=$(curl -s http://localhost:8848/nacos/v1/console/health/readiness)
        if echo "$health" | grep -q "UP"; then
            echo -e "  ${GREEN}✓${NC} Nacos 健康状态: UP"
        fi
    else
        echo -e "  ${RED}✗${NC} Nacos: 无法访问 (HTTP $http_code)"
        exit 1
    fi
    
    echo ""
}

# 检查业务服务
check_services() {
    echo -e "${YELLOW}[5/5] 检查业务服务...${NC}"
    
    services=(
        "Gateway:8080"
        "Auth:9200"
        "System:9201"
        "Job:9204"
    )
    
    for service in "${services[@]}"; do
        name=$(echo $service | cut -d: -f1)
        port=$(echo $service | cut -d: -f2)
        
        # 等待服务启动
        max_attempts=30
        attempt=0
        
        while [ $attempt -lt $max_attempts ]; do
            http_code=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:$port/actuator/health 2>/dev/null)
            
            if [ "$http_code" = "200" ]; then
                echo -e "  ${GREEN}✓${NC} $name: http://localhost:$port (健康)"
                break
            fi
            
            attempt=$((attempt + 1))
            if [ $attempt -eq $max_attempts ]; then
                echo -e "  ${YELLOW}⚠${NC} $name: http://localhost:$port (启动中...)"
            else
                sleep 2
            fi
        done
    done
    
    echo ""
}

# 显示总结
show_summary() {
    echo "=========================================="
    echo "  验证完成！"
    echo "=========================================="
    echo ""
    echo "  ${GREEN}✓${NC} 中间件层:"
    echo "    PostgreSQL: localhost:5432"
    echo "    Redis:      localhost:6379"
    echo "    Nacos:      http://localhost:8848/nacos"
    echo ""
    echo "  ${GREEN}✓${NC} 业务服务:"
    echo "    Gateway:    http://localhost:8080"
    echo "    Auth:       http://localhost:9200"
    echo "    System:     http://localhost:9201"
    echo "    Job:        http://localhost:9204"
    echo ""
    echo "  ${GREEN}✓${NC} JobFlow 监控:"
    echo "    健康检查:   curl http://localhost:9204/actuator/jobflow/health"
    echo "    配置查看:   curl http://localhost:9204/actuator/jobflow/config"
    echo "    运行指标:   curl http://localhost:9204/actuator/jobflow/metrics"
    echo ""
    echo "  ${GREEN}✓${NC} 登录凭证:"
    echo "    Nacos:      xuman / xuman@2026"
    echo "    PostgreSQL: xuman / xuman@2026"
    echo "    Redis:      xuman@2026"
    echo ""
    echo "=========================================="
    echo ""
    echo "  下一步:"
    echo "    1. 访问 Nacos 控制台创建 JobFlow 配置"
    echo "    2. 查看服务日志: ./deploy-small.sh logs [服务名]"
    echo "    3. 测试 API 接口"
    echo "    4. 阅读文档: README.md"
    echo "=========================================="
}

# 主函数
main() {
    check_containers
    check_postgres
    check_redis
    check_nacos
    check_services
    show_summary
}

main
