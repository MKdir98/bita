# Next-Generation Enterprise Service Gateway - System Architecture

## Document Information

**Project**: BITA - Next-Generation Enterprise Service Gateway  
**Version**: 4.0  
**Date**: January 28, 2026  
**Purpose**: System Architecture and Design Document

---

## 1. Executive Summary

This document describes the architecture of the next-generation Enterprise Service Gateway (ESG) based on Large Language Models (LLMs). The system modernizes the existing ESM/ESB infrastructure by introducing:

- **LLM-Powered Service Creation**: Natural language-based service definition and route generation
- **Reactive Architecture**: Vert.x for high-throughput request handling
- **Auto-Scaling per Service**: Each Service (with version) gets its own Pod Group
- **Automatic K8s Deployment**: ESM Backend auto-creates Deployments when Service is activated
- **Path-based Routing**: `/esb/{collection}/{version}/*` format
- **Event-Driven Synchronization**: Kafka-based real-time configuration updates
- **Modern Security**: Flexible credential management with backward compatibility for WS-Security 1.1

### Key Architecture Decisions

| Decision | Description |
|----------|-------------|
| **One Pod Group per Service** | Each Service (with version) gets its own Deployment for fast startup and independent scaling |
| **Automatic K8s Resources** | ESM Backend creates Deployment, Service, HPA, Ingress when Service goes ACTIVE |
| **Path-based Routing** | `/esb/{collection}/{version}/*` enables Ingress to route to correct pod group |
| **Primary Runtime: Direct Kubernetes** | Standard ESB Core Docker image + dynamic route loading from ESM API |
| **Camel K: Optional Mode** | Camel K can be used for generated integrations in specific deployment profiles |

---

## 2. System Overview

### 2.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              External Clients                                │
│                    (Organizations calling services)                          │
└────────────────────────────────┬────────────────────────────────────────────┘
                                 │ HTTPS
