# Project Modules Specification

## Document Information

**Project**: BITA - Next-Generation Enterprise Service Gateway  
**Version**: 4.0  
**Date**: January 28, 2026  
**Purpose**: Detailed specification of all project modules, services, and APIs

---

## 1. Project Overview

```
bita/
├── bita-common/               # Shared Library
├── bita-esm-backend/          # ESM Backend (Spring Boot)
├── bita-esm-frontend/         # ESM Frontend (React + TypeScript)
├── bita-esb-core/             # ESB Core (Vert.x + Camel)
└── bita-infra/                # Infrastructure (Docker, K8s, Helm)
```

---

## 2. bita-common (Shared Library)

### 2.1 Purpose
Shared code between ESM and ESB including DTOs, events, utilities, and common processors.

### 2.2 Package Structure

```
bita-common/
└── src/main/java/ir/iais/bita/common/
    ├── domain/                    # Shared domain primitives
    │   ├── ClientId.java
    │   ├── ServiceId.java
    │   ├── RouteId.java
    │   ├── CredentialType.java
    │   ├── ServicePhase.java
    │   └── AccessStatus.java
    ├── dto/                       # Data Transfer Objects
    │   ├── client/
    │   │   ├── ClientDto.java
    │   │   ├── CredentialDto.java
    │   │   └── CreateClientRequest.java
    │   ├── service/
    │   │   ├── ServiceDto.java
    │   │   ├── ServiceCollectionDto.java
    │   │   └── ServiceAccessDto.java
    │   ├── route/
    │   │   ├── RouteDto.java
    │   │   ├── EndpointDto.java
    │   │   ├── ComponentDto.java
    │   │   └── RouteDefinitionDto.java
    │   └── sync/
    │       ├── FullSyncDataDto.java
    │       └── ConfigChangeEventDto.java
    ├── event/                     # Domain Events
    │   ├── DomainEvent.java       # Base class
    │   ├── client/
    │   │   ├── ClientCreated.java
    │   │   ├── ClientUpdated.java
    │   │   ├── CredentialAdded.java
    │   │   └── CredentialRemoved.java
    │   ├── service/
    │   │   ├── ServiceCreated.java
    │   │   ├── ServicePhaseChanged.java
    │   │   ├── AccessGranted.java
    │   │   └── AccessRevoked.java
    │   └── route/
    │       ├── RouteCreated.java
    │       ├── RouteUpdated.java
    │       └── RouteDeleted.java
    ├── processor/                 # Shared Camel Processors
    │   ├── CheckAccessProcessor.java
    │   ├── PrepareHeadersProcessor.java
    │   ├── CallingServiceProcessor.java
    │   ├── PrepareResultProcessor.java
    │   └── TimingLogProcessor.java
    ├── security/                  # Security Utilities
    │   ├── PasswordCallbackHandler.java
    │   ├── CertificateUtils.java
    │   └── CredentialValidator.java
    └── util/                      # Utilities
        ├── JsonUtils.java
        └── ValidationUtils.java
```

### 2.3 Dependencies

```xml
<dependencies>
    <dependency>
        <groupId>org.apache.camel</groupId>
        <artifactId>camel-core</artifactId>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>
    <dependency>
        <groupId>jakarta.validation</groupId>
        <artifactId>jakarta.validation-api</artifactId>
    </dependency>
</dependencies>
```

---

## 3. bita-esm-backend (ESM Backend)

### 3.1 Purpose
Central management platform for clients, services, routes, and access control. Provides REST APIs and LLM integration.

### 3.2 Technology Stack
- Java 17+
- Spring Boot 3.x
- Spring Security 6.x
- Hibernate 6.x with Envers
- PostgreSQL 15+
- Redis (caching)
- Apache Kafka (events)
- **Kubernetes Java Client** (auto-deployment when Service → ACTIVE)

### 3.3 Module Structure (DDD)

