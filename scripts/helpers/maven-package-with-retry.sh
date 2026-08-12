#!/usr/bin/env bash
set -euo pipefail

# 打包用途的脚本：默认跳过测试，因为测试门禁由 workflow 里独立的 `test` job
# 承担（见 .github/workflows/full-app-image.yml 与 ai-image.yml），
# 而不是靠每个镜像 job 重复跑一遍完整测试。
# 需要在打包阶段一并跑测试时设置 HAN_MAVEN_SKIP_TESTS=false。
#
# 重试只用来吸收镜像源抖动，不是用来掩盖真实的编译失败：每次失败都会写进
# GitHub Actions 的 job summary，便于统计到底抖了多少次。

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <module-path> [maven-args...]" >&2
  exit 2
fi

module="$1"
shift

skip_tests="${HAN_MAVEN_SKIP_TESTS:-true}"
declare -a test_args=()
if [[ "${skip_tests}" == "true" ]]; then
  test_args+=("-DskipTests")
fi

max_attempts="${HAN_MAVEN_MAX_ATTEMPTS:-3}"
failures=0

note_failure() {
  local attempt="$1"
  echo "Maven package failed for ${module} on attempt ${attempt}; retrying after transient mirror backoff." >&2
  if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    echo "- \`${module}\` maven package retry ${attempt}/${max_attempts}" >>"${GITHUB_STEP_SUMMARY}"
  fi
}

for ((attempt = 1; attempt <= max_attempts; attempt++)); do
  if mvn -B -gs settings.workspace.xml -pl "${module}" -am "${test_args[@]}" package "$@"; then
    if [[ "${failures}" -gt 0 && -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
      echo "- \`${module}\` succeeded after ${failures} transient failure(s)" >>"${GITHUB_STEP_SUMMARY}"
    fi
    exit 0
  fi

  failures=$((failures + 1))
  note_failure "${attempt}"
  sleep $((attempt * 20))
done

# 最后一次兜底保持与前几次完全相同的参数（含 -gs settings.workspace.xml）。
# 换用 runner 默认 settings 会让同一个 job 出现两种依赖解析配置，
# 第四次成功也无法证明前三次的失败是瞬时的。
mvn -B -gs settings.workspace.xml -pl "${module}" -am "${test_args[@]}" package "$@"
