param(
    [string] $Tag = '',
    [string[]] $Services = @('ai', 'aivideo', 'ui'),
    [string] $Registry = 'registry.cn-hangzhou.aliyuncs.com/xzy0112',
    [string] $SshTarget = 'ubuntu@124.223.116.125',
    [string] $DeployDir = '/opt/han/deploy/full-app',
    [int] $WaitSeconds = 0,
    [switch] $DryRun,
    [switch] $NoHealthCheck
)

$ErrorActionPreference = 'Stop'

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$envScript = Join-Path $repoRoot 'scripts\helpers\use-d-drive-dev-env.ps1'
if (-not (Test-Path -LiteralPath $envScript)) {
    throw "D-drive environment script is missing: $envScript"
}
. $envScript -Quiet

$serviceMap = @{
    gateway = @{ image = 'han-gateway'; env = 'HAN_GATEWAY_IMAGE' }
    auth    = @{ image = 'han-auth'; env = 'HAN_AUTH_IMAGE' }
    system  = @{ image = 'han-system'; env = 'HAN_SYSTEM_IMAGE' }
    job     = @{ image = 'han-job'; env = 'HAN_JOB_IMAGE' }
    file    = @{ image = 'han-file'; env = 'HAN_FILE_IMAGE' }
    ai      = @{ image = 'han-ai'; env = 'HAN_AI_IMAGE' }
    aivideo = @{ image = 'han-aivideo'; env = 'HAN_AIVIDEO_IMAGE' }
    ui      = @{ image = 'han-ui'; env = 'HAN_UI_IMAGE' }
    tenant  = @{ image = 'han-tenant'; env = 'HAN_TENANT_IMAGE' }
}

if (-not $Tag) {
    $Tag = (& git -C $repoRoot rev-parse --short=7 HEAD).Trim()
}

if ($Tag -notmatch '^[A-Za-z0-9._-]+$') {
    throw "Invalid image tag: $Tag"
}
if ($Registry -notmatch '^[A-Za-z0-9./:_-]+$') {
    throw "Invalid registry: $Registry"
}
if ($DeployDir -notmatch '^/[A-Za-z0-9._/-]+$') {
    throw "Invalid deploy dir: $DeployDir"
}

$expandedServices = @()
foreach ($serviceValue in $Services) {
    foreach ($service in ($serviceValue -split ',')) {
        if ($service.Trim()) {
            $expandedServices += $service.Trim()
        }
    }
}

$normalizedServices = @()
foreach ($service in $expandedServices) {
    $name = $service.Trim().ToLowerInvariant()
    if (-not $serviceMap.ContainsKey($name)) {
        throw "Unsupported service '$service'. Supported: $($serviceMap.Keys -join ', ')"
    }
    if ($normalizedServices -notcontains $name) {
        $normalizedServices += $name
    }
}
if (-not $normalizedServices.Count) {
    throw 'At least one service is required.'
}

$serviceCsv = $normalizedServices -join ','
$wait = [Math]::Max(0, $WaitSeconds)
$healthCheck = if ($NoHealthCheck) { '0' } else { '1' }

Write-Host 'Han/AIVideo ACR deploy'
Write-Host "Repo:      $repoRoot"
Write-Host "Tag:       $Tag"
Write-Host "Services:  $serviceCsv"
Write-Host "Registry:  $Registry"
Write-Host "Target:    $SshTarget"
Write-Host "DeployDir: $DeployDir"
Write-Host 'Build:     GitHub Actions only; this script never builds on Tencent Cloud.'

if ($DryRun) {
    foreach ($service in $normalizedServices) {
        $image = "$Registry/$($serviceMap[$service].image):$Tag"
        Write-Host "DRY-RUN $service -> $($serviceMap[$service].env)=$image"
    }
    exit 0
}

$remoteScript = @'
set -euo pipefail

image_name_for() {
  case "$1" in
    gateway) echo "han-gateway" ;;
    auth) echo "han-auth" ;;
    system) echo "han-system" ;;
    job) echo "han-job" ;;
    file) echo "han-file" ;;
    ai) echo "han-ai" ;;
    aivideo) echo "han-aivideo" ;;
    ui) echo "han-ui" ;;
    tenant) echo "han-tenant" ;;
    *) echo "Unsupported service: $1" >&2; return 1 ;;
  esac
}

env_key_for() {
  case "$1" in
    gateway) echo "HAN_GATEWAY_IMAGE" ;;
    auth) echo "HAN_AUTH_IMAGE" ;;
    system) echo "HAN_SYSTEM_IMAGE" ;;
    job) echo "HAN_JOB_IMAGE" ;;
    file) echo "HAN_FILE_IMAGE" ;;
    ai) echo "HAN_AI_IMAGE" ;;
    aivideo) echo "HAN_AIVIDEO_IMAGE" ;;
    ui) echo "HAN_UI_IMAGE" ;;
    tenant) echo "HAN_TENANT_IMAGE" ;;
    *) echo "Unsupported service: $1" >&2; return 1 ;;
  esac
}

