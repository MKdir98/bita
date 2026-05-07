#!/bin/bash

# BITA Local Development Setup Script
# This script sets up the local development environment with Docker + Minikube

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$(dirname "$SCRIPT_DIR")"
PROJECT_DIR="$(dirname "$INFRA_DIR")"

echo "=========================================="
echo "BITA Local Development Setup"
echo "=========================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check prerequisites
check_prerequisites() {
    echo -e "\n${YELLOW}Checking prerequisites...${NC}"
    
    if ! command -v docker &> /dev/null; then
        echo -e "${RED}Docker is not installed. Please install Docker first.${NC}"
        exit 1
    fi
    
    if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
        echo -e "${RED}Docker Compose is not installed. Please install Docker Compose first.${NC}"
        exit 1
    fi
    
    if ! command -v minikube &> /dev/null; then
        echo -e "${RED}Minikube is not installed. Please install Minikube first.${NC}"
        exit 1
    fi
    
    if ! command -v kubectl &> /dev/null; then
        echo -e "${RED}kubectl is not installed. Please install kubectl first.${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}All prerequisites met!${NC}"
}

# Start Docker Compose services
start_docker_services() {
    echo -e "\n${YELLOW}Starting Docker Compose services...${NC}"
    
    cd "$INFRA_DIR/docker"
    
    # Use docker compose (v2) or docker-compose (v1)
    if docker compose version &> /dev/null; then
        docker compose up -d
    else
        docker-compose up -d
    fi
    
    echo -e "${GREEN}Docker services started!${NC}"
    
    # Wait for services to be healthy
    echo -e "${YELLOW}Waiting for services to be healthy...${NC}"
    sleep 10
    
    # Check PostgreSQL
    until docker exec bita-postgres pg_isready -U bita -d bita_db &> /dev/null; do
        echo "Waiting for PostgreSQL..."
        sleep 2
    done
    echo -e "${GREEN}PostgreSQL is ready!${NC}"
    
    # Check Redis
    until docker exec bita-redis redis-cli ping &> /dev/null; do
        echo "Waiting for Redis..."
        sleep 2
    done
    echo -e "${GREEN}Redis is ready!${NC}"
    
    # Check Kafka
    echo "Waiting for Kafka to be ready (this may take a moment)..."
    sleep 30
    echo -e "${GREEN}Kafka should be ready!${NC}"
}

# Start Minikube
start_minikube() {
    echo -e "\n${YELLOW}Starting Minikube...${NC}"
    
    # Check if Minikube is already running
    if minikube status &> /dev/null; then
        echo -e "${GREEN}Minikube is already running!${NC}"
    else
        minikube start --cpus=4 --memory=8192 --driver=docker
        echo -e "${GREEN}Minikube started!${NC}"
    fi
    
    # Enable addons
    echo -e "${YELLOW}Enabling Minikube addons...${NC}"
    minikube addons enable ingress
    minikube addons enable metrics-server
    
    echo -e "${GREEN}Minikube addons enabled!${NC}"
}

# Configure networking between Docker and Minikube
configure_networking() {
    echo -e "\n${YELLOW}Configuring networking...${NC}"
    
    # Get Docker network gateway
    DOCKER_GATEWAY=$(docker network inspect bita-network -f '{{range .IPAM.Config}}{{.Gateway}}{{end}}' 2>/dev/null || echo "172.17.0.1")
    
    # Get host IP for Minikube to access Docker services
    HOST_IP=$(minikube ssh "cat /etc/resolv.conf" | grep nameserver | awk '{print $2}' | head -1)
    
    echo -e "${GREEN}Docker Gateway: $DOCKER_GATEWAY${NC}"
    echo -e "${GREEN}Host IP from Minikube: $HOST_IP${NC}"
    
    echo ""
    echo "To access Docker services from Minikube pods, use these addresses:"
    echo "  PostgreSQL: host.minikube.internal:5432"
    echo "  Redis: host.minikube.internal:6379"
    echo "  Kafka: host.minikube.internal:9092"
    echo "  Elasticsearch: host.minikube.internal:9200"
}

# Create Kubernetes namespaces
create_namespaces() {
    echo -e "\n${YELLOW}Creating Kubernetes namespaces...${NC}"
    
    kubectl create namespace bita-esm --dry-run=client -o yaml | kubectl apply -f -
    kubectl create namespace bita-esb --dry-run=client -o yaml | kubectl apply -f -
    
    echo -e "${GREEN}Namespaces created!${NC}"
}

# Print summary
print_summary() {
    echo ""
    echo "=========================================="
    echo -e "${GREEN}BITA Local Environment Setup Complete!${NC}"
    echo "=========================================="
    echo ""
    echo "Docker Services:"
    echo "  - PostgreSQL: localhost:5432 (user: bita, pass: bita123, db: bita_db)"
    echo "  - Redis: localhost:6379"
    echo "  - Kafka: localhost:9092"
    echo "  - Zookeeper: localhost:2181"
    echo "  - Elasticsearch: localhost:9200"
    echo "  - Kafka UI: http://localhost:8080"
    echo ""
    echo "Minikube:"
    echo "  - Dashboard: minikube dashboard"
    echo "  - IP: $(minikube ip)"
    echo ""
    echo "Next steps:"
    echo "  1. Build the project: mvn clean install"
    echo "  2. Run ESM Backend: cd bita-esm-backend && mvn spring-boot:run"
    echo "  3. Run ESM Frontend: cd bita-esm-frontend && npm run dev"
    echo ""
}

# Main execution
main() {
    check_prerequisites
    start_docker_services
    start_minikube
    configure_networking
    create_namespaces
    print_summary
}

main "$@"