```
bita-esm-backend/
└── src/main/java/ir/iais/bita/esm/
    │
    ├── client/                    # Client Management Context
    │   ├── domain/
    │   │   ├── model/
    │   │   │   ├── Client.java              # Aggregate Root
    │   │   │   ├── Credential.java          # Entity
    │   │   │   └── ClientTag.java           # Value Object
    │   │   ├── repository/
    │   │   │   └── ClientRepository.java
    │   │   └── event/
    │   │       └── ClientDomainEvents.java
    │   ├── application/
    │   │   ├── command/
    │   │   │   ├── CreateClientCommand.java
    │   │   │   ├── UpdateClientCommand.java
    │   │   │   ├── DeleteClientCommand.java
    │   │   │   ├── AddCredentialCommand.java
    │   │   │   └── RemoveCredentialCommand.java
    │   │   ├── query/
    │   │   │   ├── GetClientQuery.java
    │   │   │   ├── ListClientsQuery.java
    │   │   │   └── SearchClientsQuery.java
    │   │   └── service/
    │   │       └── ClientApplicationService.java
    │   └── interfaces/
    │       └── rest/
    │           └── ClientController.java
    │
    ├── service/                   # Service Management Context
    │   ├── domain/
    │   │   ├── model/
    │   │   │   ├── ServiceCollection.java   # Aggregate Root
    │   │   │   ├── Service.java             # Entity
    │   │   │   └── ServiceAccess.java       # Aggregate Root
    │   │   └── repository/
    │   │       ├── ServiceCollectionRepository.java
    │   │       ├── ServiceRepository.java
    │   │       └── ServiceAccessRepository.java
    │   ├── application/
    │   │   ├── command/
    │   │   │   ├── CreateServiceCollectionCommand.java
    │   │   │   ├── CreateServiceCommand.java
    │   │   │   ├── ChangeServicePhaseCommand.java
    │   │   │   ├── GrantAccessCommand.java
    │   │   │   └── RevokeAccessCommand.java
    │   │   ├── query/
    │   │   │   ├── GetServiceQuery.java
    │   │   │   ├── ListServicesQuery.java
    │   │   │   └── CheckAccessQuery.java
    │   │   └── scheduler/
    │   │       └── AccessExpirationScheduler.java
    │   └── interfaces/
    │       └── rest/
    │           ├── ServiceCollectionController.java
    │           ├── ServiceController.java
    │           └── ServiceAccessController.java
    │
    ├── route/                     # Route Configuration Context
    │   ├── domain/
    │   │   ├── model/
    │   │   │   ├── template/
    │   │   │   │   ├── EndpointTemplate.java
    │   │   │   │   ├── ComponentTemplate.java
    │   │   │   │   └── RouteTemplate.java
    │   │   │   └── instance/
    │   │   │       ├── Endpoint.java
    │   │   │       ├── Component.java
    │   │   │       ├── Route.java           # Aggregate Root
    │   │   │       └── RouteFile.java
    │   │   └── repository/
    │   │       └── ...
    │   ├── application/
    │   │   ├── command/
    │   │   │   ├── CreateEndpointTemplateCommand.java
    │   │   │   ├── CreateEndpointCommand.java
    │   │   │   ├── CreateRouteCommand.java
    │   │   │   └── AddRouteFileCommand.java
    │   │   └── service/
    │   │       ├── TemplateResolverService.java
    │   │       └── RouteValidationService.java
    │   └── interfaces/
    │       └── rest/
    │           ├── TemplateController.java
    │           ├── EndpointController.java
    │           ├── ComponentController.java
    │           └── RouteController.java
    │
    ├── llm/                       # LLM Integration Context
    │   ├── domain/
    │   │   ├── model/
    │   │   │   ├── ChatSession.java
    │   │   │   ├── ChatMessage.java
    │   │   │   └── ToolExecution.java
    │   │   └── service/
    │   │       └── LlmProvider.java         # Interface
    │   ├── application/
    │   │   ├── command/
    │   │   │   ├── StartChatCommand.java
    │   │   │   ├── SendMessageCommand.java
    │   │   │   └── ConfirmToolCallCommand.java
    │   │   └── tools/                       # LLM Tools (API wrappers)
    │   │       ├── LlmTool.java             # Interface
    │   │       ├── ToolRegistry.java
    │   │       ├── ListClientsTool.java
    │   │       ├── GetClientTool.java
    │   │       ├── CreateServiceTool.java
    │   │       ├── CreateRouteTool.java
    │   │       ├── ListTemplatesTools.java
    │   │       └── CreateEndpointTool.java
    │   ├── infrastructure/
    │   │   └── provider/
    │   │       ├── OpenAiProvider.java
    │   │       └── OllamaProvider.java
    │   └── interfaces/
    │       └── rest/
    │           └── ChatController.java
    │
    ├── sync/                      # ESB Synchronization Context
    │   ├── application/
    │   │   ├── service/
    │   │   │   └── SyncDataService.java
    │   │   └── event/
    │   │       └── EventPublisher.java
    │   └── interfaces/
    │       └── rest/
    │           └── SyncController.java      # Internal APIs for ESB
    │
    ├── k8s/                       # Kubernetes Deployer (Auto-deploy when ACTIVE)
    │   ├── service/
    │   │   └── KubernetesDeployerService.java
    │   ├── builder/
    │   │   ├── DeploymentBuilder.java
    │   │   ├── K8sServiceBuilder.java
    │   │   ├── HpaBuilder.java
    │   │   └── IngressManager.java
    │   └── config/
    │       └── KubernetesConfig.java
    │
    ├── auth/                      # Authentication & Authorization
    │   ├── domain/
    │   │   ├── model/
    │   │   │   ├── User.java
    │   │   │   ├── Role.java
    │   │   │   └── Permission.java
    │   │   └── repository/
    │   │       └── UserRepository.java
    │   ├── application/
    │   │   ├── command/
    │   │   │   ├── CreateUserCommand.java
    │   │   │   └── UpdateUserRolesCommand.java
    │   │   └── service/
    │   │       └── AuthenticationService.java
    │   ├── infrastructure/
    │   │   └── security/
    │   │       ├── SecurityConfig.java
    │   │       ├── JwtTokenProvider.java
    │   │       └── CustomUserDetailsService.java
    │   └── interfaces/
    │       └── rest/
    │           └── AuthController.java
    │
    └── shared/                    # Shared Infrastructure
        ├── config/
        │   ├── JpaConfig.java
        │   ├── KafkaConfig.java
        │   └── RedisConfig.java
        ├── audit/
        │   ├── AuditRevisionEntity.java
        │   └── AuditRevisionListener.java
        └── exception/
            ├── GlobalExceptionHandler.java
            └── BusinessException.java
```

