#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

ENV_FILE="${ENV_FILE:-}"

if [[ -z "${ENV_FILE}" ]]; then
  if [[ -f ".env.local" ]]; then
    ENV_FILE=".env.local"
  elif [[ -f ".env" ]]; then
    ENV_FILE=".env"
  fi
fi

if [[ -n "${ENV_FILE}" ]]; then
  if [[ ! -f "${ENV_FILE}" ]]; then
    echo "Arquivo de ambiente não encontrado: ${ENV_FILE}" >&2
    exit 1
  fi
  set -a
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
  set +a
  echo "Usando variáveis de: ${ENV_FILE}"
else
  echo "Nenhum .env encontrado (.env.local ou .env)."
fi

exec ./mvnw -q -Dspring-boot.run.profiles=local spring-boot:run "$@"
