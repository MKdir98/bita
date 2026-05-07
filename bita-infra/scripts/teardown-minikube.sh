#!/bin/bash
#
# BITA - Minikube Teardown Script
# This script removes the Minikube cluster and cleans up resources
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

MINIKUBE_PROFILE="bita"

# Confirm teardown
confirm_teardown() {
    echo ""
    log_warn "This will delete the Minikube cluster '${MINIKUBE_PROFILE}' and all its data!"
    echo ""
    read -p "Are you sure you want to continue? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        log_info "Teardown cancelled."
        exit 0
    fi
}

# Delete cluster
delete_cluster() {
    log_info "Deleting Minikube cluster '${MINIKUBE_PROFILE}'..."
    
    if minikube status -p "${MINIKUBE_PROFILE}" &> /dev/null; then
        minikube delete -p "${MINIKUBE_PROFILE}"
        log_info "Cluster deleted successfully."
    else
        log_warn "Cluster '${MINIKUBE_PROFILE}' does not exist or is not running."
    fi
}

# Cleanup Docker resources (optional)
cleanup_docker() {
    echo ""
    read -p "Do you want to clean up dangling Docker resources? (y/N) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        log_info "Cleaning up Docker resources..."
        
        # Remove dangling images
        docker image prune -f
        
        # Remove unused volumes
        docker volume prune -f
        
        log_info "Docker cleanup complete."
    fi
}

# Remove hosts entries (manual)
remove_hosts_entries() {
    echo ""
    log_info "Remember to remove the following entries from /etc/hosts:"
    echo "  - bita.local"
    echo "  - esm.bita.local"
    echo "  - esb.bita.local"
    echo "  - grafana.bita.local"
    echo ""
}

# Main
main() {
    echo "========================================"
    echo "  BITA - Minikube Teardown Script"
    echo "========================================"
    
    confirm_teardown
    delete_cluster
    cleanup_docker
    remove_hosts_entries
    
    log_info "Teardown complete!"
}

main "$@"
