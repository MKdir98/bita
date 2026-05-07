# Local Development Guide

## Document Information

**Project**: BITA - Next-Generation Enterprise Service Gateway  
**Version**: 4.0  
**Date**: January 28, 2026  
**Purpose**: Quick and easy local development setup

---

## 1. Prerequisites

### 1.1 Required Software

| Software | Version | Installation |
|----------|---------|--------------|
| Docker | 24+ | https://docs.docker.com/get-docker/ |
| Docker Compose | 2.20+ | Included with Docker Desktop |
| Java | 17+ | `sdk install java 17.0.9-tem` |
| Node.js | 20+ | `nvm install 20` |
| Maven | 3.9+ | `sdk install maven` |

### 1.2 Optional (for Kubernetes testing)

| Software | Version | Installation |
|----------|---------|--------------|
| Minikube | 1.32+ | `brew install minikube` |
| kubectl | 1.28+ | `brew install kubectl` |
| Helm | 3.13+ | `brew install helm` |

---

## 2. Quick Start

### 2.1 Option A: Docker Only (Simple)

```bash
# Clone repository
git clone https://github.com/your-org/bita.git
cd bita

# Start everything with Docker Compose
./bita-infra/scripts/setup-docker.sh

# This starts: PostgreSQL, Redis, Kafka, Elasticsearch + all apps
```

### 2.2 Option B: Hybrid (Docker + Minikube - Production-like)

```bash
# Clone repository
git clone https://github.com/your-org/bita.git
cd bita

# Start hybrid environment
./bita-infra/scripts/setup-local.sh

# This starts:
# - Docker: PostgreSQL, Redis, Kafka, Elasticsearch
# - Minikube: ESM Backend, ESM Frontend, ESB Core
```

### 2.2 Docker Compose Configuration

Create `docker-compose.yml` in project root:

```yaml
version: '3.8'

services:
  # PostgreSQL Database
  postgres:
    image: postgres:15-alpine
    container_name: bita-postgres
    environment:
      POSTGRES_USER: bita
      POSTGRES_PASSWORD: bita123
      POSTGRES_DB: bita_esm
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U bita"]
      interval: 10s
      timeout: 5s
      retries: 5

  # Redis Cache
  redis:
    image: redis:7-alpine
    container_name: bita-redis
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  # Kafka (with Zookeeper)
  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    container_name: bita-zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    ports:
      - "2181:2181"

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    container_name: bita-kafka
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
      - "9093:9093"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: 'true'
    healthcheck:
      test: ["CMD", "kafka-broker-api-versions", "--bootstrap-server", "localhost:9092"]
      interval: 10s
      timeout: 10s
      retries: 5

  # Kafka UI (optional - for debugging)
  kafka-ui:
    image: provectuslabs/kafka-ui:latest
    container_name: bita-kafka-ui
    depends_on:
      - kafka
    ports:
      - "8090:8080"
    environment:
      KAFKA_CLUSTERS_0_NAME: local
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:29092
      KAFKA_CLUSTERS_0_ZOOKEEPER: zookeeper:2181

  # Elasticsearch (for logging)
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.11.0
    container_name: bita-elasticsearch
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
    ports:
      - "9200:9200"
    volumes:
      - elasticsearch_data:/usr/share/elasticsearch/data
    healthcheck:
      test: ["CMD-SHELL", "curl -s http://localhost:9200/_cluster/health | grep -q 'green\\|yellow'"]
      interval: 10s
      timeout: 5s
      retries: 10

  # Kibana (optional - for log viewing)
  kibana:
    image: docker.elastic.co/kibana/kibana:8.11.0
    container_name: bita-kibana
    depends_on:
      - elasticsearch
    ports:
      - "5601:5601"
    environment:
      ELASTICSEARCH_HOSTS: http://elasticsearch:9200

volumes:
  postgres_data:
  elasticsearch_data:
```

### 2.3 Start Development Servers