set_env_value() {
  local key="$1"
  local value="$2"
  if grep -q "^${key}=" .env; then
    sed -i "s|^${key}=.*|${key}=${value}|" .env
  else
    printf '\n%s=%s\n' "$key" "$value" >> .env
  fi
}

IFS=',' read -r -a service_array <<< "$SERVICES"
cd "$DEPLOY_DIR"

echo "Remote host: $(hostname)"
echo "Deploy dir: $(pwd)"
echo "Tag: ${TAG}"
echo "Services: ${SERVICES}"
echo "Rule: no Maven package, no Docker build; pull ACR images only."

deadline=$((SECONDS + WAIT_SECONDS))
while true; do
  missing=()
  for service in "${service_array[@]}"; do
    image_name="$(image_name_for "$service")"
    image="${REGISTRY}/${image_name}:${TAG}"
    if ! docker manifest inspect "$image" >/dev/null 2>&1; then
      missing+=("$image")
    fi
  done

  if [ "${#missing[@]}" -eq 0 ]; then
    echo "All remote manifests are available."
    break
  fi

  if [ "$WAIT_SECONDS" -le 0 ] || [ "$SECONDS" -ge "$deadline" ]; then
    echo "Missing remote manifests:" >&2
    printf '  %s\n' "${missing[@]}" >&2
    echo "Fix GitHub Actions / ACR push first; do not build on Tencent Cloud." >&2
    exit 1
  fi

  echo "Waiting for GitHub Actions to push images..."
  printf '  %s\n' "${missing[@]}"
  sleep 15
done

backup=".env.bak-${TAG}-$(date +%Y%m%d%H%M%S)-deploy-script"
cp .env "$backup"
echo "Backed up .env to ${backup}"

for service in "${service_array[@]}"; do
  image_name="$(image_name_for "$service")"
  env_key="$(env_key_for "$service")"
  image="${REGISTRY}/${image_name}:${TAG}"
  set_env_value "$env_key" "$image"
  echo "Updated ${env_key}=${image}"
done

echo "Pulling images..."
docker compose pull "${service_array[@]}"

echo "Starting services..."
docker compose up -d "${service_array[@]}"

echo "Compose status:"
docker compose ps "${service_array[@]}"

if [ "$HEALTH_CHECK" = "1" ]; then
  echo "Checking container health..."
  for service in "${service_array[@]}"; do
    container_id="$(docker compose ps -q "$service")"
    if [ -z "$container_id" ]; then
      echo "No container found for service ${service}" >&2
      exit 1
    fi
    ok="0"
    for i in $(seq 1 30); do
      state="$(docker inspect -f '{{.State.Status}}' "$container_id" 2>/dev/null || true)"
      health="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{end}}' "$container_id" 2>/dev/null || true)"
      if [ "$state" = "running" ] && { [ -z "$health" ] || [ "$health" = "healthy" ]; }; then
        echo "${service}: ${state}${health:+ / $health}"
        ok="1"
        break
      fi
      echo "${service}: waiting, current=${state}${health:+ / $health}"
      sleep 5
    done
    if [ "$ok" != "1" ]; then
      echo "${service}: not healthy after wait" >&2
      docker compose logs --tail=80 "$service" >&2 || true
      exit 1
    fi
  done
fi

echo "Public route smoke check:"
curl -fsS -o /dev/null -w 'https://han.scavengers.cn/ -> HTTP %{http_code}\n' https://han.scavengers.cn/ || true
curl -fsS -o /dev/null -w 'https://han.scavengers.cn/studio/projects -> HTTP %{http_code}\n' https://han.scavengers.cn/studio/projects || true

echo "Deploy finished."
'@

$remoteCommand = "TAG='$Tag' REGISTRY='$Registry' DEPLOY_DIR='$DeployDir' SERVICES='$serviceCsv' WAIT_SECONDS='$wait' HEALTH_CHECK='$healthCheck' bash -s"
$tempRemoteScript = New-TemporaryFile
try {
    # Avoid a BOM on stdin; otherwise remote bash may not enable set -e.
    $utf8NoBom = New-Object System.Text.UTF8Encoding -ArgumentList $false
    [System.IO.File]::WriteAllText($tempRemoteScript.FullName, $remoteScript, $utf8NoBom)
    $process = Start-Process -FilePath 'ssh' `
        -ArgumentList @($SshTarget, $remoteCommand) `
        -NoNewWindow `
        -Wait `
        -PassThru `
        -RedirectStandardInput $tempRemoteScript.FullName
    if ($process.ExitCode -ne 0) {
        throw "Remote deploy failed with exit code $($process.ExitCode)"
    }
}
finally {
    Remove-Item -LiteralPath $tempRemoteScript.FullName -Force -ErrorAction SilentlyContinue
}