### 3.4 REST API Summary

#### Client Management APIs
| Method | Endpoint | Permission | Description |
|--------|----------|------------|-------------|
| POST | /api/v1/clients | CLIENT_WRITE | Create client |
| GET | /api/v1/clients | CLIENT_READ | List clients |
| GET | /api/v1/clients/{id} | CLIENT_READ | Get client |
| PUT | /api/v1/clients/{id} | CLIENT_WRITE | Update client |
| DELETE | /api/v1/clients/{id} | CLIENT_WRITE | Soft delete client |
| POST | /api/v1/clients/{id}/credentials | CLIENT_WRITE | Add credential |
| DELETE | /api/v1/clients/{id}/credentials/{credId} | CLIENT_WRITE | Remove credential |

#### Service Management APIs
| Method | Endpoint | Permission | Description |
|--------|----------|------------|-------------|
| POST | /api/v1/service-collections | SERVICE_WRITE | Create collection |
| GET | /api/v1/service-collections | SERVICE_READ | List collections |
| POST | /api/v1/services | SERVICE_WRITE | Create service |
| GET | /api/v1/services | SERVICE_READ | List services |
| GET | /api/v1/services/{id} | SERVICE_READ | Get service with K8s status |
| PATCH | /api/v1/services/{id}/phase | SERVICE_WRITE | Change phase (AUTO-DEPLOYS when ACTIVE) |
| PATCH | /api/v1/services/{id}/scaling | SERVICE_WRITE | Update min/max replicas |
| POST | /api/v1/service-access | ACCESS_WRITE | Grant access |
| DELETE | /api/v1/service-access/{id} | ACCESS_WRITE | Revoke access |