**Terminal 1 - ESM Backend:**
```bash
cd bita-esm-backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

**Terminal 2 - ESM Frontend:**
```bash
cd bita-esm-frontend
npm install
npm run dev
```

**Terminal 3 - ESB Core (optional):**
```bash
cd bita-esb-core
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### 2.4 Access Points

| Service | URL | Credentials |
|---------|-----|-------------|
| ESM Frontend | http://localhost:5173 | admin / admin123 |
| ESM Backend API | http://localhost:8080/api/v1 | - |
| ESM Swagger UI | http://localhost:8080/swagger-ui.html | - |
| ESB Core | http://localhost:8081 | - |
| Kafka UI | http://localhost:8090 | - |
| Kibana | http://localhost:5601 | - |
| PostgreSQL | localhost:5432 | bita / bita123 |

---

## 3. Configuration Files

### 3.1 ESM Backend - application-local.yml

```yaml
# bita-esm-backend/src/main/resources/application-local.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/bita_esm
    username: bita
    password: bita123
  
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
  
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: esm-local
  
  redis:
    host: localhost
    port: 6379

# LLM Configuration
llm:
  provider: openai
  api-key: ${OPENAI_API_KEY:sk-test-key}
  model: gpt-4

# Logging
logging:
  level:
    ir.iais.bita: DEBUG
    org.hibernate.SQL: DEBUG

# Server
server:
  port: 8080
```

### 3.2 ESM Frontend - .env.local

```env
# bita-esm-frontend/.env.local
VITE_API_URL=http://localhost:8080
VITE_WS_URL=ws://localhost:8080
```

### 3.3 ESB Core - application-local.yml

```yaml
# bita-esb-core/src/main/resources/application-local.yml
esb:
  domain: local
  esm-url: http://localhost:8080

spring:
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: esb-local

server:
  port: 8081
```

---

## 4. Database Setup

### 4.1 Initial Schema

The schema is automatically created by Hibernate. To manually create:

```bash
# Connect to PostgreSQL
docker exec -it bita-postgres psql -U bita -d bita_esm

# Run schema (if needed)
\i /path/to/schema.sql
```

### 4.2 Seed Data

```bash
# Run seed script
cd bita-esm-backend
mvn spring-boot:run -Dspring-boot.run.arguments="--seed"
```

Or manually insert:

```sql
-- Create admin user
INSERT INTO users (username, email, password_hash, full_name, is_active)
VALUES ('admin', 'admin@bita.ir', '$2a$10$...', 'Administrator', true);

-- Create test client
INSERT INTO client (name, display_name, tags, is_active)
VALUES ('TestClient', 'سازمان تست', '{"national_id": "1234567890"}', true);
```

---

## 5. Running Tests

### 5.1 Unit Tests

```bash
# All projects
mvn test

# Specific project
cd bita-esm-backend
mvn test

# With coverage
mvn test jacoco:report
```

### 5.2 Integration Tests

```bash
# Start test containers
cd bita-esm-backend
mvn verify -Pintegration-tests
```

### 5.3 Frontend Tests

```bash
cd bita-esm-frontend
npm run test
npm run test:e2e
```

---

## 6. Hybrid Setup (Docker + Minikube)

This setup is production-like: databases in Docker, applications in Kubernetes.

### 6.1 Setup Script

Create `bita-infra/scripts/setup-local.sh`:

```bash
#!/bin/bash
set -e

echo "=== BITA Local Development Setup ==="

# Step 1: Start infrastructure in Docker
echo "Starting infrastructure services (Docker)..."
docker-compose -f docker/docker-compose.infra.yml up -d

# Wait for services
echo "Waiting for PostgreSQL..."
until docker exec bita-postgres pg_isready -U bita; do sleep 2; done

echo "Waiting for Kafka..."
until docker exec bita-kafka kafka-broker-api-versions --bootstrap-server localhost:9092 2>/dev/null; do sleep 2; done

# Step 2: Start Minikube
echo "Starting Minikube..."
minikube start --cpus=4 --memory=8192 --driver=docker

# Enable addons
minikube addons enable ingress
minikube addons enable metrics-server

# Step 3: Configure networking between Docker and Minikube
DOCKER_HOST_IP=$(docker network inspect bridge --format='{{range .IPAM.Config}}{{.Gateway}}{{end}}')
echo "Docker host IP: $DOCKER_HOST_IP"

# Step 4: Build and load images to Minikube
echo "Building application images..."
eval $(minikube docker-env)
docker build -t bita/esm-backend:local -f docker/esm-backend/Dockerfile ../..
docker build -t bita/esm-frontend:local -f docker/esm-frontend/Dockerfile ../..
docker build -t bita/esb-core:local -f docker/esb-core/Dockerfile ../..

# Step 5: Deploy to Minikube
echo "Deploying applications to Minikube..."
kubectl create namespace bita --dry-run=client -o yaml | kubectl apply -f -

# Create ConfigMap with external service URLs
kubectl create configmap bita-config \
  --from-literal=POSTGRES_HOST=$DOCKER_HOST_IP \
  --from-literal=POSTGRES_PORT=5432 \
  --from-literal=KAFKA_BOOTSTRAP_SERVERS=$DOCKER_HOST_IP:9092 \
  --from-literal=REDIS_HOST=$DOCKER_HOST_IP \
  --from-literal=ELASTICSEARCH_HOST=$DOCKER_HOST_IP \
  -n bita --dry-run=client -o yaml | kubectl apply -f -

# Apply Kubernetes manifests
kubectl apply -f kubernetes/local/ -n bita

# Step 6: Wait for deployments
echo "Waiting for deployments..."
kubectl rollout status deployment/esm-backend -n bita --timeout=300s
kubectl rollout status deployment/esm-frontend -n bita --timeout=300s
kubectl rollout status deployment/esb-core -n bita --timeout=300s

# Step 7: Setup ingress
kubectl apply -f kubernetes/local/ingress.yaml -n bita

# Step 8: Start tunnel (in background)
echo "Starting Minikube tunnel..."
minikube tunnel &

echo ""
echo "=== Setup Complete ==="
echo "ESM Frontend: http://localhost/esm"
echo "ESM Backend API: http://localhost/api"
echo "ESB Core: http://localhost/esb"
echo "Kafka UI: http://localhost:8090"
echo "Kibana: http://localhost:5601"
echo ""
echo "To stop: ./teardown-local.sh"
```

### 6.2 Infrastructure Docker Compose

Create `bita-infra/docker/docker-compose.infra.yml`:

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15-alpine
    container_name: bita-postgres
    environment:
      POSTGRES_USER: bita
      POSTGRES_PASSWORD: bita123
      POSTGRES_DB: bita_esm
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine
    container_name: bita-redis
    ports:
      - "6379:6379"

  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    container_name: bita-zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
    ports:
      - "2181:2181"

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    container_name: bita-kafka
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://host.docker.internal:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1

  kafka-ui:
    image: provectuslabs/kafka-ui:latest
    container_name: bita-kafka-ui
    ports:
      - "8090:8080"
    environment:
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:9092

  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.11.0
    container_name: bita-elasticsearch
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
    ports:
      - "9200:9200"

  kibana:
    image: docker.elastic.co/kibana/kibana:8.11.0
    container_name: bita-kibana
    ports:
      - "5601:5601"
    environment:
      ELASTICSEARCH_HOSTS: http://elasticsearch:9200

volumes:
  postgres_data:
