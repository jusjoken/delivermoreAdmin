#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$repo_root"

if [[ ! -f .env ]]; then
  echo ".env file not found in $repo_root" >&2
  exit 1
fi

set -a
source .env
set +a

exec env SERVER_PORT=8443 ./mvnw -q -DskipTests spring-boot:run