**Note:** When phase changes to ACTIVE, ESM Backend automatically:
1. Creates K8s Deployment (`{collection}-{version}-esb`)
2. Creates K8s Service (`{collection}-{version}-esb-svc`)
3. Creates HPA if max_replicas > min_replicas
4. Adds path rule to Ingress (`/esb/{collection}/{version}/*`)

#### Route Management APIs
| Method | Endpoint | Permission | Description |
|--------|----------|------------|-------------|
| POST | /api/v1/endpoint-templates | TEMPLATE_WRITE | Create endpoint template |
| POST | /api/v1/component-templates | TEMPLATE_WRITE | Create component template |
| POST | /api/v1/route-templates | TEMPLATE_WRITE | Create route template |
| POST | /api/v1/endpoints | ROUTE_WRITE | Create endpoint |
| POST | /api/v1/components | ROUTE_WRITE | Create component |
| POST | /api/v1/routes | ROUTE_WRITE | Create route |
| POST | /api/v1/routes/{id}/files | ROUTE_WRITE | Add route file |
| GET | /api/v1/routes/{id}/kamel-yaml | ROUTE_READ | Get Kamel YAML |

#### LLM Chat APIs
| Method | Endpoint | Permission | Description |
|--------|----------|------------|-------------|
| POST | /api/v1/chat/sessions | CHAT_ACCESS | Start session |
| POST | /api/v1/chat/sessions/{id}/messages | CHAT_ACCESS | Send message |
| POST | /api/v1/chat/sessions/{id}/confirm | CHAT_ACCESS | Confirm tool call |
| GET | /api/v1/chat/sessions/{id}/history | CHAT_ACCESS | Get history |

#### Authentication APIs
| Method | Endpoint | Permission | Description |
|--------|----------|------------|-------------|
| POST | /api/v1/auth/login | PUBLIC | Login |
| POST | /api/v1/auth/logout | AUTHENTICATED | Logout |
| POST | /api/v1/auth/refresh | AUTHENTICATED | Refresh token |
| GET | /api/v1/auth/me | AUTHENTICATED | Get current user |
| POST | /api/v1/users | USER_ADMIN | Create user |
| PUT | /api/v1/users/{id}/roles | USER_ADMIN | Update roles |

#### Sync APIs (Internal - ESB only)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /internal/v1/sync/clients | Get all clients with credentials |
| GET | /internal/v1/sync/services | Get all services |
| GET | /internal/v1/sync/routes | Get all active routes |
| GET | /internal/v1/sync/access | Get all access records |
| GET | /internal/v1/sync/full | Get complete sync data |

### 3.5 User Roles and Permissions

```java
public enum Role {
    ADMIN,           // Full access to everything
    SERVICE_MANAGER, // Manage services and routes
    CLIENT_MANAGER,  // Manage clients and credentials
    ACCESS_MANAGER,  // Manage service access
    VIEWER           // Read-only access
}

public enum Permission {
    // Client
    CLIENT_READ, CLIENT_WRITE,
    // Service
    SERVICE_READ, SERVICE_WRITE,
    // Access
    ACCESS_READ, ACCESS_WRITE,
    // Route & Template
    ROUTE_READ, ROUTE_WRITE,
    TEMPLATE_READ, TEMPLATE_WRITE,
    // Chat
    CHAT_ACCESS,
    // Admin
    USER_ADMIN
}

// Role to Permission mapping
ADMIN -> ALL PERMISSIONS
SERVICE_MANAGER -> SERVICE_*, ROUTE_*, TEMPLATE_*, CHAT_ACCESS
CLIENT_MANAGER -> CLIENT_*
ACCESS_MANAGER -> ACCESS_*, SERVICE_READ, CLIENT_READ
VIEWER -> *_READ
```