```

### 6.3 Kubernetes Local Manifests

Create `bita-infra/kubernetes/local/esm-backend.yaml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: esm-backend
spec:
  replicas: 1
  selector:
    matchLabels:
      app: esm-backend
  template:
    metadata:
      labels:
        app: esm-backend
    spec:
      containers:
      - name: esm-backend
        image: bita/esm-backend:local
        imagePullPolicy: Never
        ports:
        - containerPort: 8080
        envFrom:
        - configMapRef:
            name: bita-config
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: local
---
apiVersion: v1
kind: Service
metadata:
  name: esm-backend
spec:
  selector:
    app: esm-backend
  ports:
  - port: 8080
```

### 6.4 Teardown Script

Create `bita-infra/scripts/teardown-local.sh`:

```bash
#!/bin/bash

echo "Stopping Minikube..."
minikube stop

echo "Stopping Docker services..."
docker-compose -f docker/docker-compose.infra.yml down

echo "Done!"
```

### 6.5 Access Services

| Service | URL | Notes |
|---------|-----|-------|
| ESM Frontend | http://localhost/esm | Via Minikube Ingress |
| ESM Backend | http://localhost/api | Via Minikube Ingress |
| ESB Core | http://localhost/esb | Via Minikube Ingress |
| Kafka UI | http://localhost:8090 | Direct Docker |
| Kibana | http://localhost:5601 | Direct Docker |
| PostgreSQL | localhost:5432 | Direct Docker |

---

## 7. Common Issues & Solutions

### 7.1 Docker Issues

**Problem: Port already in use**
```bash
# Find and kill process
lsof -i :5432
kill -9 <PID>

# Or change port in docker-compose.yml
```

**Problem: Out of disk space**
```bash
docker system prune -a
```

### 7.2 Database Issues

**Problem: Connection refused**
```bash
# Check if container is running
docker ps | grep postgres

# Check logs
docker logs bita-postgres
```

**Problem: Schema issues**
```bash
# Reset database
docker-compose down -v
docker-compose up -d postgres
```

### 7.3 Kafka Issues

**Problem: Consumer lag**
```bash
# Check consumer group
docker exec bita-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group esm-local \
  --describe
```

### 7.4 Frontend Issues

**Problem: API connection error**
```bash
# Check CORS in backend
# Ensure application-local.yml has:
# spring.mvc.cors.allowed-origins: http://localhost:5173
```

---

## 8. Development Workflow

### 8.1 Backend Development

```bash
# Hot reload with spring-boot-devtools
mvn spring-boot:run

# Run specific test
mvn test -Dtest=ClientServiceTest

# Generate API docs
mvn springdoc-openapi:generate
```

### 8.2 Frontend Development

```bash
# Development server with hot reload
npm run dev

# Build for production
npm run build

# Lint and format
npm run lint
npm run format
```

### 8.3 Database Migrations

```bash
# Using Flyway (if configured)
mvn flyway:migrate
mvn flyway:info

# Or using Liquibase
mvn liquibase:update
```

---

## 9. IDE Setup

### 9.1 IntelliJ IDEA (Backend)

1. Import as Maven project
2. Enable annotation processing (Lombok)
3. Set JDK 17
4. Run configuration: Spring Boot, profile=local

### 9.2 VS Code (Frontend)

Recommended extensions:
- ESLint
- Prettier
- Tailwind CSS IntelliSense
- TypeScript Vue Plugin

Settings:
```json
{
  "editor.formatOnSave": true,
  "editor.defaultFormatter": "esbenp.prettier-vscode",
  "typescript.preferences.importModuleSpecifier": "relative"
}
```

---

## 10. Quick Commands Reference

```bash
# Start everything
docker-compose up -d && cd bita-esm-backend && mvn spring-boot:run &

# Stop everything
docker-compose down

# View logs
docker-compose logs -f

# Reset database
docker-compose down -v && docker-compose up -d postgres

# Run tests
mvn test

# Build all
mvn clean package -DskipTests

# Check services
curl http://localhost:8080/actuator/health
curl http://localhost:8081/health
```

---

**Document Version**: 1.0  
**Last Updated**: January 28, 2026
