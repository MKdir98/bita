# Next-Generation Enterprise Service Gateway Documentation

## Project Overview

This repository contains documentation for the **Next-Generation Enterprise Service Gateway Based on Large Language Models** - a master's thesis project that aims to modernize and enhance the existing Enterprise Service Management (ESM) and Enterprise Service Bus (ESB) infrastructure using LLM-powered automation and intelligent service orchestration.

---

## Documentation Structure

### Phase 1: Legacy System Analysis

1. **[Legacy System Overview](./01-legacy-system-overview.md)**
   - Comprehensive analysis of the existing ESM/ESB architecture
   - System capabilities and features
   - Technology stack and design patterns
   - Security architecture (WS-Security 1.1)
   - Performance characteristics
   - Limitations and challenges

2. **[ESM Technical Specification](./02-esm-technical-specification.md)**
   - Enterprise Service Management platform details
   - Domain model and entity relationships
   - Web UI (Apache Wicket) architecture
   - REST API specifications
   - WS-Security implementation
   - Database schema
   - Configuration and deployment

3. **[ESB Technical Specification](./03-esb-technical-specification.md)**
   - Enterprise Service Bus implementation
   - Request processing flow
   - Dynamic route creation
   - Processors and interceptors
   - Multi-domain architecture
   - ActiveMQ integration
   - Monitoring and logging
   - Performance tuning

### Phase 2: Next-Generation System Design

4. **[System Architecture](./04-system-architecture.md)**
   - High-level architecture overview
   - One Pod Group per Service (with version)
   - Auto-deployment when Service → ACTIVE
   - Path-based routing: `/esb/{collection}/{version}/*`
   - Security architecture
   - Logging and monitoring
   - LLM integration design

5. **[Database Schema](./05-database-schema.md)**
   - Complete database schema
   - Entity relationships
   - Template tables (Endpoint, Component, Route)
   - Instance tables
   - Audit tables (Hibernate Envers)
   - Sample data

6. **[DDD Architecture](./06-ddd-architecture.md)**
   - Bounded contexts
   - Domain models
   - Package structure
   - Commands and handlers
   - REST API specifications
   - LLM tools definition

7. **[Kubernetes Deployment Guide](./07-kubernetes-deployment-guide.md)**
   - Prerequisites
   - Step-by-step deployment
   - Camel K integration
   - Zero downtime updates
   - Monitoring setup
   - Backup and recovery
   - Troubleshooting

8. **[Project Modules Specification](./08-project-modules-specification.md)**
   - All projects and their modules
   - Package structures (DDD)
   - REST API specifications
   - User roles and permissions
   - Project dependencies

9. **[Local Development Guide](./09-local-development-guide.md)**
   - Quick start with Docker Compose
   - Configuration files
   - Running tests
   - Minikube setup
   - Common issues and solutions

10. **[Test Scenarios](./10-test-scenarios.md)**
    - Unit test scenarios
    - Integration test scenarios
    - API contract tests
    - E2E tests (Cypress)
    - Performance tests (Gatling)

11. **[UI Design Specification](./11-ui-design-specification.md)**
    - Layout structure
    - Authentication pages
    - Client management pages
    - Service management pages
    - Route pages with diagrams
    - LLM chat interface
    - User management pages
    - Responsive design

12. **[Task Breakdown](./12-task-breakdown.md)**
    - All development tasks with IDs
    - Task status tracking
    - Dependencies between tasks
    - Estimated hours
    - BDD test scenarios

---

## Legacy System Summary

### ESM (Enterprise Service Management)

**Purpose**: Central registry and management platform for enterprise services

**Key Features**:
- Service registry with WSDL management
- Multi-tenant organization hierarchy
- Role-based access control
- X.509 certificate management
- Web-based administration interface
- REST API for ESB integration

**Technology**: Java 8, Apache Wicket, Apache CXF, Hibernate, PostgreSQL

### ESB-Master (BITA)

**Purpose**: Service gateway and routing infrastructure

**Key Features**:
- Dynamic service routing based on ESM configuration
- WS-Security 1.1 enforcement (X.509 encryption/signing)
- IP-based and organization-based access control
- Multi-domain support with ActiveMQ
- Load balancing and failover
- Comprehensive transaction logging (InfluxDB)

**Technology**: Spring Boot, Apache Camel, Apache CXF, ActiveMQ

### Architecture Pattern

