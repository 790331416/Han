# Maven 仓库修复指南

## 问题说明

编译时遇到错误：
```
java.io.IOException: 拒绝访问。
```

**原因**：Maven 仓库目录权限问题

## Maven 仓库配置

**当前配置**：
- **本地仓库**：`D:\mavenJar`
- **镜像仓库**：阿里云 Maven
- **JDK 版本**：21

## 修复步骤

### 步骤 1：检查仓库权限

运行以下脚本：
```powershell
powershell -ExecutionPolicy Bypass -File "D:\code\Han\fix-maven-repo.ps1"
```

这个脚本会：
1. 检查 `D:\mavenJar` 目录是否存在
2. 测试写入权限
3. 如果失败，提供建议

### 步骤 2：清理 Maven 缓存

运行以下脚本：
```powershell
powershell -ExecutionPolicy Bypass -File "D:\code\Han\clear-maven-cache.ps1"
```

这个脚本会：
1. 删除 Spring Framework 缓存
2. 清理跟踪文件
3. 显示清理进度

### 步骤 3：配置 Maven settings.xml

已创建 `settings.xml`，配置：
- 本地仓库：`D:\mavenJar`
- 镜像仓库：阿里云 Maven
- JDK 21 配置

**使用方法**：
```cmd
set MAVEN_OPTS="-s D:\code\Han\settings.xml"
mvn clean package
```

或者在 PowerShell 中：
```powershell
$env:MAVEN_OPTS="-s D:\code\Han\settings.xml"
mvn clean package
```

### 步骤 4：重新编译

清理完成后，重新运行编译：

```powershell
powershell -ExecutionPolicy Bypass -File "D:\code\Han\build-gateway-only.ps1"
```

## 常见问题

### Q1: 拒绝访问错误

**错误信息**：
```
java.io.IOException: 拒绝访问。
```

**解决方法**：
1. 以管理员身份运行 PowerShell
2. 检查杀毒软件设置
3. 检查文件权限
4. 关闭占用文件的程序

### Q2: Maven 无法下载依赖

**解决方法**：
1. 检查网络连接
2. 使用阿里云镜像（已配置）
3. 清理本地仓库缓存

### Q3: 编译失败

**解决方法**：
1. 查看 Maven 日志
2. 检查依赖冲突
3. 更新依赖版本

## 手动清理命令

如果脚本无法运行，可以手动执行：

### 清理 Spring Framework 缓存
```powershell
Remove-Item -Path "D:\mavenJar\org\springframework" -Recurse -Force
```

### 清理跟踪文件
```powershell
Get-ChildItem "D:\mavenJar" -Filter "_remote.repositories" | Remove-Item -Recurse -Force
```

### 清理整个仓库
```powershell
Remove-Item -Path "D:\mavenJar\*" -Recurse -Force
```

## 验证修复

修复后，验证权限：

```powershell
$testFile = "D:\mavenJar\test.txt"
"Test" | Out-File -FilePath $testFile -Force
Remove-Item $testFile -Force
```

如果没有错误，说明权限正常。

## 下一步

1. 运行 `fix-maven-repo.ps1` 检查权限
2. 运行 `clear-maven-cache.ps1` 清理缓存
3. 运行 `build-gateway-only.ps1` 重新编译
4. 如果成功，继续编译其他模块
