# BITA Infrastructure

Infrastructure configurations for the BITA platform.

## Directory Structure

```
bita-infra/
├── docker/
│   ├── docker-compose.yml       # Main compose file
│   └── docker-compose.dev.yml   # Development overrides
├── kubernetes/
│   └── local/                   # Minikube configurations
└── scripts/
    ├── setup-local.sh           # Setup local environment
    └── teardown-local.sh        # Cleanup local environment
```

## Local Development with Docker Compose

```bash
cd docker
docker-compose up -d
```

### Services

| Service | Port | Description |
|---------|------|-------------|
| PostgreSQL | 5432 | Database |
| Redis | 6379 | Cache |
| Kafka | 9092 | Event streaming |
| Zookeeper | 2181 | Kafka coordination |
| Elasticsearch | 9200 | Logging |
| Kafka UI | 8080 | Kafka management UI |

### Default Credentials

- **PostgreSQL**: `bita` / `bita123` / database: `bita_db`
- **Redis**: No password (local only)
- **Elasticsearch**: No auth (local only)

## Minikube Setup

```bash
./scripts/setup-local.sh
```

This will:
1. Start Minikube
2. Deploy ESM Backend, Frontend, and ESB Core
3. Configure networking between Docker and Minikube