```
┌─────────────────────────────────────────────────────────────┐
│                     Client Applications                      │
└────────────────────────┬────────────────────────────────────┘
                         │ SOAP + WS-Security 1.1
                         │
┌────────────────────────▼────────────────────────────────────┐
│                  ESB-Master (Gateway)                        │
│  - Dynamic routing                                           │
│  - Security enforcement                                      │
│  - Access control                                            │
│  - Load balancing                                            │
└────────────────────────┬────────────────────────────────────┘
                         │
              ┌──────────┼──────────┐
              │          │          │
    ┌─────────▼──┐  ┌───▼────┐  ┌─▼─────────┐
    │ Local      │  │ Remote │  │ Remote    │
    │ Services   │  │ Domain │  │ Domain    │
    │            │  │ (MQ)   │  │ (MQ)      │
    └────────────┘  └────────┘  └───────────┘

┌─────────────────────────────────────────────────────────────┐
│              ESM (Management & Registry)                     │
│  - Service metadata                                          │
│  - Organization management                                   │
│  - Access control rules                                      │
│  - Certificate repository                                    │
└─────────────────────────────────────────────────────────────┘
```

---

## Key Challenges in Legacy System

### 1. Developer Experience
- **Manual Service Registration**: Requires WSDL creation and upload
- **Complex Configuration**: WS-Security setup is error-prone
- **Limited Tooling**: No automated testing or development tools
- **Steep Learning Curve**: SOAP and WS-Security expertise required

### 2. Protocol Limitations
- **SOAP Only**: No REST, GraphQL, or gRPC support
- **WS-Security 1.1**: No modern authentication (OAuth, JWT)
- **Legacy Standards**: Difficult to integrate with modern systems

### 3. Scalability Challenges
- **Single ESM Instance**: Potential bottleneck
- **ActiveMQ Latency**: Cross-domain communication overhead
- **Certificate Distribution**: Manual and time-consuming

### 4. Operational Complexity
- **Manual Certificate Management**: Upload, renewal, revocation
- **Limited Observability**: No distributed tracing
- **Complex Debugging**: Encrypted messages difficult to inspect

### 5. Integration Challenges
- **Tight Coupling**: ESB depends on ESM structure
- **Java-Only Services**: No polyglot support
- **Limited Service Composition**: Manual orchestration required

---

## Research Objectives

The next-generation system aims to address these challenges by leveraging Large Language Models to:

1. **Automate Service Creation**
   - Natural language service definition
   - Automatic WSDL/OpenAPI generation
   - Code generation for service implementations

2. **Simplify Security Configuration**
   - LLM-guided security policy creation
   - Automated certificate management
   - Modern authentication protocols (OAuth 2.0, JWT)

3. **Enhance Developer Experience**
   - Conversational service development
   - Intelligent error detection and resolution
   - Automated testing and validation

4. **Support Modern Protocols**
   - REST APIs with OpenAPI specifications
   - GraphQL schemas and resolvers
   - gRPC service definitions
   - WebSocket support

5. **Intelligent Service Orchestration**
   - LLM-powered service composition
   - Automatic workflow generation
   - Semantic service discovery

6. **Improve Observability**
   - Natural language query interface for logs and metrics
   - Automated anomaly detection
   - Intelligent alerting and diagnostics

---

## Technology Stack (Decided)

### Backend
- **Language**: Java 17+
- **Framework**: Spring Boot 3.x (ESM), Vert.x 4.x (ESB)
- **ORM**: Hibernate 6.x with Envers (audit trail)
- **Database**: PostgreSQL 15+
- **Cache**: Redis

### Frontend
- **Framework**: React 18+ with TypeScript 5.x
- **State Management**: TanStack Query
- **UI Components**: Shadcn/ui + Tailwind CSS
- **Visualization**: React Flow (route diagrams)

### Integration
- **Routing Engine**: Apache Camel 4.x
- **SOAP Services**: Apache CXF 4.x (WS-Security 1.1)
- **K8s Deployment**: Auto-managed by ESM Backend (no Camel K needed)

### Messaging
- **Event Streaming**: Apache Kafka (Strimzi operator)
- **Cross-domain Messaging**: Apache ActiveMQ

### LLM Integration
- **Primary Provider**: OpenAI GPT-4 (swappable)
- **Interface**: Tool/Function calling for ESM APIs
- **Interaction**: Chat-based with confirmation flow

### Security
- **Credential Types**: IP, X.509 Certificate, API Key, OAuth2, Basic Auth
- **SSL/TLS**: Nginx Ingress with cert-manager
- **WS-Security**: WSS4J (backward compatibility)

