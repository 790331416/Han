param(
    [switch] $Quiet
)

$ErrorActionPreference = 'Stop'

# 工作区规范：所有编译工具、开发工具统一放在 D:\source，禁止调用
# C:\Program Files、D:\Program Files、AppData 下的副本。
# 例外：Git 目前没有 D:\source 下的副本，沿用 D:\Program Files\Git。
$javaHome = 'D:\source\java\jdk-21.0.10'
$mavenHome = 'D:\source\maven\apache-maven-3.9.12'
$nodeHome = 'D:\source\nodejs'
$corepackShimHome = 'D:\source\nodejs\node_modules\corepack\shims'
$gitHome = 'D:\Program Files\Git\cmd'
$ffmpegHome = 'D:\source\sdk\ffmpeg-master-latest-win64-gpl-shared\bin'
$pythonHome = 'D:\source\python\Python-3.12.2'

$requiredPaths = @(
    "$javaHome\bin\java.exe",
    "$mavenHome\bin\mvn.cmd",
    "$nodeHome\node.exe",
    "$corepackShimHome\pnpm.cmd",
    "$gitHome\git.exe",
    "$ffmpegHome\ffmpeg.exe",
    "$pythonHome\python.exe"
)

foreach ($path in $requiredPaths) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Required D-drive tool is missing: $path"
    }
}

$env:JAVA_HOME = $javaHome
$env:MAVEN_HOME = $mavenHome
$env:NODE_HOME = $nodeHome
$env:FFMPEG_HOME = $ffmpegHome
$env:PYTHON_HOME = $pythonHome

# 缓存与中间产物统一落在 D:\source，不写 C 盘用户目录。
$env:MAVEN_OPTS = "-Dmaven.repo.local=D:\source\maven\.m2\repository"
$env:NPM_CONFIG_CACHE = 'D:\source\nvm\npm-cache'
$env:PIP_CACHE_DIR = 'D:\source\python\pip-cache'

# 这些路径一旦混进 PATH 就会把裸命令解析到 D:\source 之外。
$blockedDevPathPatterns = @(
    'C:\Program Files\Common Files\Oracle\Java\javapath',
    'C:\Users\79033\AppData\Roaming\npm',
    'D:\Program Files\Java\jdk-21.0.10\bin',
    'D:\Program Files\apache-maven-3.9.12\bin',
    'D:\Program Files\nodejs',
    'D:\Program Files\Python'
)

$pathParts = ($env:Path -split ';') |
    Where-Object { $_ -and ($blockedDevPathPatterns -notcontains $_.TrimEnd('\')) }

$preferredPathParts = @(
    "$javaHome\bin",
    "$mavenHome\bin",
    $nodeHome,
    $corepackShimHome,
    $gitHome,
    $ffmpegHome,
    $pythonHome,
    "$pythonHome\Scripts"
)

$env:Path = (($preferredPathParts + $pathParts) | Select-Object -Unique) -join ';'

if (-not $Quiet) {
    Write-Host "D-drive development environment is active."
    Write-Host "JAVA_HOME=$env:JAVA_HOME"
    Write-Host "MAVEN_HOME=$env:MAVEN_HOME"
    Write-Host "NODE_HOME=$env:NODE_HOME"
    Write-Host "FFMPEG_HOME=$env:FFMPEG_HOME"
    Write-Host "PYTHON_HOME=$env:PYTHON_HOME"
}
