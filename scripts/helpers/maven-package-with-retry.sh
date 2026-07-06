#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <module-path> [maven-args...]" >&2
  exit 2
fi

module="$1"
shift

for attempt in 1 2 3; do
  if mvn -B -gs settings.workspace.xml -pl "${module}" -am -DskipTests package "$@"; then
    exit 0
  fi

  echo "Maven package failed for ${module} on attempt ${attempt}; retrying after transient mirror backoff." >&2
  sleep $((attempt * 20))
done

mvn -B -gs settings.workspace.xml -pl "${module}" -am -DskipTests package "$@"