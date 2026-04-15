# 完整编译打包部署指南

## 脚本说明

`full-build-deploy.bat` 是一个完整的自动化脚本，支持：
- ✅ 环境选择（大/中/小）
- ✅ Maven 编译后端
- ✅ NPM 编译前端
- ✅ Docker 镜像构建
- ✅ Docker 容器部署

## 使用方法

### 第一步：运行脚本

**双击运行**：
```
D:\code\Han\full-build-deploy.bat
```

### 第二步：选择环境

脚本启动后会提示选择部署环境：

```
请选择部署环境：
  [1] 大环境 - 完整部署所有服务
  [2] 中环境 - 部署核心服务
  [3] 小环境 - 部署最小服务集
请输入选项 (1-3):
```

#### 选项 1：大环境（完整部署）

**包含服务**：
- Gateway（端口 8080）
- Auth（端口 9200）
- System（端口 9201）
- File（端口 9202）
- Job（端口 9204）
- Open（端口 9205）
- Tenant（端口 9206）
- Workflow（端口 9207）
- Monitor（端口 9208）
- Frontend（端口 80）

**编译模块**：所有后端模块
**编译前端**：是

**适用场景**：生产环境或完整功能测试

## 环境配置

脚本已预配置以下环境变量：

- **JAVA_HOME**：`D:\Program Files\Java\jdk-21.0.10`
- **MAVEN_HOME**：`D:\Program Files\apache-maven-3.9.12`
- **NVM_HOME**：`D:\Program Files\nvm\v20.0.0`

脚本会自动设置这些环境变量。

### 选项 2：中环境（核心服务）

**包含服务**：
- Gateway（端口 8080）
- Auth（端口 9200）
- System（端口 9201）
- Job（端口 9204）
- Frontend（端口 80）

**编译模块**：gateway, auth, system, job
**编译前端**：是

**适用场景**：日常开发或功能测试

#### 选项 3：小环境（最小服务）

**包含服务**：
- Gateway（端口 8080）
- Auth（端口 9200）
- System（端口 9201）
- Frontend（端口 80）

**编译模块**：gateway, auth, system
**编译前端**：是

**适用场景**：快速测试或轻量开发

### 第三步：自动执行流程

脚本会自动执行以下步骤：

1. ✅ **设置 Java 环境**
   - 设置 JAVA_HOME
   - 验证 Java 版本

2. ✅ **Maven 编译后端**
   - 清理旧构建
   - 编译指定模块
   - 跳过测试
   - 批量模式

3. ✅ **验证 JAR 文件**
   - 检查 JAR 文件是否存在
   - 统计生成数量
   - 不匹配时提示确认

4. ✅ **NPM 编译前端**
   - 安装依赖
   - 构建 Vue 项目
   - 生成静态文件

5. ✅ **Docker 镜像构建**
   - 停止旧容器
   - 删除旧容器
   - 构建所有镜像
   - 显示构建进度

6. ✅ **Docker 容器部署**
   - 启动所有容器
   - 配置网络连接
   - 映射端口
   - 后台运行

7. ✅ **等待服务启动**
   - 等待 60 秒
   - 让服务完全启动

8. ✅ **显示服务状态**
   - 列出所有容器
   - 显示运行状态
   - 显示端口映射

### 第四步：访问服务

部署完成后，脚本会显示所有服务的访问地址：

```
服务访问地址：
  Gateway:   http://localhost:8080
  Auth:      http://localhost:9200
  System:    http://localhost:9201
  File:      http://localhost:9202
  Job:       http://localhost:9204
  Open:      http://localhost:9205
  Tenant:     http://localhost:9206
  Workflow:   http://localhost:9207
  Monitor:    http://localhost:9208
  Frontend:  http://localhost
  Nacos:     http://localhost:8848/nacos
```

## 常见问题

### Q1: Java 版本不正确

**错误信息**：
```
错误：Java 未找到或版本不正确
```

**解决方法**：
1. 确认 JDK 21 已安装：`D:\Program Files\Eclipse Adoptium\jdk-21.0.1-hotspot`
2. 运行：`java -version` 验证版本
3. 检查环境变量：`echo %JAVA_HOME%`

### Q2: Maven 编译失败

**错误信息**：
```
Maven 编译失败
```

**可能原因**：
1. 依赖下载失败
2. 编译错误
3. 网络问题

