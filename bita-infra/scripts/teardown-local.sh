#!/bin/bash

# BITA Local Development Teardown Script
# This script stops and cleans up the local development environment

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$(dirname "$SCRIPT_DIR")"

echo "=========================================="
echo "BITA Local Development Teardown"
echo "=========================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Ask for confirmation
read -p "This will stop all BITA services. Continue? (y/N) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Aborted."
    exit 0
fi

# Stop Docker Compose services
stop_docker_services() {
    echo -e "\n${YELLOW}Stopping Docker Compose services...${NC}"
    
    cd "$INFRA_DIR/docker"
    
    if docker compose version &> /dev/null; then
        docker compose down
    else
        docker-compose down
    fi
    
    echo -e "${GREEN}Docker services stopped!${NC}"
}

# Stop Minikube
stop_minikube() {
    echo -e "\n${YELLOW}Stopping Minikube...${NC}"
    
    if minikube status &> /dev/null; then
        minikube stop
        echo -e "${GREEN}Minikube stopped!${NC}"
    else
        echo -e "${YELLOW}Minikube is not running.${NC}"
    fi
}

# Ask about data cleanup
cleanup_data() {
    echo ""
    read -p "Do you also want to remove Docker volumes (ALL DATA WILL BE LOST)? (y/N) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo -e "\n${YELLOW}Removing Docker volumes...${NC}"
        cd "$INFRA_DIR/docker"
        
        if docker compose version &> /dev/null; then
            docker compose down -v
        else
            docker-compose down -v
        fi
        
        echo -e "${GREEN}Docker volumes removed!${NC}"
    fi
    
    read -p "Do you also want to delete Minikube cluster? (y/N) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo -e "\n${YELLOW}Deleting Minikube cluster...${NC}"
        minikube delete
        echo -e "${GREEN}Minikube cluster deleted!${NC}"
    fi
}

# Print summary
print_summary() {
    echo ""
    echo "=========================================="
    echo -e "${GREEN}BITA Local Environment Teardown Complete!${NC}"
    echo "=========================================="
}

# Main execution
main() {
    stop_docker_services
    stop_minikube
    cleanup_data
    print_summary
}

main "$@"
