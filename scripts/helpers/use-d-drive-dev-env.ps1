param(
    [switch] $Quiet
)

$ErrorActionPreference = 'Stop'

$javaHome = 'D:\Program Files\Java\jdk-21.0.10'
$mavenHome = 'D:\Program Files\apache-maven-3.9.12'
$nodeHome = 'D:\Program Files\nodejs'
$corepackShimHome = 'D:\Program Files\nodejs\node_modules\corepack\shims'
$gitHome = 'D:\Program Files\Git\bin'
$ffmpegHome = 'D:\Program Files\ffmpeg-2024-03-07-git-97beb63a66-full_build\bin'
$pythonHome = 'D:\Program Files\Python'

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

$blockedDevPathPatterns = @(
    'C:\Program Files\Common Files\Oracle\Java\javapath',
    'C:\Users\79033\AppData\Roaming\npm'
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
    $pythonHome
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