### Observability
- **Metrics**: Prometheus + Grafana
- **Logging**: Elasticsearch + Kibana (via SEDA channel)
- **Tracing**: Request ID propagation

### Infrastructure
- **Container Orchestration**: Kubernetes 1.26+
- **Package Management**: Helm 3.x
- **CI/CD**: GitLab CI / GitHub Actions

---

## Academic Context

### Research Questions

1. **How can LLMs effectively automate enterprise service gateway configuration?**
   - Natural language to service definition translation
   - Accuracy and reliability of generated configurations
   - Human-in-the-loop validation strategies

2. **What are the optimal patterns for LLM-powered service composition?**
   - Semantic service discovery
   - Automatic workflow generation
   - Performance and correctness guarantees

3. **How can LLMs enhance security policy management in service gateways?**
   - Policy generation from natural language requirements
   - Automated security testing and validation
   - Compliance verification

4. **What are the performance implications of LLM integration in service gateways?**
   - Latency impact of LLM calls
   - Caching and optimization strategies
   - Scalability considerations

### Expected Contributions

1. **Novel Architecture**: LLM-powered enterprise service gateway design
2. **Automation Framework**: Tools and patterns for LLM-based service management
3. **Evaluation Metrics**: Performance, accuracy, and usability benchmarks
4. **Best Practices**: Guidelines for LLM integration in enterprise systems
5. **Open-Source Implementation**: Reference implementation and tooling

---

## Next Steps

### Phase 1: Requirements Gathering ✓
- [x] Analyze legacy system architecture
- [x] Document existing features and capabilities
- [x] Identify limitations and challenges
- [x] Create comprehensive technical documentation

### Phase 2: System Design ✓
- [x] Define LLM integration architecture
- [x] Design service creation workflow
- [x] Specify API interfaces
- [x] Create system architecture diagrams
- [x] Define data models and schemas (DDD)
- [x] Design database schema
- [x] Document Kubernetes deployment

### Phase 3: Prototype Development (Next Phase)
- [ ] Set up project repositories
- [ ] Implement bita-common library
- [ ] Implement ESM backend (Spring Boot + K8s Deployer)
- [ ] Implement ESM frontend (React)
- [ ] Implement ESB core (Vert.x + Camel)
- [ ] LLM integration with OpenAI GPT-4

### Phase 4: Evaluation and Testing
- [ ] Unit tests for all modules
- [ ] Integration tests with Testcontainers
- [ ] Performance benchmarking
- [ ] Accuracy evaluation of LLM-generated routes
- [ ] Usability testing
- [ ] Security assessment

### Phase 5: Thesis Writing
- [ ] Literature review
- [ ] Methodology documentation
- [ ] Results analysis
- [ ] Conclusions and future work

---

## Contributing

This is an academic research project. For questions or collaboration opportunities, please contact the project team.

---

## License

This documentation is part of a master's thesis project. All rights reserved.

---

**Project Start Date**: January 28, 2026  
**Expected Completion**: [To be determined]  
**Institution**: [Your University]  
**Supervisor**: [Supervisor Name]  
**Student**: Mehdi

---

## Key Architecture Decisions

1. **One Pod Group per Service (with version)**
   - PaymentService v1.0 → `payment-1-0-esb` Deployment
   - Fast startup (only routes for one Service per pod)
   - Independent scaling per Service

2. **Automatic K8s Deployment**
   - When Service phase → ACTIVE, ESM Backend auto-creates:
     - Deployment
     - K8s Service  
     - HPA (HorizontalPodAutoscaler)
     - Ingress rule

3. **Path-based Routing**
   - Format: `/esb/{collection}/{version}/*`
   - Example: `/esb/payment/1.0/api/pay`
   - Nginx Ingress routes to correct pod group

4. **No Camel K Required**
   - ESB Core is a standard Docker image
   - Routes loaded dynamically from ESM API based on SERVICE_ID env var

---

## Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 2.0 | 2026-01-28 | Mehdi | Updated architecture: One Pod Group per Service |
| 1.0 | 2026-01-28 | Mehdi | Initial documentation of legacy system |

---

## References

1. OASIS Web Services Security (WS-Security) Specification
2. Apache CXF Documentation
3. Apache Camel Enterprise Integration Patterns
4. Spring Boot Reference Documentation
5. Large Language Models for Code Generation (Academic Papers)
6. Enterprise Integration Patterns (Hohpe & Woolf)
7. Building Microservices (Sam Newman)
8. API Gateway Patterns (Phil Calçado)
