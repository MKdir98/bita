#!/bin/bash
#
# BITA - Stop Development Environment
#

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo ""
echo "========================================"
echo "  BITA - Stopping Development Environment"
echo "========================================"
echo ""

# Stop Frontend
if [ -f /tmp/bita/frontend.pid ]; then
    pid=$(cat /tmp/bita/frontend.pid)
    if kill -0 $pid 2>/dev/null; then
        kill $pid 2>/dev/null
        log_info "Stopped Frontend (PID: $pid)"
    fi
    rm -f /tmp/bita/frontend.pid
fi

# Also kill any npm/node processes on frontend port
if lsof -ti:5173 &>/dev/null; then
    kill $(lsof -ti:5173) 2>/dev/null || true
    log_info "Killed process on port 5173"
fi

# Stop Backend
if [ -f /tmp/bita/backend.pid ]; then
    pid=$(cat /tmp/bita/backend.pid)
    if kill -0 $pid 2>/dev/null; then
        kill $pid 2>/dev/null
        log_info "Stopped Backend (PID: $pid)"
    fi
    rm -f /tmp/bita/backend.pid
fi

# Also kill any Java process on backend port
if lsof -ti:8081 &>/dev/null; then
    kill $(lsof -ti:8081) 2>/dev/null || true
    log_info "Killed process on port 8081"
fi

# Stop ESB Core
if [ -f /tmp/bita/esb-core.pid ]; then
    pid=$(cat /tmp/bita/esb-core.pid)
    if kill -0 $pid 2>/dev/null; then
        kill $pid 2>/dev/null
        log_info "Stopped ESB Core (PID: $pid)"
    fi
    rm -f /tmp/bita/esb-core.pid
fi

# Also kill any process on ESB Core port
if lsof -ti:8080 &>/dev/null; then
    kill $(lsof -ti:8080) 2>/dev/null || true
    log_info "Killed process on port 8080"
fi

# Ask about Docker
echo ""
read -p "Stop Docker containers? (y/N) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    cd "$SCRIPT_DIR/bita-infra/docker"
    docker-compose down
    log_info "Stopped Docker containers"
fi

echo ""
log_info "Development environment stopped"
