#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
config_file="$repo_root/.cloudflared/config.yml"

if ! command -v cloudflared >/dev/null 2>&1; then
  echo "cloudflared is not installed or not on PATH" >&2
  exit 1
fi

if [[ ! -f "$config_file" ]]; then
  echo "Missing $config_file" >&2
  echo "Copy .cloudflared/config.yml.example to .cloudflared/config.yml and update it for this machine." >&2
  exit 1
fi

exec cloudflared tunnel --config "$config_file" run