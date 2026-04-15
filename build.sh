#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REGISTRY="registry.cn-hangzhou.aliyuncs.com/xzy0112"
STAGING_DIR="${ROOT_DIR}/.docker-staging"

PUSH=false
ALL=false
NO_BUILD=false
SERVICES=()

declare -A MODULE_MAP=(
  [gateway]="han-gateway"
  [auth]="han-auth"
  [system]="han-modules/han-system"
  [tenant]="han-modules/han-tenant"
  [job]="han-modules/han-job"
  [open]="han-modules/han-open"
  [file]="han-modules/han-file"
  [ai]="han-modules/han-ai"
  [gen]="han-modules/han-gen"
  [workflow]="han-modules/han-workflow"
  [monitor]="han-visual/han-monitor"
  [ui]="han-ui"
)

declare -A ARTIFACT_MAP=(
  [gateway]="han-gateway"
  [auth]="han-auth"
  [system]="han-system"
  [tenant]="han-tenant"
  [job]="han-job"
  [open]="han-open"
  [file]="han-file"
  [ai]="han-ai"
  [gen]="han-gen"
  [workflow]="han-workflow"
  [monitor]="han-monitor"
)

declare -A DOCKERFILE_DIR_MAP=(
  [gateway]="han-gateway"
  [auth]="han-auth"
  [system]="han-modules/han-system"
  [tenant]="han-modules/han-tenant"
  [job]="han-modules/han-job"
  [open]="han-modules/han-open"
  [file]="han-modules/han-file"
  [ai]="han-modules/han-ai"
  [gen]="han-modules/han-gen"
  [workflow]="han-modules/han-workflow"
  [monitor]="han-visual/han-monitor"
  [ui]="han-ui"
)

CORE_SERVICES=(gateway auth system tenant job open)
ALL_SERVICES=(ai auth file gateway gen job monitor open system tenant ui workflow)

usage() {
  cat <<'EOF'
Usage: ./build.sh [service ...] [--all] [--push] [--no-build]

Services:
  gateway auth system tenant job open file ai gen workflow monitor ui

Examples:
  ./build.sh system workflow
  ./build.sh ui
  ./build.sh --all --push
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --push)
      PUSH=true
      ;;
    --all)
      ALL=true
      ;;
    --no-build)
      NO_BUILD=true
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      SERVICES+=("$1")
      ;;
  esac
  shift
done

if [[ "${ALL}" == true ]]; then
  SERVICES=("${ALL_SERVICES[@]}")
elif [[ "${#SERVICES[@]}" -eq 0 ]]; then
  SERVICES=("${CORE_SERVICES[@]}")
fi

for svc in "${SERVICES[@]}"; do
  if [[ -z "${MODULE_MAP[$svc]:-}" ]]; then
    echo "ERROR: unknown service '${svc}'" >&2
    usage
    exit 1
  fi
done

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: docker is required" >&2
  exit 1
fi

JAVA_SERVICES=()
for svc in "${SERVICES[@]}"; do
  if [[ "${svc}" != "ui" ]]; then
    JAVA_SERVICES+=("${svc}")
  fi
done

run_maven() {
  local pl_list="$1"
  if command -v mvn >/dev/null 2>&1; then
    mvn clean package -DskipTests -pl "${pl_list}" -am -q
    return
  fi

  local uid gid
  uid="$(id -u)"
  gid="$(id -g)"
  mkdir -p "${ROOT_DIR}/.m2"
  docker run --rm \
    -u "${uid}:${gid}" \
    -v "${ROOT_DIR}:/workspace" \
    -v "${ROOT_DIR}/.m2:/var/maven/.m2" \
    -w /workspace \
    -e MAVEN_CONFIG=/var/maven/.m2 \
    maven:3.9.9-eclipse-temurin-21 \
    mvn clean package -DskipTests -pl "${pl_list}" -am -q
}

prepare_staging_dir() {
  rm -rf "${STAGING_DIR}"
  mkdir -p "${STAGING_DIR}"
}

copy_jars_to_staging() {
  for svc in "${JAVA_SERVICES[@]}"; do
    local module_dir artifact_name jar_src jar_dst
    module_dir="${MODULE_MAP[$svc]}"
    artifact_name="${ARTIFACT_MAP[$svc]}"
    jar_src="${ROOT_DIR}/${module_dir}/target/${artifact_name}.jar"
    jar_dst="${STAGING_DIR}/${artifact_name}.jar"
    if [[ ! -f "${jar_src}" ]]; then
      echo "ERROR: missing jar ${jar_src}" >&2
      exit 1
    fi
    cp "${jar_src}" "${jar_dst}"
    printf '  staged %s (%s MB)\n' "${artifact_name}.jar" "$(du -m "${jar_dst}" | cut -f1)"
  done
}

if [[ "${NO_BUILD}" == false && "${#JAVA_SERVICES[@]}" -gt 0 ]]; then
  echo "==> Maven package"
  pl_list="$(IFS=,; echo "${JAVA_SERVICES[*]/#/${ROOT_DIR}/}")"
  pl_list=""
  for svc in "${JAVA_SERVICES[@]}"; do
    if [[ -n "${pl_list}" ]]; then
      pl_list+=","
    fi
    pl_list+="${MODULE_MAP[$svc]}"
  done
  run_maven "${pl_list}"
  prepare_staging_dir
  copy_jars_to_staging
fi

BUILT_IMAGES=()

for svc in "${SERVICES[@]}"; do
  context_dir="${ROOT_DIR}/${DOCKERFILE_DIR_MAP[$svc]}"
  dockerfile_path="${context_dir}/Dockerfile"
  if [[ ! -f "${dockerfile_path}" ]]; then
    echo "ERROR: missing Dockerfile ${dockerfile_path}" >&2
    exit 1
  fi

  if [[ "${svc}" == "ui" ]]; then
    image_name="han-ui:local"
  else
    artifact_name="${ARTIFACT_MAP[$svc]}"
    jar_staged="${STAGING_DIR}/${artifact_name}.jar"
    jar_target_dir="${context_dir}/target"
    mkdir -p "${jar_target_dir}"
    if [[ "${NO_BUILD}" == false ]]; then
      cp "${jar_staged}" "${jar_target_dir}/${artifact_name}.jar"
    elif [[ ! -f "${jar_target_dir}/${artifact_name}.jar" ]]; then
      echo "ERROR: ${jar_target_dir}/${artifact_name}.jar not found; rerun without --no-build" >&2
      exit 1
    fi
    image_name="${REGISTRY}/han-${svc}:latest"
  fi

  echo "==> Docker build ${image_name}"
  docker build --no-cache -t "${image_name}" -f "${dockerfile_path}" "${context_dir}"
  BUILT_IMAGES+=("${image_name}")
done

if [[ "${PUSH}" == true ]]; then
  for image_name in "${BUILT_IMAGES[@]}"; do
    if [[ "${image_name}" == han-ui:* ]]; then
      echo "skip push for ${image_name}"
      continue
    fi
    echo "==> Docker push ${image_name}"
    docker push "${image_name}"
  done
fi

rm -rf "${STAGING_DIR}"
echo "Build complete: ${SERVICES[*]}"
