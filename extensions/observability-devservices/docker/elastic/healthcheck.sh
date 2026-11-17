#!/usr/bin/env bash
set -euo pipefail

READY_FILE="${READY_FILE:-/tmp/ready}"

if [[ -f "$READY_FILE" ]]; then
    exit 0
fi
exit 1