**解决方法**：
1. 检查网络连接
2. 查看编译日志
3. 重新运行脚本
4. 手动编译排查问题

### Q3: NPM 安装失败

**错误信息**：
```
NPM 安装失败
```

**解决方法**：
1. 检查网络连接
2. 清除 NPM 缓存：`npm cache clean --force`
3. 使用淘宝镜像：`npm config set registry https://registry.npmmirror.com`

### Q4: Docker 镜像构建失败

**错误信息**：
```
Docker 镜像构建失败
```

**可能原因**：
1. JAR 文件不存在
2. Dockerfile 配置错误
3. 网络问题

**解决方法**：
1. 检查 JAR 文件是否生成
2. 查看 Dockerfile 配置
3. 查看 Docker 构建日志

### Q5: 容器启动失败

**错误信息**：
```
容器启动失败
```

**可能原因**：
1. 端口被占用
2. 网络配置错误
3. 资源不足

**解决方法**：
1. 检查端口占用：`netstat -ano | findstr "8080"`
2. 查看容器日志：`docker logs han-gateway`
3. 检查 Docker 网络：`docker network ls`

### Q6: 服务无法访问

**错误信息**：
服务启动但无法访问

**解决方法**：
1. 检查防火墙设置
2. 查看容器日志：`docker logs han-gateway`
3. 检查容器状态：`docker ps`
4. 重启容器：`docker restart han-gateway`

## 手动执行（备选方案）

如果脚本无法运行，可以手动执行各步骤：

### 手动编译后端

```cmd
set JAVA_HOME=D:\Program Files\Java\jdk-21.0.10
set MAVEN_HOME=D:\Program Files\apache-maven-3.9.12
set PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%
cd /d D:\code\Han
"%MAVEN_HOME%\bin\mvn.cmd" clean package -DskipTests -B -pl han-gateway,han-auth,han-modules/han-system,han-modules/han-job -am
```

### 手动编译前端

```cmd
cd /d D:\code\Han\han-ui
npm install
npm run build
```

### 手动构建 Docker 镜像

```cmd
cd /d D:\code\Han
docker build -t han-gateway:latest -f han-gateway/Dockerfile .
docker build -t han-auth:latest -f han-auth/Dockerfile .
docker build -t han-system:latest -f han-modules/han-system/Dockerfile .
docker build -t han-job:latest -f han-modules/han-job/Dockerfile .
docker build -t han-ui:latest -f han-ui/Dockerfile .
```

### 手动启动容器

```cmd
docker run -d --name han-gateway --network han-network -p 8080:8080 han-gateway:latest
docker run -d --name han-auth --network han-network -p 9200:9200 han-auth:latest
docker run -d --name han-system --network han-network -p 9201:9201 han-system:latest
docker run -d --name han-job --network han-network -p 9204:9204 han-job:latest
docker run -d --name han-ui --network han-network -p 80:80 han-ui:latest
```

## 服务管理命令

### 查看所有容器

```cmd
docker ps
```

### 查看容器日志

```cmd
docker logs -f han-gateway
docker logs -f han-auth
docker logs -f han-system
docker logs -f han-job
docker logs -f han-ui
```

### 停止容器

```cmd
docker stop han-gateway han-auth han-system han-job han-ui
```

### 重启容器

```cmd
docker restart han-gateway
docker restart han-auth
docker restart han-system
docker restart han-job
docker restart han-ui
```

### 删除容器

```cmd
docker stop han-gateway han-auth han-system han-job han-ui
docker rm han-gateway han-auth han-system han-job han-ui
```

## 下一步

1. **双击运行**：`D:\code\Han\full-build-deploy.bat`
2. **选择环境**：根据需要选择 1/2/3
3. **等待完成**：预计 5-10 分钟
4. **访问服务**：http://localhost
5. **测试登录**：使用默认账号登录

## 注意事项

1. **首次运行**：需要下载依赖，时间较长
2. **网络要求**：确保网络连接正常
3. **磁盘空间**：确保有足够磁盘空间（建议 5GB+）
4. **端口占用**：确保 80、8080、9200-9208 端口未被占用
5. **Docker 状态**：确保 Docker Desktop 正在运行
6. **Java 版本**：确保使用 JDK 21

## 技术支持

如遇问题，请查看：
1. 脚本输出的错误信息
2. Docker 容器日志
3. 项目文档：`.windsurf/rules/project_rules.md`
4. 编译日志：`han-gateway/target/` 目录下的日志
