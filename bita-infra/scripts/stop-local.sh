#!/bin/bash
#
# BITA - Stop Local Development Environment
#

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${SCRIPT_DIR}/../.."

echo ""
echo "========================================"
echo "  BITA - Stopping Local Environment"
echo "========================================"
echo ""

# Stop Frontend
if [ -f /tmp/bita-esm-frontend.pid ]; then
    pid=$(cat /tmp/bita-esm-frontend.pid)
    if kill -0 $pid 2>/dev/null; then
        kill $pid
        log_info "Stopped ESM Frontend (PID: $pid)"
    fi
    rm -f /tmp/bita-esm-frontend.pid
fi

# Stop Backend
if [ -f /tmp/bita-esm-backend.pid ]; then
    pid=$(cat /tmp/bita-esm-backend.pid)
    if kill -0 $pid 2>/dev/null; then
        kill $pid
        log_info "Stopped ESM Backend (PID: $pid)"
    fi
    rm -f /tmp/bita-esm-backend.pid
fi

# Stop ESB Core
if [ -f /tmp/bita-esb-core.pid ]; then
    pid=$(cat /tmp/bita-esb-core.pid)
    if kill -0 $pid 2>/dev/null; then
        kill $pid
        log_info "Stopped ESB Core (PID: $pid)"
    fi
    rm -f /tmp/bita-esb-core.pid
fi

# Stop Docker containers
echo ""
read -p "Stop Docker infrastructure? (y/N) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    cd "${PROJECT_ROOT}/bita-infra/docker"
    docker-compose down
    log_info "Stopped Docker infrastructure"
fi

# Clean up logs
read -p "Remove log files? (y/N) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    rm -f /tmp/bita-*.log
    log_info "Cleaned up log files"
fi

echo ""
log_info "Local environment stopped"
