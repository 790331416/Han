#!/usr/bin/env pwsh
# =============================================
# Han Cloud Docker 镜像构建脚本
# 用途: 安全构建 Docker 镜像，避免 IDE 编译覆盖导致的桩代码污染
# 原理: mvn clean package 后立即将 JAR 复制到临时目录，docker build 从临时目录取 JAR
# =============================================

param(
    [Parameter(Position=0)]
    [string[]]$Services,

    [switch]$Push,
    [switch]$All,
    [switch]$NoBuild  # 跳过 Maven 构建，仅构建 Docker 镜像
)

$ErrorActionPreference = "Stop"
$Registry = "registry.cn-hangzhou.aliyuncs.com/xzy0112"
$RootDir = $PSScriptRoot

# 服务定义: name -> [module_path, jar_name, dockerfile_path]
# 这张表必须与 build.sh 的 MODULE_MAP / ARTIFACT_MAP / DOCKERFILE_DIR_MAP 保持一致，
# 否则同一个 --all 在 Windows 和 Linux 上构建出的镜像集合不同。
$ServiceMap = @{
    "gateway"  = @("han-gateway",                "han-gateway",  "han-gateway")
    "auth"     = @("han-auth",                   "han-auth",     "han-auth")
    "system"   = @("han-modules/han-system",     "han-system",   "han-modules/han-system")
    "tenant"   = @("han-modules/han-tenant",     "han-tenant",   "han-modules/han-tenant")
    "job"      = @("han-modules/han-job",        "han-job",      "han-modules/han-job")
    "open"     = @("han-modules/han-open",       "han-open",     "han-modules/han-open")
    "file"     = @("han-modules/han-file",       "han-file",     "han-modules/han-file")
    "ai"       = @("han-modules/han-ai",         "han-ai",       "han-modules/han-ai")
    "gen"      = @("han-modules/han-gen",        "han-gen",      "han-modules/han-gen")
    "workflow" = @("han-modules/han-workflow",   "han-workflow", "han-modules/han-workflow")
    "monitor"  = @("han-visual/han-monitor",     "han-monitor",  "han-visual/han-monitor")
    # 前端没有 JAR，只走 docker build，也不推送到 Registry
    "ui"       = @("han-ui",                     "",             "han-ui")
}

# 无 Maven 产物的服务（只 docker build，不参与 mvn package / JAR 暂存）
$NonJavaServices = @("ui")

# 默认构建的核心服务
$CoreServices = @("gateway", "auth", "system", "tenant", "job", "open")

if ($All) {
    $Services = $ServiceMap.Keys | Sort-Object
} elseif (-not $Services -or $Services.Count -eq 0) {
    $Services = $CoreServices
}