---

## 4. bita-esm-frontend (ESM Frontend)

### 4.1 Purpose
Web-based admin interface for ESM with LLM chat integration.

### 4.2 Technology Stack
- React 18+
- TypeScript 5.x
- TanStack Query (React Query)
- React Router 6
- Zustand (state management)
- React Flow (diagrams)
- Tailwind CSS
- Shadcn/ui components

### 4.3 Recommended Template
**Shadcn Admin Dashboard**: https://github.com/satnaing/shadcn-admin

Features included:
- Authentication (login, register, forgot password)
- Dashboard layout
- Sidebar navigation
- Dark/Light mode
- Responsive design
- Data tables
- Forms with validation

### 4.4 Page Structure

```
bita-esm-frontend/
└── src/
    ├── app/                       # App entry
    │   └── App.tsx
    ├── pages/                     # Page components
    │   ├── auth/
    │   │   ├── LoginPage.tsx
    │   │   └── ForgotPasswordPage.tsx
    │   ├── dashboard/
    │   │   └── DashboardPage.tsx
    │   ├── clients/
    │   │   ├── ClientListPage.tsx
    │   │   ├── ClientDetailPage.tsx
    │   │   └── ClientFormPage.tsx
    │   ├── services/
    │   │   ├── ServiceListPage.tsx
    │   │   ├── ServiceDetailPage.tsx
    │   │   └── ServiceFormPage.tsx
    │   ├── routes/
    │   │   ├── RouteListPage.tsx
    │   │   ├── RouteDetailPage.tsx
    │   │   ├── RouteFormPage.tsx
    │   │   └── RouteDiagramPage.tsx
    │   ├── templates/
    │   │   ├── TemplateListPage.tsx
    │   │   └── TemplateFormPage.tsx
    │   ├── access/
    │   │   ├── AccessListPage.tsx
    │   │   └── AccessFormPage.tsx
    │   ├── chat/
    │   │   └── ChatPage.tsx          # LLM Chat Interface
    │   ├── users/
    │   │   ├── UserListPage.tsx
    │   │   └── UserFormPage.tsx
    │   └── settings/
    │       └── SettingsPage.tsx
    ├── features/                  # Feature modules
    │   ├── auth/
    │   │   ├── api.ts
    │   │   ├── hooks.ts
    │   │   └── store.ts
    │   ├── clients/
    │   │   ├── api.ts
    │   │   ├── hooks.ts
    │   │   └── components/
    │   │       ├── ClientTable.tsx
    │   │       └── CredentialForm.tsx
    │   ├── services/
    │   ├── routes/
    │   │   └── components/
    │   │       └── RouteDiagram.tsx   # React Flow diagram
    │   └── chat/
    │       └── components/
    │           ├── ChatWindow.tsx
    │           ├── MessageList.tsx
    │           └── ToolConfirmation.tsx
    ├── components/                # Shared components
    │   ├── ui/                    # Shadcn components
    │   ├── layout/
    │   │   ├── Sidebar.tsx
    │   │   ├── Header.tsx
    │   │   └── MainLayout.tsx
    │   └── common/
    │       ├── DataTable.tsx
    │       ├── ConfirmDialog.tsx
    │       └── LoadingSpinner.tsx
    ├── hooks/                     # Custom hooks
    │   ├── useAuth.ts
    │   └── usePermission.ts
    ├── lib/                       # Utilities
    │   ├── api.ts                 # Axios instance
    │   └── utils.ts
    └── types/                     # TypeScript types
        └── index.ts
```

### 4.5 Pages Description

