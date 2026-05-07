#!/bin/bash
#
# BITA - Local Development Startup Script
# This script starts all services for local development
#

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step() { echo -e "${BLUE}[STEP]${NC} $1"; }

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${SCRIPT_DIR}/../.."

# Check prerequisites
check_prerequisites() {
    log_step "Checking prerequisites..."
    
    local missing=()
    
    command -v docker &>/dev/null || missing+=("docker")
    command -v docker-compose &>/dev/null || missing+=("docker-compose")
    command -v java &>/dev/null || missing+=("java")
    command -v mvn &>/dev/null || missing+=("maven")
    command -v node &>/dev/null || missing+=("node")
    command -v npm &>/dev/null || missing+=("npm")
    
    if [ ${#missing[@]} -ne 0 ]; then
        log_error "Missing prerequisites: ${missing[*]}"
        exit 1
    fi
    
    # Check Java version
    java_version=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$java_version" -lt 17 ]; then
        log_error "Java 17+ required, found: $java_version"
        exit 1
    fi
    
    log_info "All prerequisites met"
}

# Start infrastructure
start_infrastructure() {
    log_step "Starting infrastructure services (PostgreSQL, Redis, Kafka, Elasticsearch)..."
    
    cd "${PROJECT_ROOT}/bita-infra/docker"
    
    # Start without Kafka UI on port 8080 (we need it for ESM Backend)
    docker-compose up -d postgres redis zookeeper kafka elasticsearch
    
    # Wait for services to be healthy
    log_info "Waiting for services to be healthy..."
    
    local max_wait=120
    local waited=0
    
    while [ $waited -lt $max_wait ]; do
        if docker-compose ps | grep -q "healthy" && \
           docker exec bita-postgres pg_isready -U bita -d bita_db &>/dev/null && \
           docker exec bita-redis redis-cli ping &>/dev/null; then
            log_info "Infrastructure services are ready"
            break
        fi
        sleep 5
        waited=$((waited + 5))
        echo -n "."
    done
    echo
    
    if [ $waited -ge $max_wait ]; then
        log_error "Timeout waiting for infrastructure services"
        exit 1
    fi
}

# Build Maven projects
build_maven() {
    log_step "Building Maven projects..."
    
    cd "${PROJECT_ROOT}"
    
    # Build common first
    mvn clean install -pl bita-common -DskipTests -q
    log_info "bita-common built"
    
    # Build backend
    mvn clean package -pl bita-esm-backend -am -DskipTests -q
    log_info "bita-esm-backend built"
    
    # Build ESB Core
    mvn clean package -pl bita-esb-core -am -DskipTests -q
    log_info "bita-esb-core built"
}

# Start ESM Backend
start_backend() {
    log_step "Starting ESM Backend on port 8081..."
    
    cd "${PROJECT_ROOT}/bita-esm-backend"
    
    # Create file storage directory
    mkdir -p /tmp/bita/route-files
    
    # Start in background
    nohup java -jar target/*.jar \
        --spring.profiles.active=local \
        --server.port=8081 \
        --app.file.storage-path=/tmp/bita/route-files \
        > /tmp/bita-esm-backend.log 2>&1 &
    
    echo $! > /tmp/bita-esm-backend.pid
    
    # Wait for startup
    log_info "Waiting for ESM Backend to start..."
    local max_wait=60
    local waited=0
    
    while [ $waited -lt $max_wait ]; do
        if curl -s http://localhost:8081/actuator/health &>/dev/null; then
            log_info "ESM Backend is running on http://localhost:8081"
            break
        fi
        sleep 2
        waited=$((waited + 2))
        echo -n "."
    done
    echo
    
    if [ $waited -ge $max_wait ]; then
        log_error "ESM Backend failed to start. Check /tmp/bita-esm-backend.log"
        exit 1
    fi
}

# Install frontend dependencies
install_frontend() {
    log_step "Installing frontend dependencies..."
    
    cd "${PROJECT_ROOT}/bita-esm-frontend"
    
    if [ ! -d "node_modules" ]; then
        npm install
    fi
}

# Start Frontend
start_frontend() {
    log_step "Starting ESM Frontend on port 5173..."
    
    cd "${PROJECT_ROOT}/bita-esm-frontend"
    
    # Create .env.local for API URL
    cat > .env.local << EOF
VITE_API_BASE_URL=http://localhost:8081
EOF
    
    # Start in background
    nohup npm run dev > /tmp/bita-esm-frontend.log 2>&1 &
    echo $! > /tmp/bita-esm-frontend.pid
    
    sleep 3
    log_info "ESM Frontend is running on http://localhost:5173"
}

# Print status
print_status() {
    echo ""
    echo "========================================"
    echo "  BITA Local Development Environment"
    echo "========================================"
    echo ""
    echo "Services:"
    echo "  - PostgreSQL:     localhost:5432  (user: bita, pass: bita123)"
    echo "  - Redis:          localhost:6379"
    echo "  - Kafka:          localhost:9092"
    echo "  - Elasticsearch:  localhost:9200"
    echo ""
    echo "Applications:"
    echo "  - ESM Backend:    http://localhost:8081"
    echo "  - ESM Frontend:   http://localhost:5173"
    echo "  - API Docs:       http://localhost:8081/swagger-ui.html"
    echo ""
    echo "Logs:"
    echo "  - Backend:  tail -f /tmp/bita-esm-backend.log"
    echo "  - Frontend: tail -f /tmp/bita-esm-frontend.log"
    echo ""
    echo "To stop all services: ./stop-local.sh"
    echo ""
}

# Main
main() {
    echo ""
    echo "========================================"
    echo "  BITA - Starting Local Environment"
    echo "========================================"
    echo ""
    
    check_prerequisites
    start_infrastructure
    build_maven
    start_backend
    install_frontend
    start_frontend
    print_status
}

main "$@"
