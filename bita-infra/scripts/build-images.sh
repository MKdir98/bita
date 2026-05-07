#!/bin/bash
#
# BITA - Docker Image Build Script
# Builds Docker images for BITA services
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${SCRIPT_DIR}/../.."
REGISTRY="${REGISTRY:-localhost:5000}"
VERSION="${VERSION:-latest}"
MINIKUBE_PROFILE="${MINIKUBE_PROFILE:-bita}"

# Build ESM Backend
build_esm_backend() {
    log_info "Building ESM Backend..."
    
    cd "${PROJECT_ROOT}"
    
    # Build with Maven
    ./mvnw clean package -pl bita-esm-backend -am -DskipTests
    
    # Build Docker image
    docker build \
        -t "${REGISTRY}/bita-esm-backend:${VERSION}" \
        -f bita-esm-backend/Dockerfile \
        bita-esm-backend/
    
    log_info "ESM Backend image built: ${REGISTRY}/bita-esm-backend:${VERSION}"
}

# Build ESB Core
build_esb_core() {
    log_info "Building ESB Core..."
    
    cd "${PROJECT_ROOT}"
    
    # Build with Maven
    ./mvnw clean package -pl bita-esb-core -am -DskipTests
    
    # Build Docker image
    docker build \
        -t "${REGISTRY}/bita-esb-core:${VERSION}" \
        -f bita-esb-core/Dockerfile \
        bita-esb-core/
    
    log_info "ESB Core image built: ${REGISTRY}/bita-esb-core:${VERSION}"
}

# Build ESM Frontend
build_esm_frontend() {
    log_info "Building ESM Frontend..."
    
    cd "${PROJECT_ROOT}/bita-esm-frontend"
    
    # Install dependencies
    npm ci
    
    # Build
    npm run build
    
    # Build Docker image
    docker build \
        -t "${REGISTRY}/bita-esm-frontend:${VERSION}" \
        -f Dockerfile \
        .
    
    log_info "ESM Frontend image built: ${REGISTRY}/bita-esm-frontend:${VERSION}"
}

# Push to registry
push_images() {
    log_info "Pushing images to registry..."
    
    docker push "${REGISTRY}/bita-esm-backend:${VERSION}"
    docker push "${REGISTRY}/bita-esb-core:${VERSION}"
    docker push "${REGISTRY}/bita-esm-frontend:${VERSION}"
    
    log_info "Images pushed successfully."
}

# Setup Minikube Docker environment
setup_minikube_docker() {
    if minikube status -p "${MINIKUBE_PROFILE}" &> /dev/null; then
        log_info "Using Minikube Docker daemon..."
        eval $(minikube -p "${MINIKUBE_PROFILE}" docker-env)
    else
        log_warn "Minikube not running. Using local Docker daemon."
    fi
}

# Print usage
usage() {
    echo "Usage: $0 [OPTIONS] [COMPONENT]"
    echo ""
    echo "Components:"
    echo "  all         Build all components (default)"
    echo "  backend     Build ESM Backend only"
    echo "  esb         Build ESB Core only"
    echo "  frontend    Build ESM Frontend only"
    echo ""
    echo "Options:"
    echo "  --push      Push images to registry after build"
    echo "  --minikube  Use Minikube Docker daemon"
    echo "  --version   Image version tag (default: latest)"
    echo "  --registry  Docker registry (default: localhost:5000)"
    echo "  --help      Show this help"
    echo ""
}

# Parse arguments
PUSH=false
USE_MINIKUBE=false
COMPONENT="all"

while [[ $# -gt 0 ]]; do
    case $1 in
        --push)
            PUSH=true
            shift
            ;;
        --minikube)
            USE_MINIKUBE=true
            shift
            ;;
        --version)
            VERSION="$2"
            shift 2
            ;;
        --registry)
            REGISTRY="$2"
            shift 2
            ;;
        --help)
            usage
            exit 0
            ;;
        all|backend|esb|frontend)
            COMPONENT="$1"
            shift
            ;;
        *)
            log_error "Unknown option: $1"
            usage
            exit 1
            ;;
    esac
done

# Main
main() {
    echo "========================================"
    echo "  BITA - Docker Image Build Script"
    echo "========================================"
    echo ""
    
    if [ "${USE_MINIKUBE}" = true ]; then
        setup_minikube_docker
    fi
    
    case "${COMPONENT}" in
        all)
            build_esm_backend
            build_esb_core
            build_esm_frontend
            ;;
        backend)
            build_esm_backend
            ;;
        esb)
            build_esb_core
            ;;
        frontend)
            build_esm_frontend
            ;;
    esac
    
    if [ "${PUSH}" = true ]; then
        push_images
    fi
    
    log_info "Build complete!"
}

main