| Page | Path | Permission | Description |
|------|------|------------|-------------|
| Login | /login | Public | User authentication |
| Dashboard | / | Authenticated | Overview stats |
| Clients | /clients | CLIENT_READ | List all clients |
| Client Detail | /clients/:id | CLIENT_READ | Client details + credentials |
| Services | /services | SERVICE_READ | List services |
| Service Detail | /services/:id | SERVICE_READ | Service details + routes |
| Routes | /routes | ROUTE_READ | List routes |
| Route Diagram | /routes/:id/diagram | ROUTE_READ | Visual route editor |
| Templates | /templates | TEMPLATE_READ | List templates |
| Access Management | /access | ACCESS_READ | Manage access |
| Chat | /chat | CHAT_ACCESS | LLM chat interface |
| Users | /users | USER_ADMIN | Manage users |
| Settings | /settings | Authenticated | User settings |

---

## 5. bita-esb-core (ESB Core)

### 5.1 Purpose
High-performance service gateway. Each Service (with version) gets its own pod group for fast startup and independent scaling.

### 5.2 Technology Stack
- Java 17+
- Vert.x 4.x (HTTP server)
- Apache Camel 4.x (routing)
- Apache CXF 4.x (SOAP/WS-Security)
- Apache Kafka (event consumption)

### 5.3 Architecture

