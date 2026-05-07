# BITA - Next-Generation Enterprise Service Gateway

Enterprise Service Gateway based on Large Language Models (LLMs) for intelligent service creation and management.

## Project Structure

```
bita/
├── bita-common/           # Shared library (DTOs, Events, Processors)
├── bita-esm-backend/      # ESM Backend (Spring Boot + K8s Client)
├── bita-esm-frontend/     # ESM Frontend (React + TypeScript)
├── bita-esb-core/         # ESB Core (Vert.x + Camel)
├── bita-infra/            # Infrastructure (Docker, Kubernetes, Helm)
└── docs/                  # Documentation
```

## Prerequisites

- Java 17+
- Maven 3.9+
- Docker & Docker Compose
- Node.js 18+ (for frontend)
- Kubernetes cluster (for production) or Minikube (for local development)

## Quick Start

### 1. Start Infrastructure Services

```bash
cd bita-infra/docker
docker-compose up -d
```

This starts:
- PostgreSQL (port 5432)
- Redis (port 6379)
- Kafka + Zookeeper (ports 9092, 2181)
- Elasticsearch (port 9200)
- Kafka UI (port 8080)

### 2. Build the Project

```bash
mvn clean install
```

### 3. Run ESM Backend

```bash
cd bita-esm-backend
mvn spring-boot:run
```

### 4. Run ESM Frontend

```bash
cd bita-esm-frontend
npm install
npm run dev
```

## Technology Stack

### Backend
- Java 17+
- Spring Boot 3.x (ESM)
- Vert.x 4.x (ESB)
- Apache Camel 4.x
- Apache CXF 4.x (WS-Security 1.1)
- Hibernate 6.x with Envers

### Frontend
- React 18+
- TypeScript 5.x
- TanStack Query
- Tailwind CSS
- Shadcn/ui

### Infrastructure
- PostgreSQL 15+
- Redis 7+
- Apache Kafka
- Elasticsearch 8+
- Kubernetes

## Modules

### bita-common
Shared library containing:
- Domain primitives (Value Objects, Enums)
- DTOs for API communication
- Domain events for Kafka messaging
- Shared Camel processors

### bita-esm-backend
Enterprise Service Management backend:
- Service and client management
- Template and route management
- LLM integration for service creation
- Kubernetes deployer for auto-deployment

### bita-esm-frontend
React-based admin dashboard:
- Service management UI
- LLM chat interface
- Route visualization
- User management

### bita-esb-core
Enterprise Service Bus core:
- High-performance HTTP server (Vert.x)
- Dynamic route loading
- Request processing with Camel
- WS-Security 1.1 support

### bita-infra
Infrastructure configurations:
- Docker Compose for local development
- Kubernetes manifests
- Helm charts

## Documentation

See the [docs/](docs/) directory for detailed documentation:
- [System Architecture](docs/04-system-architecture.md)
- [Database Schema](docs/05-database-schema.md)
- [DDD Architecture](docs/06-ddd-architecture.md)
- [Local Development Guide](docs/09-local-development-guide.md)
- [Task Breakdown](docs/12-task-breakdown.md)

## License

All rights reserved.