┌────────────────────────────────▼────────────────────────────────────────────┐
│                           Nginx Ingress                                      │
│  - SSL/TLS Termination                                                       │
│  - Path-based Routing: /esb/{collection}/{version}/* → Pod Group           │
│  - Load Balancing (per Service pod group)                                   │
└────────────────────────────────┬────────────────────────────────────────────┘
                                 │
        ┌────────────────────────┼────────────────────────┐
        │                        │                        │
        ▼                        ▼                        ▼
┌───────────────────┐  ┌───────────────────┐  ┌───────────────────┐
│ payment-1-0-esb   │  │ payment-1-1-esb   │  │ user-2-0-esb      │
│ (2 replicas)      │  │ (2 replicas)      │  │ (3 replicas)      │
│                   │  │                   │  │                   │
│ Routes for        │  │ Routes for        │  │ Routes for        │
│ PaymentSvc v1.0   │  │ PaymentSvc v1.1   │  │ UserSvc v2.0      │
└───────────────────┘  └───────────────────┘  └───────────────────┘
        │                        │                        │
        └────────────────────────┼────────────────────────┘
                                 │ Kafka Events + REST API
┌────────────────────────────────▼────────────────────────────────────────────┐
│                          Apache Kafka                                        │
│  - Configuration change events                                               │
│  - Route updates                                                             │
│  - Audit logs                                                                │
└────────────────────────────────┬────────────────────────────────────────────┘
                                 │
┌────────────────────────────────▼────────────────────────────────────────────┐
│                              ESM                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                      ESM Backend                                     │   │
│  │  - Service Management                                                │   │
│  │  - Client Management                                                 │   │
│  │  - Template & Route Management                                       │   │
│  │  - Access Control                                                    │   │
│  │  - LLM Integration                                                   │   │
│  │  - **Kubernetes Deployer** (auto-create Deployments)                │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                      ESM Frontend                                    │   │
│  │  - Admin Dashboard                                                   │   │
│  │  - LLM Chat Interface                                                │   │
│  │  - Route Visualization                                               │   │
│  │  - Service Testing Panel                                             │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                      PostgreSQL                                      │   │
│  │  - Configuration data                                                │   │
│  │  - Audit trail (Hibernate Envers)                                   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Service to Pod Group Mapping

```
ServiceCollection: payment (base_path: /esb/payment)
│
├── Service: PaymentService v1.0 (phase: ACTIVE)
│   ├── K8s Deployment: payment-1-0-esb
│   ├── K8s Service: payment-1-0-esb-svc
│   ├── HPA: payment-1-0-esb-hpa (min: 2, max: 10)
│   ├── Ingress Path: /esb/payment/1.0/*
│   └── Routes: [soap-route, rest-route, ...]
│
├── Service: PaymentService v1.1 (phase: ACTIVE)
│   ├── K8s Deployment: payment-1-1-esb
│   ├── K8s Service: payment-1-1-esb-svc
│   ├── HPA: payment-1-1-esb-hpa (min: 1, max: 5)
│   ├── Ingress Path: /esb/payment/1.1/*
│   └── Routes: [new-soap-route, ...]
│
└── Service: PaymentService v2.0 (phase: DRAFT)
    └── No K8s resources yet (not ACTIVE)

ServiceCollection: user (base_path: /esb/user)
│
└── Service: UserService v2.0 (phase: ACTIVE)
    ├── K8s Deployment: user-2-0-esb
    ├── K8s Service: user-2-0-esb-svc
    ├── Ingress Path: /esb/user/2.0/*
    └── Routes: [user-api-route, ...]
```

### 2.3 Project Structure

```
bita/
├── bita-common/               # Shared Library (DTOs, Events, Processors)
├── bita-esm-backend/          # ESM Backend (Spring Boot + K8s Client)
├── bita-esm-frontend/         # ESM Frontend (React + TypeScript)
├── bita-esb-core/             # ESB Core (Vert.x + Camel)
├── bita-infra/                # Infrastructure (Helm, K8s manifests)
└── docs/                      # Documentation
```

---

## 3. Automatic Kubernetes Deployment

### 3.1 Service Lifecycle & Auto-Deployment

When a Service changes phase to ACTIVE, ESM Backend automatically creates Kubernetes resources:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Service Phase: DRAFT → TEST → ACTIVE                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  DRAFT:    - Service definition saved                                       │
│            - Routes can be added                                            │
│            - No K8s resources created                                       │
│                                                                              │
│  TEST:     - Service is deployed to test runtime                            │
│            - Ingress test path uses /test prefix                            │
│                                                                              │
│  ACTIVE:   - ESM Backend creates K8s resources automatically:              │
│            ┌─────────────────────────────────────────────────────────────┐ │
│            │  1. Deployment: {collection}-{version}-esb                  │ │
│            │     - Image: bita/esb-core:latest                           │ │
│            │     - Env: SERVICE_ID={id}, ESM_URL=http://esm-backend     │ │
│            │     - Replicas: service.min_replicas                        │ │
│            │                                                              │ │
│            │  2. K8s Service: {collection}-{version}-esb-svc            │ │
│            │     - Port: 8080                                            │ │
│            │                                                              │ │
│            │  3. HPA: {collection}-{version}-esb-hpa                    │ │
│            │     - Min: service.min_replicas                             │ │
│            │     - Max: service.max_replicas                             │ │
│            │     - Target CPU: service.target_cpu_percent                │ │
│            │                                                              │ │
│            │  4. Ingress Rule (added to esb-ingress):                   │ │
│            │     - TEST Path: /test/esb/{collection}/{version}/*        │ │
│            │     - ACTIVE Path: /esb/{collection}/{version}/*           │ │
│            │     - Backend: {collection}-{version}-esb-svc              │ │
│            └─────────────────────────────────────────────────────────────┘ │
│                                                                              │
│  DEACTIVATE: - ESM Backend deletes all K8s resources                       │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.2 Ingress Configuration (Auto-Generated)

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: esb-ingress
  namespace: bita-esb
spec:
  rules:
  - http:
      paths:
      # Auto-generated paths when Services become ACTIVE:
      - path: /esb/payment/1.0
        pathType: Prefix
        backend:
          service:
            name: payment-1-0-esb-svc
            port:
              number: 8080
      - path: /esb/payment/1.1
        pathType: Prefix
        backend:
          service:
            name: payment-1-1-esb-svc
            port:
              number: 8080
      - path: /esb/user/2.0
        pathType: Prefix
        backend:
          service:
            name: user-2-0-esb-svc
            port:
              number: 8080
```

---

## 4. Component Architecture

### 4.1 ESM Backend

**Technology Stack:**
- Java 17+
- Spring Boot 3.x
- Hibernate 6.x with Envers (audit trail)
- PostgreSQL
- Apache Kafka (event publishing)
- **Kubernetes Java Client** (auto-deployment)
- OpenAI GPT-4 API (LLM integration)

**Key Modules:**

```
bita-esm-backend/
├── src/main/java/ir/iais/bita/esm/
│   ├── client/                    # Client domain (DDD)
│   ├── service/                   # Service domain (DDD)
│   ├── route/                     # Route/Template domain (DDD)
│   ├── llm/                       # LLM integration
│   ├── auth/                      # Authentication (OTP, JWT)
│   ├── sync/                      # ESB sync APIs
│   ├── k8s/                       # **Kubernetes Deployer**
│   │   ├── KubernetesDeployerService.java
│   │   ├── DeploymentBuilder.java
│   │   ├── ServiceBuilder.java
│   │   ├── HpaBuilder.java
│   │   └── IngressManager.java
│   └── shared/                    # Shared infrastructure
```

**Kubernetes Deployer Service:**

```java
@Service
public class KubernetesDeployerService {
    
    private final KubernetesClient k8sClient;
    
    public void deployService(Service service) {
        ServiceCollection collection = service.getCollection();
        String name = buildName(collection.getName(), service.getVersion());
        // Example: payment-1-0-esb
        
        // 1. Create Deployment
        createDeployment(service.getId(), name, service.getMinReplicas());
        
        // 2. Create K8s Service
        createK8sService(name);
        
        // 3. Create HPA
        createHpa(name, service.getMinReplicas(), 
                  service.getMaxReplicas(), service.getTargetCpuPercent());
        
        // 4. Add Ingress Rule
        String path = collection.getBasePath() + "/" + service.getVersion();
        addIngressRule(path, name + "-svc");
        
        // 5. Update DB
        service.setK8sDeploymentName(name);
        service.setK8sServiceName(name + "-svc");
    }
    
    public void undeployService(Service service) {
        String name = service.getK8sDeploymentName();
        
        // Remove in reverse order
        removeIngressRule(service.getCollection().getBasePath() + "/" + service.getVersion());
        deleteHpa(name + "-hpa");
        deleteK8sService(name + "-svc");
        deleteDeployment(name);
    }
}
```

### 3.2 ESM Frontend

**Technology Stack:**
- React 18+
- TypeScript 5.x
- TanStack Query (data fetching)
- React Flow (route visualization)
- Tailwind CSS
- Shadcn/ui components

**Key Features:**
- Dashboard for service/organization management
- Chat-based LLM interface for service creation
- Interactive route diagram editor
- Service testing panel
- Real-time configuration updates

### 3.3 ESB Core

**Technology Stack:**
- Java 17+
- Vert.x 4.x (reactive HTTP server)
- Apache Camel 4.x (routing engine)
- Apache CXF 4.x (WS-Security)
- Apache Kafka (event consumption)

**Architecture:**

```
┌─────────────────────────────────────────────────────────────┐
│                     Vert.x HTTP Server                       │
│  - Non-blocking request handling                             │
│  - Back-pressure support                                     │
│  - High concurrency (event loop)                            │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│                    Request Router                            │
│  - Route matching                                            │
│  - Protocol detection (SOAP/REST)                           │
│  - Credential validation                                     │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│                    Apache Camel                              │
│  - Route execution                                           │
│  - EIP patterns                                              │
│  - Component integration                                     │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│               Target Services / Queues                       │
│  - Direct service invocation                                 │
│  - ActiveMQ for cross-domain                                │
│  - External HTTP services                                    │
└─────────────────────────────────────────────────────────────┘
```

### 3.4 Camel K Integration (Optional Profile)

**How it works (when Camel K profile is enabled):**

1. ESM generates route configuration
2. bita-kamel-generator converts to Kamel YAML
3. ESB applies Integration CRD to Kubernetes
4. Camel K Operator:
   - Compiles Java sources
   - Builds container image
   - Deploys as Pod
   - Manages rolling updates

**Status clarification:**
- پیش‌فرض معماری اجرایی این پروژه، deployment مستقیم ESB Core روی Kubernetes است (per-service pod group + ingress path routing).
- Camel K به عنوان مسیر اختیاری/پروفایل خاص برای integrationهای تولیدشده در نظر گرفته می‌شود.

**Integration CRD Example:**

```yaml
apiVersion: camel.apache.org/v1
kind: Integration
metadata:
  name: payment-service-v1
  namespace: bita-services
spec:
  replicas: 2
  dependencies:
    - "mvn:org.apache.camel:camel-cxf:4.0.0"
    - "mvn:org.apache.cxf:cxf-rt-ws-security:4.0.0"
  traits:
    container:
      requestCPU: "500m"
      requestMemory: "512Mi"
    health:
      enabled: true
      readinessProbeEnabled: true
  sources:
    - name: route.java
      content: |
        // Generated route code
```

---

## 4. Data Flow

### 4.1 Service Creation Flow

```
┌──────────────────────────────────────────────────────────────────────────┐
│ 1. User describes service in chat                                         │
│    "یک سرویس پرداخت با WS-Security 1.1 بساز که..."                        │
└────────────────────────────┬─────────────────────────────────────────────┘
                             │
┌────────────────────────────▼─────────────────────────────────────────────┐
│ 2. LLM processes request                                                  │
│    - Understands requirements                                             │
│    - Selects appropriate templates                                        │
│    - Generates configuration                                              │
└────────────────────────────┬─────────────────────────────────────────────┘
                             │
┌────────────────────────────▼─────────────────────────────────────────────┐
│ 3. LLM returns plan for user confirmation                                │
│    "برای این سرویس نیاز دارم که:"                                         │
│    "- یک Endpoint از نوع CXF WS-Security بسازم"                          │
│    "- یک Route با این مسیر تعریف کنم"                                     │
│    "- تایید میکنید؟"                                                      │
└────────────────────────────┬─────────────────────────────────────────────┘
                             │ User confirms
┌────────────────────────────▼─────────────────────────────────────────────┐
│ 4. ESM creates resources                                                  │
│    - Create Service record                                                │
│    - Create Endpoint instances                                            │
│    - Create Route configuration                                           │
│    - Publish ROUTE_CREATED event to Kafka                                │
└────────────────────────────┬─────────────────────────────────────────────┘
                             │ Kafka Event
┌────────────────────────────▼─────────────────────────────────────────────┐
│ 5. ESB receives event                                                     │
│    - Fetch route configuration from ESM API                              │
│    - Generate Kamel YAML                                                  │
│    - Apply to Kubernetes                                                  │
└────────────────────────────┬─────────────────────────────────────────────┘
                             │
┌────────────────────────────▼─────────────────────────────────────────────┐
│ 6. Camel K Operator deploys                                               │
│    - Build container image                                                │
│    - Deploy Pod(s)                                                        │
│    - Service is live!                                                     │
└──────────────────────────────────────────────────────────────────────────┘
```

### 4.2 Request Processing Flow

```
┌──────────────────────────────────────────────────────────────────────────┐
│ 1. Client sends request (SOAP/REST)                                       │
│    POST /services/PaymentService_v1                                       │
│    Headers: X-Organization-Id, X-Certificate, etc.                       │
└────────────────────────────┬─────────────────────────────────────────────┘
                             │ HTTPS
┌────────────────────────────▼─────────────────────────────────────────────┐
│ 2. Nginx Ingress                                                          │
│    - SSL termination                                                      │
│    - Route to ESB service                                                 │
└────────────────────────────┬─────────────────────────────────────────────┘
                             │ HTTP
┌────────────────────────────▼─────────────────────────────────────────────┐
│ 3. Vert.x receives request (non-blocking)                                │
│    - Parse request                                                        │
│    - Extract credentials                                                  │
│    - Match route                                                          │
└────────────────────────────┬─────────────────────────────────────────────┘
                             │
┌────────────────────────────▼─────────────────────────────────────────────┐
│ 4. Camel Route execution                                                  │
│    a) CheckAccessProcessor                                                │
│       - Validate credentials (IP, Certificate, API Key, etc.)            │
│       - Check organization access                                         │
│       - Check rate limits                                                 │
│    b) PrepareHeadersProcessor                                             │
│       - Set caller organization ID in SOAP header                        │
│       - Add request ID for tracing                                       │
│    c) Service invocation                                                  │
│       - Local: Call service directly                                      │
│       - Remote: Send to ActiveMQ queue                                   │
│    d) PrepareResultProcessor                                              │
│       - Format response                                                   │
│    e) Log to SEDA channel                                                 │
└────────────────────────────┬─────────────────────────────────────────────┘
                             │
┌────────────────────────────▼─────────────────────────────────────────────┐
│ 5. Response returned to client                                            │
└──────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────┐
│ 6. Async: Log processing                                                  │
│    SEDA channel → Log Processor → Elasticsearch/Kafka                    │
└──────────────────────────────────────────────────────────────────────────┘
```

### 4.3 Configuration Sync Flow

```
┌──────────────────────────────────────────────────────────────────────────┐
│                              ESM                                          │
│  - Admin updates organization credential                                  │
│  - Or: Access expires based on valid_until                               │
└────────────────────────────┬─────────────────────────────────────────────┘
                             │ Publish Event
                             │
┌────────────────────────────▼─────────────────────────────────────────────┐
│                           Kafka                                           │
│  Topic: bita.config.changes                                              │
│  Event: {                                                                 │
│    "eventType": "ORGANIZATION_CREDENTIAL_UPDATED",                       │
│    "timestamp": "2026-01-28T15:30:00Z",                                  │
│    "payload": {                                                          │
│      "organizationId": 123,                                              │
│      "credentialId": 456,                                                │
│      "changeType": "UPDATED"                                             │
│    }                                                                      │
│  }                                                                        │
└────────────────────────────┬─────────────────────────────────────────────┘
                             │ All ESBs consume
                             │
┌────────────────────────────▼─────────────────────────────────────────────┐
│                         ESB-1, ESB-2, ESB-3                               │
│  - Receive event                                                          │
│  - Fetch ONLY changed credential from ESM API                            │
│  - Update local cache                                                     │
│  - No restart needed!                                                     │
└──────────────────────────────────────────────────────────────────────────┘
```

### 4.4 Offline ESB Sync

```
┌──────────────────────────────────────────────────────────────────────────┐
│ Scenario: ESB was offline, comes back online                             │
└────────────────────────────┬─────────────────────────────────────────────┘
                             │
┌────────────────────────────▼─────────────────────────────────────────────┐
│ 1. ESB connects to Kafka                                                  │
│    - Reads from last committed offset                                     │
│    - If too old → publishes FULL_SYNC_REQUESTED event                    │
└────────────────────────────┬─────────────────────────────────────────────┘
                             │
┌────────────────────────────▼─────────────────────────────────────────────┐
│ 2. ESM receives FULL_SYNC_REQUESTED                                       │
│    - Publishes FULL_SYNC event with current state summary                │
└────────────────────────────┬─────────────────────────────────────────────┘
                             │
┌────────────────────────────▼─────────────────────────────────────────────┐
│ 3. ESB performs full sync                                                 │
│    - Fetch all organizations                                              │
│    - Fetch all services                                                   │
│    - Fetch all routes                                                     │
│    - Rebuild local cache                                                  │
│    - Redeploy all integrations                                           │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Security Architecture

### 5.1 Credential Types

The system supports multiple credential types for organization authentication:

| Credential Type | Description | Use Case |
|-----------------|-------------|----------|
| `IP_ADDRESS` | IP whitelist | Legacy systems, internal services |
| `X509_CERTIFICATE` | X.509 certificate | WS-Security 1.1 services |
| `API_KEY` | API key/secret | REST APIs |
| `OAUTH2` | OAuth 2.0 token | Modern integrations |
| `BASIC_AUTH` | Username/password | Simple authentication |

### 5.2 WS-Security 1.1 Implementation

For backward compatibility with existing SOAP services:

```
┌─────────────────────────────────────────────────────────────┐
│                    Client Request                            │
│  - SOAP envelope with WS-Security header                    │
│  - X.509 certificate attached                               │
│  - Signature and encryption                                  │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│                  WSS4JInInterceptor                          │
│  - Validate signature                                        │
│  - Decrypt body                                              │
│  - Extract caller certificate                                │
│  - Validate timestamp                                        │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│               CheckAccessProcessor                           │
│  - Find organization by certificate                          │
│  - Validate access to service                                │
│  - Check rate limits                                         │
│  - Set callerNationalId in SOAP From header                 │
└────────────────────────┬────────────────────────────────────┘
                         │
                    [Route processing]
                         │
┌────────────────────────▼────────────────────────────────────┐
│                 WSS4JOutInterceptor                          │
│  - Sign response with ESB certificate                       │
│  - Encrypt with caller's public key                         │
│  - Add timestamp                                             │
└─────────────────────────────────────────────────────────────┘
```

### 5.3 Rate Limiting

```
Rate Limit Priority:
1. Custom rate limit per organization per service (service_access table)
2. Default rate limit on endpoint (endpoint table)
3. Global default (configuration)

Algorithm: Token Bucket (implemented via Redis or in-memory)
```

---

## 6. Logging Architecture

### 6.1 Logging Strategy

All routes include standardized logging via SEDA channel:

```
┌─────────────────────────────────────────────────────────────┐
│                    Every Route Step                          │
│  - Logs timing information                                   │
│  - Sends to seda:logChannel                                 │
└────────────────────────┬────────────────────────────────────┘
                         │ Async (non-blocking)
┌────────────────────────▼────────────────────────────────────┐
│                    seda:logChannel                           │
│  - Aggregates logs from all routes                          │
│  - Single point for log processing                          │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│                  Log Router (internal route)                 │
│  - Route to Elasticsearch                                    │
│  - Route to Kafka (for analytics)                           │
│  - Route to file (backup)                                   │
└─────────────────────────────────────────────────────────────┘
```

### 6.2 Log Structure

```json
{
  "requestId": "uuid",
  "timestamp": "2026-01-28T15:30:00.123Z",
  "routeId": "PaymentService_v1_route",
  "serviceId": 50,
  "callerOrganizationId": 123,
  "callerIp": "192.168.1.100",
  "steps": [
    {
      "step": "CheckAccessProcessor",
      "startTime": "2026-01-28T15:30:00.123Z",
      "duration_ms": 5,
      "status": "SUCCESS"
    },
    {
      "step": "CallingServiceProcessor",
      "startTime": "2026-01-28T15:30:00.128Z",
      "duration_ms": 150,
      "status": "SUCCESS"
    }
  ],
  "totalDuration_ms": 180,
  "status": "SUCCESS",
  "responseCode": 200
}
```

---

## 7. Scalability and High Availability

### 7.1 ESB Scaling

- **Horizontal Pod Autoscaler**: Based on CPU/memory usage
- **Camel K replicas**: Each Integration can have multiple replicas
- **Zero downtime**: Rolling updates handled by Kubernetes

### 7.2 ESM Scaling

- **Stateless backend**: Can run multiple instances
- **Database connection pooling**: HikariCP
- **Cache layer**: Redis for frequently accessed data

### 7.3 Kafka

- **Multiple brokers**: Minimum 3 for production
- **Partition strategy**: By organization ID for ordering
- **Retention**: 7 days for event replay

---

## 8. Deployment Architecture

### 8.1 Kubernetes Cluster Layout

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Kubernetes Cluster                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Namespace: ingress-nginx                                                    │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │  Nginx Ingress Controller                                               │ │
│  │  - SSL certificates (cert-manager)                                      │ │
│  │  - Load balancing                                                       │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│  Namespace: bita-esm                                                         │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │  ESM Backend (Deployment, 2+ replicas)                                  │ │
│  │  ESM Frontend (Deployment, 2+ replicas)                                 │ │
│  │  PostgreSQL (StatefulSet or managed service)                            │ │
│  │  Redis (StatefulSet or managed service)                                 │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│  Namespace: bita-esb                                                         │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │  Camel K Operator                                                       │ │
│  │  ESB Core (Deployment, 2+ replicas)                                     │ │
│  │  Integration Pods (managed by Camel K)                                  │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│  Namespace: bita-messaging                                                   │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │  Kafka (StatefulSet, 3+ brokers)                                        │ │
│  │  Zookeeper (StatefulSet, 3 nodes)                                       │ │
│  │  ActiveMQ (StatefulSet, for cross-domain)                               │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│  Namespace: bita-monitoring                                                  │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │  Elasticsearch (StatefulSet)                                            │ │
│  │  Kibana (Deployment)                                                    │ │
│  │  Prometheus (StatefulSet)                                               │ │
│  │  Grafana (Deployment)                                                   │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 8.2 Multi-Domain Deployment

For organizations with multiple data centers:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Domain: Tehran                                     │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐             │
│  │     ESM         │  │     ESB         │  │    Kafka        │             │
│  │     Cluster     │  │     Cluster     │  │    Cluster      │             │
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘             │
│           │                    │                    │                       │
│           └────────────────────┼────────────────────┘                       │
│                                │                                            │
└────────────────────────────────┼────────────────────────────────────────────┘
                                 │ ActiveMQ Bridge
┌────────────────────────────────┼────────────────────────────────────────────┐
│                           Domain: Mashhad                                    │
│           ┌────────────────────┼────────────────────┐                       │
│           │                    │                    │                       │
│  ┌────────┴────────┐  ┌───────┴─────────┐  ┌──────┴──────────┐             │
│  │     ESM         │  │     ESB         │  │    Kafka        │             │
│  │     Cluster     │  │     Cluster     │  │    Cluster      │             │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 9. LLM Integration

### 9.1 LLM Service Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    LLM Integration Layer                     │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              LLM Provider Interface                  │   │
│  │  - Abstract interface for LLM providers              │   │
│  │  - Easy to switch between models                     │   │
│  └─────────────────────────────────────────────────────┘   │
│           │                    │                    │       │
│  ┌────────▼────────┐  ┌───────▼───────┐  ┌────────▼──────┐ │
│  │  OpenAI GPT-4   │  │ Anthropic     │  │ Local LLM     │ │
│  │  Provider       │  │ Claude        │  │ (Ollama)      │ │
│  └─────────────────┘  └───────────────┘  └───────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### 9.2 LLM Tools (Function Calling)

The LLM has access to ESM APIs as tools:

```json
{
  "tools": [
    {
      "name": "create_endpoint_template",
      "description": "Create a new endpoint template",
      "parameters": {
        "name": "string",
        "uri_pattern": "string",
        "config_schema": "object"
      }
    },
    {
      "name": "create_endpoint",
      "description": "Create an endpoint instance from a template",
      "parameters": {
        "template_id": "integer",
        "name": "string",
        "config": "object"
      }
    },
    {
      "name": "create_route",
      "description": "Create a route with endpoints and components",
      "parameters": {
        "name": "string",
        "service_id": "integer",
        "endpoints": "array",
        "steps": "array"
      }
    },
    {
      "name": "create_service",
      "description": "Create a new service",
      "parameters": {
        "name": "string",
        "collection_id": "integer",
        "description": "string",
        "phase": "string"
      }
    }
  ]
}
```

### 9.3 Conversation Flow

```
User: "یک سرویس پرداخت با WS-Security 1.1 بساز"

LLM: [Analyzes request]
     [Selects cxf-wssecurity-11-endpoint template]
     [Generates configuration]
     
LLM Response: "برای ساخت این سرویس، قصد دارم:
1. یک Endpoint از نوع CXF با WS-Security 1.1 بسازم
2. یک Route برای این Endpoint تعریف کنم
3. سازمان شما را به عنوان owner تنظیم کنم

آیا تایید می‌کنید؟"

User: "بله"

LLM: [Calls create_endpoint tool]
     [Calls create_route tool]
     [Calls create_service tool]
     
LLM Response: "سرویس پرداخت با موفقیت ساخته شد. 
Route ID: 123
Endpoint URL: /services/PaymentService_v1"
```

---

## 10. Testing Strategy

### 10.1 Service Phases

| Phase | Description | Visibility |
|-------|-------------|------------|
| `DRAFT` | Under development | Only creator |
| `TEST` | Testing phase | Test organization + selected orgs |
| `ACTIVE` | Production | All authorized organizations |

### 10.2 Test Organization

A dedicated test organization with special access:
- Can test any service in TEST phase
- Has testing panel in ESM UI
- Credentials are managed separately

### 10.3 API Testing

All ESM backend APIs must have:
- Unit tests (JUnit 5)
- Integration tests (Testcontainers)
- API contract tests (OpenAPI validation)
- Load tests (Gatling)

---

## 11. Monitoring and Observability

### 11.1 Metrics

**Application Metrics (Micrometer → Prometheus):**
- Request count by service, organization, status
- Response time percentiles (p50, p95, p99)
- Active connections
- Rate limit violations
- Error rates

**Infrastructure Metrics:**
- Pod resource usage
- Kafka consumer lag
- Database connection pool

### 11.2 Tracing

Distributed tracing with Jaeger/Zipkin:
- Request ID propagated through all services
- Trace SOAP/REST requests end-to-end
- Identify bottlenecks

### 11.3 Alerting

Alert rules in Prometheus:
- High error rate (>1% for 5 minutes)
- Service unavailable
- Rate limit exceeded
- Certificate expiration (7 days warning)
- Kafka consumer lag > 1000

---

## 12. Conclusion

This architecture provides:

1. **Modernization**: LLM-powered service creation with backward compatibility
2. **Performance**: Vert.x reactive handling for high throughput
3. **Scalability**: Kubernetes-native with Camel K operator
4. **Reliability**: Event-driven sync with Kafka, zero-downtime updates
5. **Security**: Flexible credential types with WS-Security 1.1 support
6. **Observability**: Comprehensive logging and monitoring

---

**Document Version**: 1.0  
**Last Updated**: January 28, 2026  
**Author**: System Architecture Team