**One Pod Group per Service (with version):**

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           Nginx Ingress                                  │
│   /esb/payment/1.0/* → payment-1-0-esb-svc                              │
│   /esb/payment/1.1/* → payment-1-1-esb-svc                              │
│   /esb/user/2.0/*    → user-2-0-esb-svc                                 │
└────────────────────────────────┬────────────────────────────────────────┘
                                 │
        ┌────────────────────────┼────────────────────────┐
        ▼                        ▼                        ▼
┌───────────────────┐  ┌───────────────────┐  ┌───────────────────┐
│ payment-1-0-esb   │  │ payment-1-1-esb   │  │ user-2-0-esb      │
│ (Deployment)      │  │ (Deployment)      │  │ (Deployment)      │
│                   │  │                   │  │                   │
│ SERVICE_ID=1      │  │ SERVICE_ID=2      │  │ SERVICE_ID=5      │
│                   │  │                   │  │                   │
│ Routes for        │  │ Routes for        │  │ Routes for        │
│ PaymentSvc v1.0   │  │ PaymentSvc v1.1   │  │ UserSvc v2.0      │
│ only              │  │ only              │  │ only              │
│                   │  │                   │  │                   │
│ Replicas: 2       │  │ Replicas: 2       │  │ Replicas: 3       │
│ (HPA: 2-10)       │  │ (HPA: 1-5)        │  │ (HPA: 2-8)        │
└───────────────────┘  └───────────────────┘  └───────────────────┘
```

**How it works:**
1. ESB Core image is generic: `bita/esb-core:latest`
2. Each Deployment passes `SERVICE_ID` as environment variable
3. ESB Core loads only routes for that specific Service from ESM API
4. Fast startup (few routes per pod)
5. Independent scaling per Service

### 5.4 ESB Core Pod Structure

```
┌─────────────────────────────────────────────────────────────┐
│           ESB Core Pod (e.g., payment-1-0-esb)              │
│                    SERVICE_ID=1                              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              Vert.x HTTP Server                      │   │
│  │  - Non-blocking request reception                    │   │
│  │  - All requests for this Service enter here         │   │
│  └─────────────────────────┬───────────────────────────┘   │
│                            │                                 │
│  ┌─────────────────────────▼───────────────────────────┐   │
│  │              Camel Routes (for this Service only)    │   │
│  │  ─────────────────────────────────────────────────  │   │
│  │  route-1: /esb/payment/1.0/soap → cxf:PaymentSvc   │   │
│  │  route-2: /esb/payment/1.0/api  → http:backend     │   │
│  │  route-log: seda:log → elasticsearch               │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              Kafka Event Consumer                    │   │
│  │  - Listens for: ROUTE_UPDATED, ACCESS_CHANGED       │   │
│  │  - Only processes events for SERVICE_ID=1           │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              Cache (In-Memory)                       │   │
│  │  - Clients & credentials (filtered for this Service)│   │
│  │  - Access rules for this Service                    │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

**Key Points:**
- Each Service → separate Deployment (auto-created by ESM Backend)
- Fast startup: only loads routes for one Service
- Independent scaling: each Service can scale based on its load
- Ingress routes by path prefix: `/esb/{collection}/{version}/*`

### 5.5 Package Structure

```
bita-esb-core/
└── src/main/java/ir/iais/bita/esb/
    ├── config/                    # Configuration
    │   ├── VertxConfig.java
    │   ├── CamelConfig.java
    │   └── KafkaConfig.java
    ├── http/                      # Vert.x HTTP handling
    │   ├── HttpServerVerticle.java
    │   ├── RequestRouter.java
    │   └── ResponseHandler.java
    ├── route/                     # Route management
    │   ├── RouteManager.java
    │   ├── RouteLoader.java
    │   └── DynamicRouteBuilder.java
    ├── sync/                      # ESM synchronization
    │   ├── SyncService.java
    │   ├── EsmApiClient.java
    │   └── EventConsumer.java
    ├── cache/                     # Caching
    │   ├── ClientCache.java
    │   ├── CredentialCache.java
    │   ├── AccessCache.java
    │   └── RouteCache.java
    ├── log/                       # Logging
    │   ├── LogAggregator.java
    │   ├── LogProcessor.java
    │   └── LogSender.java
    └── health/                    # Health checks
        └── HealthCheckVerticle.java
```

### 5.5 Request Processing Flow

```
1. HTTP Request arrives at Vert.x
   ↓
2. RequestRouter matches request to route
   ↓
3. Camel Route executes:
   a) TimingLogProcessor.start()      → Log start time
   b) CheckAccessProcessor            → Validate credentials & access
   c) PrepareHeadersProcessor         → Add caller info to headers
   d) Route-specific processing       → Based on route definition
   e) Target invocation              → Local service or remote queue
   f) PrepareResultProcessor         → Format response
   g) TimingLogProcessor.end()       → Log end time, send to SEDA
   ↓
4. Response returned via Vert.x
   ↓
5. Async: SEDA → LogAggregator → Elasticsearch/Kafka
```

### 5.7 Startup & Event Handling

Each ESB Core pod loads routes only for its SERVICE_ID:

```java
@Component
public class EsbCoreStartup {
    
    @Value("${SERVICE_ID}")
    private Long serviceId;  // From environment variable
    
    private final EsmApiClient esmClient;
    private final RouteManager routeManager;
    private final CacheManager cacheManager;
    
    @PostConstruct
    public void onStartup() {
        log.info("Starting ESB Core for Service ID: {}", serviceId);
        
        // 1. Load routes for THIS service only
        List<RouteDto> routes = esmClient.getRoutesByService(serviceId);
        log.info("Loading {} routes for service {}", routes.size(), serviceId);
        
        for (RouteDto route : routes) {
            routeManager.addRoute(route);
        }
        
        // 2. Load access rules for this service
        List<ServiceAccessDto> accessList = esmClient.getAccessByService(serviceId);
        cacheManager.loadAccess(accessList);
        
        // 3. Load clients with credentials that have access
        Set<Long> clientIds = accessList.stream()
            .map(ServiceAccessDto::clientId)
            .collect(Collectors.toSet());
        List<ClientDto> clients = esmClient.getClientsByIds(clientIds);
        cacheManager.loadClients(clients);
        
        log.info("ESB Core ready for Service ID: {}", serviceId);
    }
}

@Component
public class EventConsumer {
    
    @Value("${SERVICE_ID}")
    private Long serviceId;
    
    @KafkaListener(topics = "bita.config.changes")
    public void handleEvent(ConfigChangeEvent event) {
        // Only process events relevant to THIS service
        Long eventServiceId = event.getPayload().getServiceId();
        
        if (eventServiceId != null && !eventServiceId.equals(serviceId)) {
            return;  // Ignore events for other services
        }
        
        switch (event.getEventType()) {
            // Route events for this service
            case "ROUTE_CREATED":
            case "ROUTE_UPDATED":
                RouteDto route = esmClient.getRoute(event.getAggregateId());
                if (route.serviceId().equals(serviceId)) {
                    routeManager.updateRoute(route);
                }
                break;
            
            case "ROUTE_DELETED":
                routeManager.removeRoute(event.getAggregateId());
                break;
            
            // Access events for this service
            case "ACCESS_GRANTED":
            case "ACCESS_REVOKED":
            case "ACCESS_EXPIRED":
                syncService.refreshAccessForService(serviceId);
                break;
            
            // Client credential changes
            case "CREDENTIAL_ADDED":
            case "CREDENTIAL_REMOVED":
                Long clientId = event.getAggregateId();
                if (cacheManager.hasClient(clientId)) {
                    syncService.refreshClient(clientId);
                }
                break;
        }
    }
}
```

---

## 6. bita-infra (Infrastructure)

### 7.1 Purpose
Docker, Kubernetes manifests, and Helm charts for deployment.

### 7.2 Structure

```
bita-infra/
├── docker/
│   ├── esm-backend/
│   │   └── Dockerfile
│   ├── esm-frontend/
│   │   └── Dockerfile
│   ├── esb-core/
│   │   └── Dockerfile
│   └── docker-compose.yml         # Local development
├── kubernetes/
│   ├── namespaces.yaml
│   ├── esm/
│   │   ├── deployment.yaml
│   │   ├── service.yaml
│   │   └── ingress.yaml
│   ├── esb/
│   │   ├── deployment.yaml
│   │   └── service.yaml
│   └── messaging/
│       ├── kafka.yaml
│       └── activemq.yaml
├── helm/
│   └── bita/
│       ├── Chart.yaml
│       ├── values.yaml
│       └── templates/
└── scripts/
    ├── setup-local.sh             # Local development setup
    └── deploy.sh                  # Production deployment
```

---

## 7. Project Dependencies

```
┌──────────────────────────────────────────────────────────────┐
│                    Dependency Graph                           │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│  bita-common ◄───────────────────────────────────────────┐   │
│       │                                                   │   │
│       ├───────────────────────────────────────────────┐   │   │
│       │                                               │   │   │
│       ▼                                               │   │   │
│  bita-esm-backend                                     │   │   │
│       │                                               │   │   │
│       │ REST APIs                                     │   │   │
│       ▼                                               │   │   │
│  bita-esm-frontend                                    │   │   │
│                                                       │   │   │
│  bita-esb-core ◄──────────────────────────────────────┘   │   │
│       │                                                   │   │
│       └───────────────────────────────────────────────────┘   │
│                                                               │
└──────────────────────────────────────────────────────────────┘

Connection Methods:
- bita-common: Maven dependency (shared DTOs, events, processors)
- bita-esm-backend ↔ bita-esm-frontend: REST APIs (HTTP)
- bita-esm-backend ↔ bita-esb-core: REST APIs (sync) + Kafka (events)
```

---

## 9. External Dependencies

| Component | Connection | Purpose |
|-----------|------------|---------|
| PostgreSQL | JDBC (port 5432) | ESM database |
| Redis | TCP (port 6379) | Caching |
| Kafka | TCP (port 9092) | Event streaming |
| Elasticsearch | HTTP (port 9200) | Logging |
| Kubernetes API | HTTPS (port 6443) | Integration deployment |
| OpenAI API | HTTPS | LLM integration |

---

**Document Version**: 1.0  
**Last Updated**: January 28, 2026
