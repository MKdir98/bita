#!/bin/bash
#
# BITA - Minikube Setup Script
# This script sets up a local Minikube cluster for BITA development
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

# Check prerequisites
check_prerequisites() {
    log_info "Checking prerequisites..."
    
    # Check minikube
    if ! command -v minikube &> /dev/null; then
        log_error "minikube is not installed. Please install it first."
        exit 1
    fi
    
    # Check kubectl
    if ! command -v kubectl &> /dev/null; then
        log_error "kubectl is not installed. Please install it first."
        exit 1
    fi
    
    # Check docker
    if ! command -v docker &> /dev/null; then
        log_error "docker is not installed. Please install it first."
        exit 1
    fi
    
    log_info "All prerequisites are met."
}

# Configuration
MINIKUBE_PROFILE="bita"
MINIKUBE_CPUS="${MINIKUBE_CPUS:-4}"
MINIKUBE_MEMORY="${MINIKUBE_MEMORY:-8192}"
MINIKUBE_DISK="${MINIKUBE_DISK:-40g}"
MINIKUBE_DRIVER="${MINIKUBE_DRIVER:-docker}"
KUBERNETES_VERSION="${KUBERNETES_VERSION:-v1.28.0}"

# Start Minikube
start_minikube() {
    log_info "Starting Minikube cluster '${MINIKUBE_PROFILE}'..."
    
    # Check if cluster already exists
    if minikube status -p "${MINIKUBE_PROFILE}" &> /dev/null; then
        log_warn "Cluster '${MINIKUBE_PROFILE}' already exists."
        read -p "Do you want to delete and recreate it? (y/N) " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            minikube delete -p "${MINIKUBE_PROFILE}"
        else
            log_info "Using existing cluster."
            minikube start -p "${MINIKUBE_PROFILE}"
            return
        fi
    fi
    
    minikube start \
        --profile="${MINIKUBE_PROFILE}" \
        --cpus="${MINIKUBE_CPUS}" \
        --memory="${MINIKUBE_MEMORY}" \
        --disk-size="${MINIKUBE_DISK}" \
        --driver="${MINIKUBE_DRIVER}" \
        --kubernetes-version="${KUBERNETES_VERSION}" \
        --addons=ingress,metrics-server,dashboard
    
    log_info "Minikube cluster started successfully."
}

# Enable addons
enable_addons() {
    log_info "Enabling required addons..."
    
    minikube addons enable ingress -p "${MINIKUBE_PROFILE}"
    minikube addons enable ingress-dns -p "${MINIKUBE_PROFILE}"
    minikube addons enable metrics-server -p "${MINIKUBE_PROFILE}"
    minikube addons enable dashboard -p "${MINIKUBE_PROFILE}"
    minikube addons enable registry -p "${MINIKUBE_PROFILE}"
    
    log_info "Addons enabled successfully."
}

# Setup Docker environment
setup_docker_env() {
    log_info "Setting up Docker environment..."
    
    # Print instructions for using Minikube's Docker daemon
    echo ""
    echo "To use Minikube's Docker daemon, run:"
    echo "  eval \$(minikube -p ${MINIKUBE_PROFILE} docker-env)"
    echo ""
    echo "Add to your shell profile (~/.bashrc or ~/.zshrc):"
    echo "  alias mk='minikube -p ${MINIKUBE_PROFILE}'"
    echo "  alias mkd='eval \$(minikube -p ${MINIKUBE_PROFILE} docker-env)'"
    echo ""
}

# Create namespaces
create_namespaces() {
    log_info "Creating namespaces..."
    
    kubectl create namespace bita --dry-run=client -o yaml | kubectl apply -f -
    kubectl create namespace bita-dev --dry-run=client -o yaml | kubectl apply -f -
    kubectl create namespace bita-test --dry-run=client -o yaml | kubectl apply -f -
    kubectl create namespace monitoring --dry-run=client -o yaml | kubectl apply -f -
    
    log_info "Namespaces created."
}

# Deploy infrastructure services (external services)
deploy_infrastructure() {
    log_info "Deploying infrastructure services..."
    
    # Get script directory
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    INFRA_DIR="${SCRIPT_DIR}/../k8s/infra"
    
    if [ -d "${INFRA_DIR}" ]; then
        kubectl apply -f "${INFRA_DIR}/" --namespace=bita
        log_info "Infrastructure services deployed."
    else
        log_warn "Infrastructure directory not found. Skipping deployment."
        log_warn "You can deploy infrastructure later using Docker Compose."
    fi
}

# Setup local DNS (optional)
setup_local_dns() {
    log_info "Setting up local DNS..."
    
    MINIKUBE_IP=$(minikube ip -p "${MINIKUBE_PROFILE}")
    
    echo ""
    echo "Add the following entries to /etc/hosts:"
    echo "  ${MINIKUBE_IP}  bita.local"
    echo "  ${MINIKUBE_IP}  esm.bita.local"
    echo "  ${MINIKUBE_IP}  esb.bita.local"
    echo "  ${MINIKUBE_IP}  grafana.bita.local"
    echo ""
    echo "Or run (requires sudo):"
    echo "  echo '${MINIKUBE_IP}  bita.local esm.bita.local esb.bita.local grafana.bita.local' | sudo tee -a /etc/hosts"
    echo ""
}

# Print cluster info
print_cluster_info() {
    log_info "Cluster information:"
    echo ""
    echo "Profile: ${MINIKUBE_PROFILE}"
    echo "Kubernetes version: ${KUBERNETES_VERSION}"
    echo "Minikube IP: $(minikube ip -p ${MINIKUBE_PROFILE})"
    echo ""
    echo "Dashboard URL: $(minikube dashboard --url -p ${MINIKUBE_PROFILE} &> /dev/null &)"
    echo "To open dashboard: minikube dashboard -p ${MINIKUBE_PROFILE}"
    echo ""
    echo "Registry: Use 'minikube -p ${MINIKUBE_PROFILE} service registry -n kube-system --url' to get URL"
    echo ""
}

# Main
main() {
    echo "========================================"
    echo "  BITA - Minikube Setup Script"
    echo "========================================"
    echo ""
    
    check_prerequisites
    start_minikube
    enable_addons
    create_namespaces
    setup_docker_env
    setup_local_dns
    print_cluster_info
    
    log_info "Setup complete!"
    echo ""
    echo "Next steps:"
    echo "  1. Configure Docker environment: eval \$(minikube -p ${MINIKUBE_PROFILE} docker-env)"
    echo "  2. Build images: ./build-images.sh"
    echo "  3. Deploy application: kubectl apply -f ../k8s/"
    echo ""
}

main "$@"
