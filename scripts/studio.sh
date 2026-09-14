#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

command_name="${1:-help}"
if [[ $# -gt 0 ]]; then
  shift
fi

local_image="${STUDIO_IMAGE_LOCAL:-environment-studio:dev}"
ui_image="${STUDIO_UI_IMAGE_LOCAL:-environment-studio-ui:dev}"
host_port="${STUDIO_HOST_PORT:-18181}"
hosted_env_file="${STUDIO_ENV_FILE:-deploy/hosted.env}"
compose_project="${STUDIO_COMPOSE_PROJECT:-environment-studio}"

usage() {
  cat <<'USAGE'
Usage: scripts/studio.sh COMMAND

Common commands:
  preflight        Run the frontend Node/version preflight.
  check-ui         Run frontend preflight, static check, tests and production build.
  image            Build the runtime Docker image. Defaults to environment-studio:dev.
  smoke            Run the existing hardened container smoke against the local image.
  package          Build the runtime image and run the smoke check.
  demo             Run the local synthetic demo on http://localhost:${STUDIO_HOST_PORT:-18181}.

Hosted PostgreSQL 16.11 commands:
  init-hosted-env  Copy deploy/hosted.env.example to deploy/hosted.env if missing.
  hosted-config    Validate the hosted Compose configuration without starting it.
  hosted-prepare-workspace
                   Create/chown/chmod the private workspace from the hosted env file.
  hosted-init      Initialize the private schema3 workspace volume.
  hosted-up        Start the hosted local-operator service.
  hosted-logs      Follow hosted service logs.
  hosted-down      Stop the hosted service without deleting workspace data.

Useful environment overrides:
  STUDIO_IMAGE_LOCAL       Local image tag for image/smoke/package/demo.
  STUDIO_IMAGE             Hosted image reference. Defaults to STUDIO_IMAGE_LOCAL in hosted commands.
  STUDIO_HOST_PORT         Loopback host port. Defaults to 18181.
  STUDIO_ENV_FILE          Hosted env file. Defaults to deploy/hosted.env.
  STUDIO_COMPOSE_PROJECT   Compose project name. Defaults to environment-studio.
USAGE
}

source_revision() {
  git rev-parse HEAD 2>/dev/null || printf 'unknown'
}

compose_base() {
  if ! command -v docker >/dev/null 2>&1; then
    echo "Docker is required for this command." >&2
    exit 127
  fi
  docker compose version >/dev/null
}

require_hosted_env() {
  if [[ ! -f "$hosted_env_file" ]]; then
    cat >&2 <<EOF_MISSING
Missing hosted env file: $hosted_env_file
Create it with:
  scripts/studio.sh init-hosted-env
Then edit the placeholders before running hosted commands.
EOF_MISSING
    exit 2
  fi
}

dotenv_value() {
  python3 - "$hosted_env_file" "$1" <<'PY_DOTENV'
from pathlib import Path
import sys

path = Path(sys.argv[1])
key = sys.argv[2]
value = ""
for line in path.read_text().splitlines():
    stripped = line.strip()
    if not stripped or stripped.startswith("#") or "=" not in line:
        continue
    current_key, current_value = line.split("=", 1)
    if current_key.strip() != key:
        continue
    current_value = current_value.strip()
    if len(current_value) >= 2 and current_value[0] == current_value[-1] and current_value[0] in "'\"":
        current_value = current_value[1:-1]
    value = current_value
print(value)
PY_DOTENV
}

hosted_compose() {
  compose_base
  require_hosted_env
  if [[ -n "${STUDIO_IMAGE:-}" || -n "$(dotenv_value STUDIO_IMAGE)" ]]; then
    docker compose --project-name "$compose_project" --env-file "$hosted_env_file" -f deploy/compose.yaml "$@"
  else
    STUDIO_IMAGE="$local_image" docker compose --project-name "$compose_project" --env-file "$hosted_env_file" -f deploy/compose.yaml "$@"
  fi
}

prepare_hosted_workspace() {
  require_hosted_env
  local workspace_path
  workspace_path="$(dotenv_value STUDIO_WORKSPACE_HOST_PATH)"
  if [[ -z "$workspace_path" || "$workspace_path" == *REPLACE* ]]; then
    echo "Set STUDIO_WORKSPACE_HOST_PATH in $hosted_env_file before preparing the workspace." >&2
    exit 2
  fi
  if [[ "$workspace_path" != /* ]]; then
    echo "STUDIO_WORKSPACE_HOST_PATH must be an absolute host path: $workspace_path" >&2
    exit 2
  fi
  mkdir -p "$workspace_path"
  local owner
  owner="$(stat -c '%u:%g' "$workspace_path")"
  if [[ "$owner" != "10001:10001" ]]; then
    if [[ "$(id -u)" == "0" ]]; then
      chown 10001:10001 "$workspace_path"
    else
      sudo chown 10001:10001 "$workspace_path"
    fi
  fi
  chmod 0700 "$workspace_path"
  echo "Prepared hosted workspace: $workspace_path"
}

case "$command_name" in
  help|-h|--help)
    usage
    ;;
  preflight)
    npm run preflight --prefix frontend
    ;;
  check-ui)
    npm run preflight --prefix frontend
    npm run check --prefix frontend
    npm test --prefix frontend
    npm run build --prefix frontend
    ;;
  ui)
    docker build --target ui -t "$ui_image" .
    ;;
  image|build)
    docker build --target runtime --build-arg "SOURCE_REVISION=$(source_revision)" -t "$local_image" .
    ;;
  smoke)
    bash scripts/container_smoke.sh "$local_image"
    ;;
  package)
    "$repo_root/scripts/studio.sh" image
    "$repo_root/scripts/studio.sh" smoke
    ;;
  demo)
    compose_base
    STUDIO_IMAGE="$local_image" STUDIO_HOST_PORT="$host_port" \
      docker compose --project-name "${STUDIO_DEMO_COMPOSE_PROJECT:-environment-studio-demo}" -f deploy/compose.demo.yaml up --remove-orphans environment-studio-demo
    ;;
  init-hosted-env)
    if [[ -e "$hosted_env_file" ]]; then
      echo "Hosted env already exists: $hosted_env_file"
      exit 0
    fi
    mkdir -p "$(dirname "$hosted_env_file")"
    cp deploy/hosted.env.example "$hosted_env_file"
    chmod 0600 "$hosted_env_file"
    cat <<EOF_CREATED
Created $hosted_env_file with mode 0600.
Edit the image, local-operator password, workspace path, PostgreSQL host/trust/identity values and public origin before hosted-up.
EOF_CREATED
    ;;
  hosted-config)
    hosted_compose config
    ;;
  hosted-prepare-workspace)
    prepare_hosted_workspace
    ;;
  hosted-init)
    hosted_compose --profile init run --rm environment-studio-workspace-init
    ;;
  hosted-up)
    hosted_compose up -d environment-studio
    ;;
  hosted-logs)
    hosted_compose logs -f environment-studio
    ;;
  hosted-down)
    hosted_compose stop environment-studio
    ;;
  *)
    echo "Unknown command: $command_name" >&2
    usage >&2
    exit 2
    ;;
esac
