#!/usr/bin/env bash
# ==============================================================================
# Switch Active Team Configuration in Harness
# Usage:
#   bash configs/teams/switch-team.sh [team3|teamA|team2|team4|<custom>]
# ==============================================================================
set -e

TEAM="${1:-team3}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CONFIG_FILE="${REPO_ROOT}/configs/teams/${TEAM}.properties"

if [ ! -f "${CONFIG_FILE}" ]; then
  echo "Error: Team config file not found: ${CONFIG_FILE}"
  echo "Available team configs in configs/teams/:"
  ls -1 "${REPO_ROOT}/configs/teams/"*.properties | xargs -n1 basename | sed 's/\.properties$//'
  exit 1
fi

echo "[switch-team] Activating team configuration: ${TEAM}"
cp "${CONFIG_FILE}" "${REPO_ROOT}/th_src/harness.properties"
cp "${CONFIG_FILE}" "${REPO_ROOT}/web/harness.properties"

echo "[switch-team] Team configuration '${TEAM}' activated successfully for simulation!"