# 验证服务名
foreach ($svc in $Services) {
    if (-not $ServiceMap.ContainsKey($svc)) {
        Write-Host "ERROR: Unknown service '$svc'. Available: $($ServiceMap.Keys -join ', ')" -ForegroundColor Red
        exit 1
    }
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host " Han Cloud Docker Build" -ForegroundColor Cyan
Write-Host " Services: $($Services -join ', ')" -ForegroundColor Cyan
Write-Host " Push: $Push" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

$JavaServices = @($Services | Where-Object { $NonJavaServices -notcontains $_ })

# Step 1: Maven clean package (一次性构建所有需要的模块)
if (-not $NoBuild -and $JavaServices.Count -gt 0) {
    $plList = ($JavaServices | ForEach-Object { $ServiceMap[$_][0] }) -join ','

    Write-Host "`n[1/3] Maven clean package..." -ForegroundColor Yellow

    # 工具链固定在 D:\source 下。已经配好的环境可用外部 JAVA_HOME / MAVEN_HOME 覆盖。
    if (-not $env:JAVA_HOME) { $env:JAVA_HOME = "D:\source\java\jdk-21.0.10" }
    if (-not (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
        Write-Host "ERROR: JAVA_HOME invalid: $($env:JAVA_HOME)" -ForegroundColor Red
        exit 1
    }
    $mavenHome = if ($env:MAVEN_HOME) { $env:MAVEN_HOME } else { "D:\source\maven\apache-maven-3.9.12" }
    $mvnCmd = Join-Path $mavenHome "bin\mvn.cmd"
    if (-not (Test-Path $mvnCmd)) {
        Write-Host "ERROR: mvn not found: $mvnCmd" -ForegroundColor Red
        exit 1
    }

    & $mvnCmd clean package -DskipTests -pl $plList -am -q
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: Maven build failed!" -ForegroundColor Red
        exit 1
    }
    Write-Host "Maven build OK" -ForegroundColor Green

    # Step 1.5: 立即将 JAR 复制到安全的临时目录，防止 IDE 覆盖
    $stagingDir = Join-Path $RootDir ".docker-staging"
    if (Test-Path $stagingDir) { Remove-Item $stagingDir -Recurse -Force }
    New-Item -ItemType Directory -Path $stagingDir -Force | Out-Null

    foreach ($svc in $JavaServices) {
        $info = $ServiceMap[$svc]
        $jarSrc = Join-Path $RootDir "$($info[0])/target/$($info[1]).jar"
        $jarDst = Join-Path $stagingDir "$($info[1]).jar"
        if (-not (Test-Path $jarSrc)) {
            Write-Host "ERROR: JAR not found: $jarSrc" -ForegroundColor Red
            exit 1
        }
        Copy-Item $jarSrc $jarDst -Force
        Write-Host "  Staged: $($info[1]).jar ($('{0:N1}' -f ((Get-Item $jarDst).Length / 1MB)) MB)" -ForegroundColor Gray
    }
    Write-Host "JARs staged to .docker-staging/" -ForegroundColor Green
}

# Step 2: Docker build
Write-Host "`n[2/3] Docker build..." -ForegroundColor Yellow
$stagingDir = Join-Path $RootDir ".docker-staging"
$built = @()

foreach ($svc in $Services) {
    $info = $ServiceMap[$svc]
    $dockerfilePath = Join-Path $RootDir "$($info[2])/Dockerfile"
    $contextDir = Join-Path $RootDir $info[2]

    if ($NonJavaServices -contains $svc) {
        # 与 build.sh 一致：前端镜像只在本地打 tag，不进 Registry
        $imageName = "han-$($svc):local"
    } else {
        $imageName = "${Registry}/han-$($svc):latest"

        # 将 staging 的 JAR 复制回 target/ 以兼容现有 Dockerfile
        $jarStaged = Join-Path $stagingDir "$($info[1]).jar"
        $targetDir = Join-Path $contextDir "target"
        if (-not (Test-Path $targetDir)) { New-Item -ItemType Directory -Path $targetDir -Force | Out-Null }
        if (-not (Test-Path $jarStaged)) {
            if (-not (Test-Path (Join-Path $targetDir "$($info[1]).jar"))) {
                Write-Host "ERROR: $($info[1]).jar not found; rerun without -NoBuild" -ForegroundColor Red
                exit 1
            }
        } else {
            Copy-Item $jarStaged (Join-Path $targetDir "$($info[1]).jar") -Force
        }
    }

    Write-Host "  Building $imageName ..." -ForegroundColor Gray
    & docker build --no-cache -t $imageName -f $dockerfilePath $contextDir 2>&1 | Select-Object -Last 2
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: Docker build failed for $svc!" -ForegroundColor Red
        exit 1
    }
    $built += $imageName
}
Write-Host "Docker build OK ($($built.Count) images)" -ForegroundColor Green

# Step 3: Docker push (optional)
if ($Push) {
    Write-Host "`n[3/3] Docker push..." -ForegroundColor Yellow
    foreach ($img in $built) {
        if ($img -notlike "$Registry/*") {
            Write-Host "  Skip push for $img" -ForegroundColor Gray
            continue
        }
        Write-Host "  Pushing $img ..." -ForegroundColor Gray
        & docker push $img 2>&1 | Select-Object -Last 1
        if ($LASTEXITCODE -ne 0) {
            Write-Host "ERROR: Docker push failed for $img!" -ForegroundColor Red
            exit 1
        }
    }
    Write-Host "Docker push OK" -ForegroundColor Green
}

# Cleanup staging
if (Test-Path $stagingDir) { Remove-Item $stagingDir -Recurse -Force }

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host " Build complete!" -ForegroundColor Cyan
Write-Host " Built: $($Services -join ', ')" -ForegroundColor Cyan
if ($Push) { Write-Host " Pushed to: $Registry" -ForegroundColor Cyan }
Write-Host "========================================" -ForegroundColor Cyan
