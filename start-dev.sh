#!/bin/bash
#
# BITA - Development Environment Startup Script
# راه‌اندازی محیط توسعه محلی
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
cd "$SCRIPT_DIR"

# Configuration
MAVEN_CMD="${MAVEN_CMD:-mvn}"
# Try to find Maven if not in PATH
if ! command -v $MAVEN_CMD &>/dev/null; then
    if [ -f "$HOME/all/tools/apache-maven-3.9.11/bin/mvn" ]; then
        MAVEN_CMD="$HOME/all/tools/apache-maven-3.9.11/bin/mvn"
    fi
fi

BACKEND_PORT=8081
FRONTEND_PORT=5173
ESB_CORE_PORT=8080

# =============================================================================
# Prerequisites Check
# =============================================================================
check_prerequisites() {
    log_step "Checking prerequisites..."
    
    local missing=()
    
    # Check Docker
    if ! command -v docker &>/dev/null; then
        missing+=("docker")
    fi
    
    # Check Docker Compose
    if ! command -v docker-compose &>/dev/null && ! docker compose version &>/dev/null 2>&1; then
        missing+=("docker-compose")
    fi
    
    # Check Java
    if ! command -v java &>/dev/null; then
        missing+=("java (17+)")
    else
        java_version=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
        if [ "$java_version" -lt 17 ] 2>/dev/null; then
            log_error "Java 17+ required, found version $java_version"
            exit 1
        fi
    fi
    
    # Check Maven
    if ! command -v $MAVEN_CMD &>/dev/null; then
        missing+=("maven")
    fi
    
    # Check Node.js
    if ! command -v node &>/dev/null; then
        missing+=("node")
    fi
    
    # Check npm
    if ! command -v npm &>/dev/null; then
        missing+=("npm")
    fi
    
    if [ ${#missing[@]} -ne 0 ]; then
        log_error "Missing prerequisites: ${missing[*]}"
        log_error "Please install them and try again."
        exit 1
    fi
    
    log_info "All prerequisites met ✓"
}

# =============================================================================
# Docker Infrastructure
# =============================================================================
start_docker_infra() {
    log_step "Starting Docker infrastructure..."
    
    cd "$SCRIPT_DIR/bita-infra/docker"
    
    # Use docker compose v2 if available, fallback to docker-compose
    DOCKER_COMPOSE="docker compose"
    if ! $DOCKER_COMPOSE version &>/dev/null 2>&1; then
        DOCKER_COMPOSE="docker-compose"
    fi
    
    # Check if containers are already running
    if docker ps | grep -q "bita-postgres"; then
        log_info "Docker containers already running"
    else
        # Start core infrastructure
        $DOCKER_COMPOSE up -d postgres redis
        
        log_info "Waiting for services to be ready..."
        
        # Wait for PostgreSQL (container uses internal port 5432, exposed on 5433)
        local max_wait=90
        local waited=0
        while [ $waited -lt $max_wait ]; do
            if docker exec bita-postgres pg_isready -U bita -d bita_db &>/dev/null 2>&1; then
                break
            fi
            sleep 2
            waited=$((waited + 2))
            echo -n "."
        done
        echo
        
        if [ $waited -ge $max_wait ]; then
            log_error "PostgreSQL did not start in time"
            exit 1
        fi
        log_info "PostgreSQL ready ✓"
        
        # Wait for Redis
        waited=0
        while [ $waited -lt 30 ]; do
            if docker exec bita-redis redis-cli ping &>/dev/null; then
                break
            fi
            sleep 2
            waited=$((waited + 2))
        done
        log_info "Redis ready ✓"
        
        # Start Kafka stack (Zookeeper -> Kafka)
        log_info "Starting Kafka stack..."
        $DOCKER_COMPOSE up -d zookeeper
        
        # Wait for Zookeeper
        waited=0
        while [ $waited -lt 30 ]; do
            if docker exec bita-zookeeper bash -c "echo ruok | nc localhost 2181" 2>/dev/null | grep -q "imok"; then
                break
            fi
            sleep 2
            waited=$((waited + 2))
            echo -n "."
        done
        echo
        
        if [ $waited -ge 30 ]; then
            log_warn "Zookeeper may not be ready, continuing anyway..."
        else
            log_info "Zookeeper ready ✓"
        fi
        
        # Start Kafka
        $DOCKER_COMPOSE up -d kafka
        
        # Wait for Kafka to be ready
        log_info "Waiting for Kafka to start..."
        waited=0
        while [ $waited -lt 60 ]; do
            # Check if Kafka is accepting connections on port 9092
            if nc -z localhost 9092 2>/dev/null; then
                # Double check by listing topics (ensures Kafka is fully operational)
                if docker exec bita-kafka kafka-topics.sh --bootstrap-server localhost:9092 --list &>/dev/null 2>&1; then
                    break
                fi
            fi
            sleep 3
            waited=$((waited + 3))
            echo -n "."
        done
        echo
        
        if [ $waited -ge 60 ]; then
            log_warn "Kafka may not be ready. Check: docker logs bita-kafka"
        else
            log_info "Kafka ready ✓ (localhost:9092)"
        fi
        
        # Start Elasticsearch (optional, don't wait)
        log_info "Starting Elasticsearch (optional)..."
        $DOCKER_COMPOSE up -d elasticsearch 2>/dev/null || true
        
        log_info "Docker infrastructure ready ✓"
    fi
    
    cd "$SCRIPT_DIR"
}

# =============================================================================
# Build Projects
# =============================================================================
build_projects() {
    log_step "Building Maven projects..."
    
    cd "$SCRIPT_DIR"
    
    # Check if already built
    if [ -f "bita-esm-backend/target/bita-esm-backend-1.0.0-SNAPSHOT.jar" ]; then
        log_info "Projects already built. Use --rebuild to force rebuild."
    else
        $MAVEN_CMD clean install -DskipTests -q
        log_info "Maven build complete ✓"
    fi
}

# =============================================================================
# Start Backend
# =============================================================================
start_backend() {
    log_step "Starting ESM Backend on port $BACKEND_PORT..."
    
    # Check if already running
    if curl -s "http://localhost:$BACKEND_PORT/actuator/health" &>/dev/null; then
        log_info "Backend already running"
        return
    fi
    
    # Kill any existing process on the port
    if lsof -ti:$BACKEND_PORT &>/dev/null; then
        log_warn "Killing existing process on port $BACKEND_PORT"
        kill $(lsof -ti:$BACKEND_PORT) 2>/dev/null || true
        sleep 2
    fi
    
    # Create necessary directories
    mkdir -p /tmp/bita/route-files
    mkdir -p /tmp/bita/logs
    
    cd "$SCRIPT_DIR/bita-esm-backend"
    
    # Start backend
    nohup java -jar target/bita-esm-backend-1.0.0-SNAPSHOT.jar \
        --spring.profiles.active=local \
        --server.port=$BACKEND_PORT \
        --app.file.storage-path=/tmp/bita/route-files \
        > /tmp/bita/logs/backend.log 2>&1 &
    
    echo $! > /tmp/bita/backend.pid
    
    # Wait for startup
    log_info "Waiting for backend to start..."
    local max_wait=90
    local waited=0
    while [ $waited -lt $max_wait ]; do
        if curl -s "http://localhost:$BACKEND_PORT/actuator/health" &>/dev/null; then
            log_info "Backend started successfully ✓"
            return
        fi
        sleep 2
        waited=$((waited + 2))
        echo -n "."
    done
    echo
    
    log_error "Backend failed to start. Check /tmp/bita/logs/backend.log"
    tail -50 /tmp/bita/logs/backend.log
    exit 1
}

# =============================================================================
# Start ESB Core
# =============================================================================
start_esb_core() {
    log_step "Starting ESB Core on port $ESB_CORE_PORT..."
    
    # ESB Core requires SERVICE_ID
    if [ -z "$ESB_SERVICE_ID" ]; then
        log_warn "ESB_SERVICE_ID not set, using default: 1"
        ESB_SERVICE_ID=1
    fi
    
    # Check if already running
    if curl -s "http://localhost:$ESB_CORE_PORT/health" &>/dev/null; then
        log_info "ESB Core already running"
        return
    fi
    
    # Kill any existing process on the port
    if lsof -ti:$ESB_CORE_PORT &>/dev/null; then
        log_warn "Killing existing process on port $ESB_CORE_PORT"
        kill $(lsof -ti:$ESB_CORE_PORT) 2>/dev/null || true
        sleep 2
    fi
    
    cd "$SCRIPT_DIR/bita-esb-core"
    
    # Find the JAR file
    JAR_FILE=$(find target -name "bita-esb-core-*.jar" -not -name "*-sources.jar" 2>/dev/null | head -1)
    if [ -z "$JAR_FILE" ]; then
        log_error "ESB Core JAR not found. Run build first."
        return 1
    fi
    
    # Start ESB Core with environment variables
    SERVICE_ID=$ESB_SERVICE_ID \
    HTTP_PORT=$ESB_CORE_PORT \
    ESM_BASE_URL="http://localhost:$BACKEND_PORT" \
    KAFKA_BOOTSTRAP_SERVERS="localhost:9092" \
    REDIS_HOST="localhost" \
    REDIS_PORT="6379" \
    ELASTICSEARCH_URL="http://localhost:9200" \
    nohup java -jar "$JAR_FILE" > /tmp/bita/logs/esb-core.log 2>&1 &
    
    echo $! > /tmp/bita/esb-core.pid
    
    # Wait for startup
    log_info "Waiting for ESB Core to start (SERVICE_ID=$ESB_SERVICE_ID)..."
    local max_wait=30
    local waited=0
    while [ $waited -lt $max_wait ]; do
        if curl -s "http://localhost:$ESB_CORE_PORT/health" &>/dev/null; then
            log_info "ESB Core started successfully ✓"
            return
        fi
        sleep 2
        waited=$((waited + 2))
        echo -n "."
    done
    echo
    
    log_warn "ESB Core may not be ready. Check /tmp/bita/logs/esb-core.log"
}

# =============================================================================
# Start Frontend
# =============================================================================
start_frontend() {
    log_step "Starting ESM Frontend on port $FRONTEND_PORT..."
    
    # Create necessary directories
    mkdir -p /tmp/bita/logs
    
    cd "$SCRIPT_DIR/bita-esm-frontend"
    
    # Install dependencies if needed
    if [ ! -d "node_modules" ]; then
        log_info "Installing npm dependencies..."
        npm install
    fi
    
    # Create .env.local
    cat > .env.local << EOF
VITE_API_BASE_URL=http://localhost:$BACKEND_PORT
EOF
    
    # Check if already running
    if curl -s "http://localhost:$FRONTEND_PORT" &>/dev/null; then
        log_info "Frontend already running"
        return
    fi
    
    # Kill any existing process
    if lsof -ti:$FRONTEND_PORT &>/dev/null; then
        kill $(lsof -ti:$FRONTEND_PORT) 2>/dev/null || true
        sleep 2
    fi
    
    # Start frontend
    nohup npm run dev > /tmp/bita/logs/frontend.log 2>&1 &
    echo $! > /tmp/bita/frontend.pid
    
    sleep 3
    log_info "Frontend started ✓"
}

# =============================================================================
# Print Status
# =============================================================================
print_status() {
    echo ""
    echo "========================================"
    echo "  BITA Development Environment"
    echo "========================================"
    echo ""
    
    # Infrastructure status
    echo "🚀 Infrastructure:"
    if [ "$RUN_INFRA" = true ]; then
        echo "   ├── PostgreSQL:     localhost:5433  (user: bita, pass: bita123)"
        echo "   ├── Redis:          localhost:6379"
        echo "   ├── Kafka:          localhost:9092  (optional)"
        echo "   └── Elasticsearch:  localhost:9200  (optional)"
    else
        echo "   └── ⏭️  Skipped (start with --services=infra)"
    fi
    echo ""
    
    # Backend status
    echo "📦 ESM Backend:"
    if [ "$RUN_BACKEND" = true ]; then
        echo "   ├── Backend API:    http://localhost:$BACKEND_PORT"
        echo "   ├── Swagger UI:     http://localhost:$BACKEND_PORT/swagger-ui.html"
        echo "   ├── Health Check:   http://localhost:$BACKEND_PORT/actuator/health"
        echo "   └── Log:            tail -f /tmp/bita/logs/backend.log"
    else
        echo "   └── ⏭️  Skipped - Run in IntelliJ:"
        echo "         1. Open bita-esm-backend in IntelliJ"
        echo "         2. Run EsmBackendApplication with profile: local"
        echo "         3. Set Active profiles: local"
    fi
    echo ""
    
    # ESB Core status
    echo "🔌 ESB Core:"
    if [ "$RUN_ESB_CORE" = true ]; then
        echo "   ├── ESB Core API:   http://localhost:$ESB_CORE_PORT"
        echo "   ├── Service ID:     ${ESB_SERVICE_ID:-1}"
        echo "   ├── Health Check:   http://localhost:$ESB_CORE_PORT/health"
        echo "   └── Log:            tail -f /tmp/bita/logs/esb-core.log"
    else
        echo "   └── ⏭️  Skipped - Run in IntelliJ:"
        echo "         1. Open bita-esb-core in IntelliJ"
        echo "         2. Run EsbCoreApplication"
        echo "         3. Set env: SERVICE_ID=1, HTTP_PORT=$ESB_CORE_PORT"
    fi
    echo ""
    
    # Frontend status
    echo "🎨 Frontend:"
    if [ "$RUN_FRONTEND" = true ]; then
        echo "   ├── Frontend:       http://localhost:$FRONTEND_PORT"
        echo "   └── Log:            tail -f /tmp/bita/logs/frontend.log"
    else
        echo "   └── ⏭️  Skipped - Run manually:"
        echo "         cd bita-esm-frontend && npm run dev"
    fi
    echo ""
    
    echo "🛑 To stop: ./stop-dev.sh"
    echo ""
}

# =============================================================================
# Print Usage
# =============================================================================
print_usage() {
    echo "Usage: ./start-dev.sh [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --services=LIST      Comma-separated list of services to start"
    echo "                       Available: infra, backend, esb-core, frontend, all"
    echo "                       Default: infra,backend,frontend (esb-core excluded)"
    echo "  --esb-service-id=ID  Service ID for ESB Core (default: 1)"
    echo "  --rebuild            Force rebuild of Maven projects"
    echo "  --help               Show this help message"
    echo ""
    echo "Examples:"
    echo "  ./start-dev.sh                              # Start infra + backend + frontend"
    echo "  ./start-dev.sh --services=all               # Start everything including esb-core"
    echo "  ./start-dev.sh --services=infra             # Only Docker infrastructure"
    echo "  ./start-dev.sh --services=infra,frontend    # For running backend in IntelliJ"
    echo "  ./start-dev.sh --services=esb-core --esb-service-id=5  # ESB Core for service 5"
    echo ""
}

# =============================================================================
# Main
# =============================================================================
main() {
    echo ""
    echo "========================================"
    echo "  BITA - Starting Development Environment"
    echo "========================================"
    echo ""
    
    # Parse arguments
    REBUILD=false
    SERVICES="default"
    ESB_SERVICE_ID=""
    
    for arg in "$@"; do
        case $arg in
            --rebuild)
                REBUILD=true
                ;;
            --services=*)
                SERVICES="${arg#*=}"
                ;;
            --esb-service-id=*)
                ESB_SERVICE_ID="${arg#*=}"
                ;;
            --help)
                print_usage
                exit 0
                ;;
        esac
    done
    
    # Convert 'default' to standard set (without esb-core)
    if [ "$SERVICES" = "default" ]; then
        SERVICES="infra,backend,frontend"
    fi
    
    # Convert 'all' to full list (including esb-core)
    if [ "$SERVICES" = "all" ]; then
        SERVICES="infra,backend,esb-core,frontend"
    fi
    
    # Parse services into array-like check
    RUN_INFRA=false
    RUN_BACKEND=false
    RUN_ESB_CORE=false
    RUN_FRONTEND=false
    
    IFS=',' read -ra SERVICE_ARRAY <<< "$SERVICES"
    for service in "${SERVICE_ARRAY[@]}"; do
        case "$service" in
            infra)
                RUN_INFRA=true
                ;;
            backend)
                RUN_BACKEND=true
                ;;
            esb-core)
                RUN_ESB_CORE=true
                ;;
            frontend)
                RUN_FRONTEND=true
                ;;
            *)
                log_error "Unknown service: $service"
                log_error "Available services: infra, backend, esb-core, frontend, all"
                exit 1
                ;;
        esac
    done
    
    log_info "Selected services: $SERVICES"
    
    if [ "$REBUILD" = true ]; then
        rm -rf bita-esm-backend/target bita-esb-core/target bita-common/target
    fi
    
    check_prerequisites
    
    # Start selected services
    if [ "$RUN_INFRA" = true ]; then
        start_docker_infra
    fi
    
    # Build if any Java service is needed
    if [ "$RUN_BACKEND" = true ] || [ "$RUN_ESB_CORE" = true ]; then
        build_projects
    fi
    
    if [ "$RUN_BACKEND" = true ]; then
        start_backend
    fi
    
    if [ "$RUN_ESB_CORE" = true ]; then
        start_esb_core
    fi
    
    if [ "$RUN_FRONTEND" = true ]; then
        start_frontend
    fi
    
    print_status
}

main "$@"
