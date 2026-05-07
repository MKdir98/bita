# Task Breakdown Document

## Document Information

**Project**: BITA - Next-Generation Enterprise Service Gateway  
**Version**: 4.0  
**Date**: January 28, 2026  
**Purpose**: Development tasks for all projects

---

## Task Status Legend

| Status | Description |
|--------|-------------|
| 📋 TODO | Not started |
| 🔄 IN_PROGRESS | Being worked on |
| 👀 IN_REVIEW | Code review |
| ✅ DONE | Completed |
| ⏸️ BLOCKED | Blocked by dependency |
| ❌ CANCELLED | No longer needed |

## Task Assignment Format

```
Assignee: [Name]
Started: [Date]
Updated: [Date]
```

---

## Phase 1: Infrastructure Setup

### P1-001: Project Structure Setup
**Status**: ✅ DONE  
**Assignee**: Claude AI  
**Started**: 2026-02-04  
**Completed**: 2026-02-04  
**Priority**: Critical  
**Estimated**: 2 hours

**Description**: Create Maven multi-module project structure

**Tasks**:
- [x] Create parent `pom.xml` with dependency management
- [x] Create `bita-common` module
- [x] Create `bita-esm-backend` module
- [x] Create `bita-esm-frontend` module (React)
- [x] Create `bita-esb-core` module
- [x] Create `bita-infra` module
- [x] Setup `.gitignore`
- [x] Setup `README.md`

**Deliverables**:
```
bita/
├── pom.xml
├── bita-common/
├── bita-esm-backend/
├── bita-esm-frontend/
├── bita-esb-core/
└── bita-infra/
```

---

### P1-002: Docker Compose for Local Development
**Status**: ✅ DONE  
**Assignee**: Claude AI  
**Started**: 2026-02-04  
**Completed**: 2026-02-04  
**Priority**: Critical  
**Estimated**: 3 hours  
**Depends on**: -

**Description**: Create Docker Compose for local infrastructure

**Tasks**:
- [x] PostgreSQL container
- [x] Redis container
- [x] Kafka + Zookeeper containers
- [x] Elasticsearch container
- [x] Kafka UI container (dev tools)
- [x] Test connectivity between services

**Deliverables**:
- `bita-infra/docker/docker-compose.yml`
- `bita-infra/docker/docker-compose.dev.yml` (for development overrides)

---

### P1-003: Minikube Setup Script
**Status**: ✅ DONE  
**Assignee**: Claude AI  
**Started**: 2026-02-04  
**Completed**: 2026-02-04  
**Priority**: High  
**Estimated**: 4 hours  
**Depends on**: P1-002

**Description**: Create script for Minikube setup with hybrid Docker/K8s

**Tasks**:
- [x] Create `setup-minikube.sh` script
- [x] Start Minikube with required addons (ingress, metrics-server, dashboard, registry)
- [x] Create namespaces (bita, bita-dev, bita-test, monitoring)
- [x] Setup Docker environment integration
- [x] Configure local DNS instructions
- [x] Create `teardown-minikube.sh` script
- [x] Create `build-images.sh` script for Docker image builds
- [x] Document setup process

**Deliverables**:
- `bita-infra/scripts/setup-local.sh`
- `bita-infra/scripts/teardown-local.sh`
- `bita-infra/kubernetes/local/`

---

## Phase 2: bita-common Module

### P2-001: Domain Primitives
**Status**: ✅ DONE  
**Assignee**: Claude AI  
**Started**: 2026-02-04  
**Completed**: 2026-02-04  
**Priority**: Critical  
**Estimated**: 4 hours  
**Depends on**: P1-001

**Description**: Create shared domain value objects and enums

**Tasks**:
- [x] `ClientId.java` - Value Object
- [x] `ServiceId.java` - Value Object
- [x] `RouteId.java` - Value Object
- [x] `CredentialType.java` - Enum (IP_ADDRESS, X509_CERTIFICATE, API_KEY, OAUTH2, BASIC_AUTH)
- [x] `ServicePhase.java` - Enum (DRAFT, TEST, ACTIVE)
- [x] `AccessStatus.java` - Enum (ACTIVE, REVOKED, EXPIRED)
- [x] `ComponentType.java` - Enum (PROCESSOR, BEAN)
- [x] `FileType.java` - Enum (WSDL, XSD, PROPERTIES, JAVA, JAR, OTHER)
- [x] Unit tests for all value objects

**Test Scenarios**:
```java
// Given a valid ID value
// When creating a ClientId
// Then it should be created successfully

// Given an invalid ID value (negative or zero)
// When creating a ClientId
// Then it should throw InvalidIdException
```

---

### P2-002: DTOs
**Status**: ✅ DONE  
**Assignee**: Claude AI  
**Started**: 2026-02-04  
**Completed**: 2026-02-04  
**Priority**: Critical  
**Estimated**: 6 hours  
**Depends on**: P2-001

**Description**: Create shared DTOs for API communication

**Tasks**:
- [x] Client DTOs: `ClientDto`, `CreateClientRequest`, `UpdateClientRequest`
- [x] Credential DTOs: `CredentialDto`, `AddCredentialRequest`
- [x] Service DTOs: `ServiceDto`, `ServiceCollectionDto`, `CreateServiceRequest`
- [x] Route DTOs: `RouteDto`, `EndpointDto`, `ComponentDto`
- [x] Access DTOs: `ServiceAccessDto`, `GrantAccessRequest`
- [x] Sync DTOs: `FullSyncDataDto`, `ConfigChangeEventDto`
- [x] Validation annotations on all DTOs
- [x] Unit tests for validation

**Test Scenarios**:
```java
// Given a CreateClientRequest with empty name
// When validating the request
// Then validation should fail with "name is required" error

// Given a CreateClientRequest with valid data
// When validating the request
// Then validation should pass
```

---

### P2-003: Domain Events
**Status**: ✅ DONE  
**Assignee**: Claude AI  
**Started**: 2026-02-04  
**Completed**: 2026-02-04  
**Priority**: High  
**Estimated**: 4 hours  
**Depends on**: P2-001

**Description**: Create domain events for Kafka messaging

**Tasks**:
- [x] `DomainEvent.java` - Base class with timestamp, eventId
- [x] Client events: `ClientCreated`, `ClientUpdated`, `ClientDeleted`
- [x] Credential events: `CredentialAdded`, `CredentialRemoved`
- [x] Service events: `ServiceCreated`, `ServicePhaseChanged`
- [x] Access events: `AccessGranted`, `AccessRevoked`, `AccessExpired`
- [x] Route events: `RouteCreated`, `RouteUpdated`, `RouteDeleted`, `RouteDeactivated`
- [x] JSON serialization/deserialization tests

---

### P2-004: Shared Camel Processors
**Status**: ✅ DONE  
**Assignee**: Claude AI  
**Started**: 2026-02-04  
**Completed**: 2026-02-04  
**Priority**: High  
**Estimated**: 8 hours  
**Depends on**: P2-001

**Description**: Create Camel processors used in routes

**Tasks**:
- [x] `LoggingProcessor.java` - Request/response logging with context tracking
- [x] `ValidationProcessor.java` - XML/JSON schema validation
- [x] `TransformProcessor.java` - XSLT and JSON mapping transformations
- [x] `ErrorHandlerProcessor.java` - Standardized error handling (JSON, XML, SOAP Fault)

---

### P2-005: Security Utilities
**Status**: ✅ DONE  
**Assignee**: Claude AI  
**Started**: 2026-02-04  
**Completed**: 2026-02-04  
**Priority**: High  
**Estimated**: 6 hours  
**Depends on**: P2-001

**Description**: Create security utility classes

**Tasks**:
- [x] `CryptoUtils.java` - AES-GCM encryption/decryption, key generation
- [x] `HashUtils.java` - SHA-256/512 hashing, salted hashing, HMAC
- [x] `CertificateUtils.java` - X.509 certificate handling, KeyStore operations
- [x] `TokenUtils.java` - JWT generation/verification, OTP generation, token masking

---

## Phase 3: bita-esm-backend Module

### P3-001: Spring Boot Project Setup
**Status**: ✅ DONE  
**Assignee**: Claude AI  
**Started**: 2026-02-04  
**Completed**: 2026-02-04  
**Priority**: Critical  
**Estimated**: 4 hours  
**Depends on**: P2-001

**Description**: Setup Spring Boot application with configurations

**Tasks**:
- [x] Create `application.yml` with profiles (local, dev, prod)
- [x] Configure PostgreSQL datasource
- [x] Configure Hibernate with Envers for auditing
- [x] Configure Redis for caching
- [x] Configure Kafka producer
- [x] Setup OpenAPI/Swagger documentation
- [x] Configure CORS
- [x] Create health check endpoint

---

### P3-002: JPA Entities (Hibernate Auto-Create)
**Status**: ✅ DONE  
**Assignee**: Claude AI  
**Started**: 2026-02-04  
**Completed**: 2026-02-04  
**Priority**: Critical  
**Estimated**: 6 hours  
**Depends on**: P3-001

**Description**: Create JPA Entity classes. Hibernate auto-creates tables using `ddl-auto: update`.

**Tasks**:
- [x] `User.java` and `UserOtp.java` entities
- [x] `Role.java` enum
- [x] `Client.java` and `Credential.java` entities
- [x] `ServiceCollection.java`, `ServiceEntity.java`, `ServiceAccess.java` entities
- [x] `EndpointTemplate.java`, `ComponentTemplate.java`, `RouteTemplate.java` templates
- [x] `Endpoint.java`, `Component.java`, `Route.java`, `RouteFile.java` entities
- [x] `BaseEntity.java` with audit fields (Hibernate Envers)
- [x] `SoftDeletableEntity.java` for soft delete support

---

### P3-003: Authentication Module
**Status**: ✅ DONE  
**Assignee**: Claude AI  
**Started**: 2026-02-04  
**Completed**: 2026-02-04  
**Priority**: Critical  
**Estimated**: 12 hours  
**Depends on**: P3-002

**Description**: User authentication with mobile phone and OTP

**Tasks**:
- [x] `User.java` entity with mobile phone (Iranian format: 09xxxxxxxxx)
- [x] `Role.java` enum
- [x] `UserRepository.java`, `UserOtpRepository.java`
- [x] `OtpService.java` - Generate and validate OTP
- [x] `SmsService.java` - Send OTP via SMS (interface + mock impl)
- [x] `AuthenticationService.java`
- [x] `JwtTokenProvider.java` - JWT token generation/validation
- [x] `SecurityConfig.java` - Spring Security configuration
- [x] `JwtAuthenticationFilter.java` - JWT filter
- [x] REST endpoints:
  - POST `/api/v1/auth/request-otp` - Request OTP for login/register
  - POST `/api/v1/auth/verify-otp` - Verify OTP and get tokens
  - POST `/api/v1/auth/refresh` - Refresh token
  - POST `/api/v1/auth/logout` - Logout
  - GET `/api/v1/auth/me` - Get current user
- [ ] Integration tests (TODO)

**Test Scenarios**:
```java
// Given a valid Iranian mobile number (09123456789)
// When requesting OTP
// Then OTP should be sent and stored

// Given a valid OTP within expiration time
// When verifying OTP
// Then JWT tokens should be returned

// Given an expired OTP
// When verifying OTP
// Then error should be returned

// Given a non-Iranian mobile format
// When requesting OTP
// Then validation error should be returned
```

---

### P3-004: Client Domain Module
**Status**: ✅ DONE  
**Assignee**: Claude AI  
**Started**: 2026-02-04  
**Completed**: 2026-02-04  
**Priority**: Critical  
**Estimated**: 16 hours  
**Depends on**: P3-003

**Description**: Complete client management with DDD

**Tasks**:
- [x] Domain Model:
  - [x] `Client.java` - Aggregate Root
  - [x] `Credential.java` - Entity
  - [x] Domain events (via bita-common)
- [x] Repository:
  - [x] `ClientRepository.java`
  - [x] `CredentialRepository.java`
- [x] Application Layer:
  - [x] `CreateClientCommand.java` + Handler
  - [x] `UpdateClientCommand.java` + Handler
  - [x] `DeleteClientCommand.java` + Handler (soft delete)
  - [x] `AddCredentialCommand.java` + Handler
  - [x] `RemoveCredentialCommand.java` + Handler
  - [x] `ClientQueryService.java`
- [x] REST Controller:
  - [x] `ClientController.java` with all CRUD endpoints
- [x] Event publishing to Kafka (DomainEventPublisher)
- [ ] Integration tests with Testcontainers (TODO)
- [x] Permission checks (@PreAuthorize)

**Test Scenarios**:
```java
// Given valid client data
// When creating a client via API
// Then client should be created and ClientCreated event published

// Given an existing client
// When adding IP credential
// Then credential should be added and CredentialAdded event published

// Given a client with active services
// When soft deleting the client
// Then client should be marked as deleted but data preserved

// Given a client with expired credential
// When checking access
// Then access should be denied
```

---

### P3-005: Service Domain Module
**Status**: ✅ DONE  
**Assignee**: Claude AI  
**Started**: 2026-02-04  
**Completed**: 2026-02-04  
**Priority**: Critical  
**Estimated**: 16 hours  
**Depends on**: P3-004

**Description**: Service and ServiceCollection management with auto K8s deployment

**Tasks**:
- [x] Domain Model:
  - [x] `ServiceCollection.java` - Aggregate Root (with base_path)
  - [x] `ServiceEntity.java` - Entity (with version, phase, scaling config)
  - [x] `ServiceAccess.java` - Entity
  - [x] Domain events (via bita-common)
- [x] Repositories (ServiceCollectionRepository, ServiceRepository, ServiceAccessRepository)
- [x] Application Layer:
  - [x] `CreateServiceCollectionCommand.java` + Handler
  - [x] `CreateServiceCommand.java` + Handler
  - [x] `ChangeServicePhaseCommand.java` + Handler
    - **When phase → ACTIVE: trigger K8s deployment**
    - **When phase → non-ACTIVE: trigger K8s undeployment**
  - [x] `UpdateServiceScalingCommand.java` + Handler (update HPA)
  - [x] `GrantAccessCommand.java` + Handler
  - [x] `RevokeAccessCommand.java` + Handler
  - [x] `AccessExpirationScheduler.java` - Check and expire access
- [x] REST Controllers (ServiceCollectionController, ServiceController, ServiceAccessController)
- [ ] Integration tests (TODO)

**Test Scenarios**:
```java
// Given a service in DRAFT phase
// When changing phase to TEST
// Then phase should change and event published

// Given a service in TEST phase
// When changing phase to ACTIVE
// Then K8s resources should be created (Deployment, Service, HPA, Ingress rule)

// Given a service in ACTIVE phase
// When changing phase to DRAFT
// Then K8s resources should be deleted

// Given access with expiration date
// When date passes
// Then scheduler should mark access as EXPIRED
```

---

### P3-005b: Kubernetes Deployer Service
**Status**: ✅ DONE  
**Assignee**: Claude AI  
**Started**: 2026-02-04  
**Completed**: 2026-02-04  
**Priority**: Critical  
**Estimated**: 16 hours  
**Depends on**: P3-005

**Description**: Auto-create K8s resources when Service phase changes to ACTIVE

**Tasks**:
- [x] `KubernetesConfig.java` - Configure Fabric8 K8s client
- [x] `KubernetesDeployerService.java`:
  - [x] `deployService(Service)` - Create all K8s resources
  - [x] `undeployService(Service)` - Delete all K8s resources
  - [x] `updateScaling(Service)` - Update HPA min/max
- [x] `K8sDeploymentBuilder.java`:
  - [x] Build Deployment with SERVICE_ID env var
  - [x] Set image: bita/esb-core:latest
  - [x] Configure readiness/liveness probes
- [x] `K8sServiceBuilder.java`:
  - [x] Build K8s Service pointing to Deployment
- [x] `K8sHpaBuilder.java`:
  - [x] Build HorizontalPodAutoscaler
  - [x] Configure CPU-based scaling
- [x] Ingress management in KubernetesDeployerService:
  - [x] Add path rule to esb-ingress
  - [x] Remove path rule from esb-ingress
  - [x] Path format: /esb/{collection}/{version}
- [ ] Integration tests with Minikube/Kind (TODO)

**Test Scenarios**:
```java
// Given a Service with min_replicas=2, max_replicas=10
// When deploying service
// Then Deployment should be created with 2 replicas
// And HPA should be created with min=2, max=10

// Given an ACTIVE service
// When undeploying service
// Then all K8s resources should be deleted
// And Ingress path rule should be removed

// Given Ingress with existing paths
// When adding new path /esb/payment/1.0
// Then path should be added without affecting other paths
```

---

### P3-006: Route Domain Module
**Status**: ✅ DONE  
**Assignee**: Claude AI  
**Started**: 2026-02-04  
**Completed**: 2026-02-04  
**Priority**: Critical  
**Estimated**: 20 hours  
**Depends on**: P3-005

**Description**: Templates, Endpoints, Components, Routes management

**Tasks**:
- [x] Domain Model (entities created in P3-002):
  - [x] `EndpointTemplate.java` - JPA Entity
  - [x] `ComponentTemplate.java` - JPA Entity
  - [x] `RouteTemplate.java` - JPA Entity
  - [x] `Endpoint.java` - JPA Entity
  - [x] `Component.java` - JPA Entity
  - [x] `Route.java` - JPA Entity
  - [x] `RouteFile.java` - JPA Entity
- [x] Repositories (7 repositories)
- [x] Template Resolver Service:
  - [x] Resolve placeholders in URI patterns
  - [x] Validate config against schema
  - [x] Apply default values
- [x] Route Builder Service:
  - [x] Build route definition from template + config
  - [x] Generate route YAML/JSON for ESB
- [x] File Management:
  - [x] `RouteFileService.java` - Upload/download/delete files
  - [x] File validation (WSDL, XSD syntax check)
- [x] REST Controllers (EndpointTemplate, ComponentTemplate, RouteTemplate, Route)
- [ ] Integration tests (TODO)

**Test Scenarios**:
```java
// Given an endpoint template with URI pattern "cxf:bean:{{serviceName}}"
// When creating endpoint with config {"serviceName": "PaymentService"}
// Then resolved URI should be "cxf:bean:PaymentService"

// Given a route with attached WSDL file
// When requesting route definition
// Then file should be included in response

// Given a route linked to a service
// When deactivating the route
// Then RouteDeactivated event should be published
```

---

### P3-007: LLM Integration Module
**Status**: ✅ DONE  
**Assignee**: Claude AI  
**Started**: 2026-02-04  
**Completed**: 2026-02-04  
**Priority**: Medium  
**Estimated**: 16 hours  
**Depends on**: P3-006

**Description**: Chat interface with LLM for service creation

**Tasks**:
- [x] Domain Model:
  - [x] `ChatSession.java`
  - [x] `ChatMessage.java`
  - [x] `ToolExecution.java`
- [x] LLM Provider Interface:
  - [x] `LlmProvider.java` - Interface
  - [x] `OpenAiProvider.java` - Implementation
  - [x] `OllamaProvider.java` - Implementation (for offline)
- [x] Tools (API wrappers for LLM):
  - [x] `LlmTool.java` - Interface
  - [x] `ToolRegistry.java`
  - [x] `ListClientsTool.java`
  - [x] `GetClientTool.java`
  - [x] `CreateServiceTool.java`
  - [x] `CreateRouteTool.java`
  - [x] `ListTemplatesTool.java`
  - [x] `ListServicesTool.java`
- [x] Chat Service:
  - [x] `ChatService.java` - Handle messages, tool calls
  - [x] Confirmation flow before tool execution
- [x] REST Controller:
  - [x] POST `/api/v1/chat/sessions` - Start session
  - [x] POST `/api/v1/chat/sessions/{id}/messages` - Send message
  - [x] POST `/api/v1/chat/sessions/{id}/confirm` - Confirm tool execution
  - [x] GET `/api/v1/chat/sessions/{id}/history` - Get history
- [ ] Integration tests (TODO)

**Test Scenarios**:
```java
// Given a chat session
// When user asks "لیست سازمان‌ها"
// Then LLM should call ListClientsTool and return results

// Given LLM wants to create a service
// When tool call is pending
// Then user should see confirmation dialog

// Given user confirms tool execution
// When executing CreateServiceTool
// Then service should be created and response returned
```

---

### P3-008: Sync API Module
**Status**: ✅ DONE  
**Assignee**: Claude AI  
**Started**: 2026-02-04  
**Completed**: 2026-02-04  
**Priority**: High  
**Estimated**: 8 hours  
**Depends on**: P3-006

**Description**: Internal APIs for ESB synchronization

**Tasks**:
- [x] `SyncDataService.java`:
  - [x] Get all clients with credentials
  - [x] Get all services with routes
  - [x] Get all access rules
  - [x] Full sync data aggregation
- [x] REST Controller (internal):
  - [x] GET `/internal/v1/sync/clients`
  - [x] GET `/internal/v1/sync/services`
  - [x] GET `/internal/v1/sync/routes` (with files)
  - [x] GET `/internal/v1/sync/access`
  - [x] GET `/internal/v1/sync/full`
- [x] Internal API authentication (API key via X-API-Key header)
- [ ] Integration tests (TODO)

---

### P3-009: Audit Trail with Hibernate Envers
**Status**: ✅ DONE  
**Assignee**: Claude AI  
**Started**: 2026-02-04  
**Completed**: 2026-02-04  
**Priority**: Medium  
**Estimated**: 6 hours  
**Depends on**: P3-002

**Description**: Configure Hibernate Envers for audit trail

**Tasks**:
- [x] `AuditRevisionEntity.java` - Custom revision entity
- [x] `AuditRevisionListener.java` - Capture user and IP
- [x] Configure Envers for all entities (via @Audited)
- [x] API to query audit history:
  - [x] GET `/api/v1/audit/{entityType}/{id}/history`
  - [x] GET `/api/v1/audit/{entityType}/{id}/revisions/{rev}`
- [ ] Integration tests (TODO)

---

## Phase 4: bita-esm-frontend Module

### P4-001: React Project Setup
**Status**: ✅ DONE  
**Assignee**: Claude AI  
**Started**: 2026-02-04  
**Completed**: 2026-02-04  
**Priority**: Critical  
**Estimated**: 4 hours  
**Depends on**: P1-001

**Description**: Setup React project with Vite

**Tasks**:
- [x] Initialize Vite + React + TypeScript
- [x] Configure Vite with path aliases
- [x] Setup TypeScript
- [x] Configure Tailwind CSS + shadcn/ui
- [x] Setup React Router v6
- [x] Setup TanStack Query
- [x] Setup Zustand for state
- [x] Configure API client (axios) with interceptors
- [x] Setup environment variables

---

### P4-002: Authentication Pages
**Status**: ✅ DONE  
**Assignee**: Claude AI  
**Started**: 2026-02-04  
**Completed**: 2026-02-04  
**Priority**: Critical  
**Estimated**: 12 hours  
**Depends on**: P4-001

**Description**: Authentication UI with OTP

**Tasks**:
- [x] Login page with mobile number input
- [x] OTP input component (6 digits)
- [x] OTP verification page
- [x] Auth state management (Zustand store)
- [x] Protected route component
- [x] Auto refresh token logic
- [x] Iranian mobile number validation (09xxxxxxxxx)
- [ ] Forgot password page (TODO)
- [ ] Reset password page (TODO)

---

### P4-003: Layout Components
**Status**: ✅ DONE  
**Assignee**: Claude AI  
**Started**: 2026-02-04  
**Completed**: 2026-02-04  
**Priority**: Critical  
**Estimated**: 8 hours  
**Depends on**: P4-002

**Description**: Main layout with sidebar and header

**Tasks**:
- [x] `MainLayout.tsx` - Main app layout
- [x] `Sidebar.tsx` - Navigation sidebar
- [x] `Header.tsx` - Top header with user menu
- [x] Permission-based menu items
- [x] Dark/Light theme toggle
- [x] Breadcrumbs component
- [ ] Responsive design (partial)

---

### P4-004: Client Management Pages
**Status**: ✅ DONE  
**Assignee**: Claude AI  
**Started**: 2026-02-04  
**Completed**: 2026-02-04  
**Priority**: High  
**Estimated**: 16 hours  
**Depends on**: P4-003

**Description**: Client CRUD pages

**Tasks**:
- [x] Client list page with search/filter
- [x] Client detail page
- [x] Client create/edit form
- [x] Credential management (view/remove)
- [x] TanStack Query hooks for CRUD
- [ ] Tags editor component (basic implementation)
- [ ] Add credential modal (TODO)
- [ ] API hooks for client operations
- [ ] Success/error toast notifications

---

### P4-005: Service Management Pages
**Status**: ✅ DONE  
**Assignee**: Claude AI  
**Started**: 2026-02-04  
**Completed**: 2026-02-04  
**Priority**: High  
**Estimated**: 14 hours  
**Depends on**: P4-003

**Description**: Service and collection management pages

**Tasks**:
- [x] Service collection list page
- [x] Service list page with filters
- [x] Service detail page (with routes)
- [x] Service create/edit form
- [x] Phase change dialog
- [x] Access management for service
- [x] TanStack Query hooks

---

### P4-006: Route Management Pages
**Status**: ✅ DONE  
**Assignee**: Claude AI  
**Started**: 2026-02-04  
**Completed**: 2026-02-04  
**Priority**: High  
**Estimated**: 16 hours  
**Depends on**: P4-005

**Description**: Route management with diagram view

**Tasks**:
- [x] Route list page
- [x] Route detail page
- [x] Route create/edit form (from template)
- [x] Template selector component
- [x] Config form (dynamic based on schema)
- [x] File upload component (WSDL, XSD, etc.)
- [x] Route YAML viewer
- [ ] Route diagram with React Flow (TODO)

---

### P4-007: Template Management Pages
**Status**: ✅ DONE  
**Assignee**: Claude AI  
**Started**: 2026-02-04  
**Completed**: 2026-02-04  
**Priority**: Medium  
**Estimated**: 12 hours  
**Depends on**: P4-003

**Description**: Template management pages

**Tasks**:
- [x] Endpoint template list/detail/form
- [x] Component template list/detail/form
- [x] Route template list/detail/form
- [x] JSON Schema editor for config_schema
- [ ] Template preview component (TODO)

---

### P4-008: LLM Chat Page
**Status**: ✅ DONE  
**Assignee**: Claude AI  
**Started**: 2026-02-04  
**Completed**: 2026-02-04  
**Priority**: Medium  
**Estimated**: 12 hours  
**Depends on**: P4-003

**Description**: Chat interface for LLM interaction

**Tasks**:
- [x] Chat page layout
- [x] Message list component
- [x] Message input component
- [x] Tool confirmation dialog
- [x] Chat history sidebar
- [x] Markdown rendering for responses
- [x] Loading states
- [ ] Client selector in chat (TODO)

---

### P4-009: User Management Pages
**Status**: ✅ DONE  
**Assignee**: Claude AI  
**Started**: 2026-02-04  
**Completed**: 2026-02-04  
**Priority**: Medium  
**Estimated**: 8 hours  
**Depends on**: P4-003

**Description**: User administration pages

**Tasks**:
- [x] User list page
- [x] User create/edit form
- [x] Role assignment component
- [x] User profile page
- [ ] Password change page (TODO)

---

## Phase 5: bita-esb-core Module

**Note:** Each ESB Core pod handles only ONE Service (identified by SERVICE_ID environment variable).
This is a generic Docker image that is deployed per-Service by ESM Backend.

### P5-001: Vert.x HTTP Server
**Status**: ✅ DONE  
**Assignee**: Claude AI  
**Started**: 2026-02-04  
**Completed**: 2026-02-04  
**Priority**: Critical  
**Estimated**: 12 hours  
**Depends on**: P2-004

**Description**: High-performance HTTP server with Vert.x

**Tasks**:
- [x] `EsbCoreApplication.java` - Main class
- [x] `HttpServerVerticle.java` - Main HTTP server
- [x] Non-blocking request handling
- [x] `RequestHandler.java` - Route matching and execution
- [x] Health check endpoints:
  - [x] `/health/live` - Liveness probe
  - [x] `/health/ready` - Readiness probe (after routes loaded)
- [x] Metrics endpoint for Prometheus
- [x] `EsbConfig.java` - Configuration from environment

---

### P5-002: Startup & Route Loading
**Status**: ✅ DONE  
**Assignee**: Claude AI  
**Started**: 2026-02-04  
**Completed**: 2026-02-04  
**Priority**: Critical  
**Estimated**: 12 hours  
**Depends on**: P5-001

**Description**: Load routes for specific SERVICE_ID on startup

**Tasks**:
- [x] Read SERVICE_ID from environment variable
- [x] `RouteDefinition.java` - Route data model
- [x] `ComponentDefinition.java` - Component data model
- [x] `RouteManager.java` - Manage route lifecycle
- [x] `DynamicRouteBuilder.java` - Build routes from definition
- [x] `CamelRouteFactory.java` - Create specific route types
- [x] Graceful shutdown with in-flight requests

**Test Scenarios**:
```java
// Given SERVICE_ID=123
// When ESB Core starts
// Then only routes for service 123 should be loaded

// Given routes loaded successfully
// When readiness probe is called
// Then should return 200 OK

// Given routes not yet loaded
// When readiness probe is called
// Then should return 503 Service Unavailable
```

---

### P5-003: Cache Manager
**Status**: ✅ DONE  
**Assignee**: Claude AI  
**Started**: 2026-02-04  
**Completed**: 2026-02-04  
**Priority**: Critical  
**Estimated**: 8 hours  
**Depends on**: P5-001

**Description**: In-memory cache for fast access (only data for this Service)

**Tasks**:
- [x] `ClientCache.java` - Cache clients with access to this Service
- [x] `AccessCache.java` - Cache access rules for this Service
- [x] `RouteCache.java` - Cache route definitions for this Service
- [x] `RateLimitCounter.java` - Rate limit counter with sliding window
- [x] Cache invalidation support

---

### P5-004: ESM Sync Service
**Status**: ✅ DONE  
**Assignee**: Claude AI  
**Started**: 2026-02-04  
**Completed**: 2026-02-04  
**Priority**: Critical  
**Estimated**: 10 hours  
**Depends on**: P5-003

**Description**: Synchronization with ESM (only for this SERVICE_ID)

**Tasks**:
- [x] `EsmApiClient.java` - REST client for ESM:
  - [x] `fetchFullSyncData()` - Get all data for this service
  - [x] `fetchRoutes()` - Get routes for this service
  - [x] `fetchAccessRules()` - Get access rules for this service
  - [x] `fetchClients()` - Get specific clients
- [x] `EventConsumer.java` - Kafka consumer:
  - [x] Filter events by SERVICE_ID (ignore events for other services)
  - [x] ROUTE_UPDATED - Update route in Camel context
  - [x] ACCESS_GRANTED, ACCESS_REVOKED - Update access cache
  - [x] CREDENTIAL_ADDED, CREDENTIAL_REMOVED - Update client cache
- [x] `SyncService.java` - Full and incremental sync

**Test Scenarios**:
```java
// Given ESB starts with empty cache
// When startup completes
// Then full sync should be triggered from ESM

// Given CLIENT_UPDATED event received
// When processing event
// Then client should be fetched from ESM and cache updated

// Given ROUTE_DEACTIVATED event received
// When processing event
// Then route should be removed from Camel context
```

---

### P5-005: Logging Infrastructure
**Status**: ✅ DONE  
**Assignee**: Claude AI  
**Started**: 2026-02-04  
**Completed**: 2026-02-04  
**Priority**: High  
**Estimated**: 10 hours  
**Depends on**: P5-002

**Description**: Comprehensive logging with SEDA channel

**Tasks**:
- [x] `LogEntry.java` - Structured log entry with all fields
- [x] `LogAggregator.java` - Collect and aggregate logs
- [x] `LogSender.java` - Interface for log destinations
- [x] `ElasticsearchLogSender.java` - Send to Elasticsearch
- [x] `KafkaLogSender.java` - Send to Kafka (for analytics)
- [x] `FileLogSender.java` - File backup

---

### P5-006: CXF Integration for WS-Security
**Status**: ❌ CANCELLED  
**Assignee**: Claude AI  
**Started**: 2026-02-04  
**Cancelled**: 2026-02-04  
**Reason**: CXF not needed - removed from project  
**Priority**: High  
**Estimated**: 12 hours  
**Depends on**: P5-002

**Description**: CXF endpoint support with WS-Security 1.1

**Tasks**:
- [x] `CxfEndpointFactory.java` - CXF endpoint factory
- [x] `WsSecurityInterceptor.java` - WS-Security interceptor for authentication
- [x] `WsSecurityCallbackHandler.java` - Password callback handler
- [x] `PolicyInjector.java` - Inject WS-Policy into WSDL
- [ ] Policy injection in WSDL
- [ ] Handle attached files (WSDL, XSD, Properties)
- [ ] Integration tests with actual SOAP calls

---

## Phase 6: Integration & E2E Testing

### P6-001: E2E Test Infrastructure
**Status**: ✅ DONE  
**Assignee**: Claude AI  
**Started**: 2026-02-04  
**Completed**: 2026-02-04  
**Priority**: High  
**Estimated**: 8 hours  
**Depends on**: P5-004

**Description**: Setup E2E testing with real services

**Tasks**:
- [x] Testcontainers setup (PostgreSQL, Redis, Kafka)
- [x] E2E test base class with auth utilities
- [x] HTTP entity helpers for REST calls

---

### P6-002: Service Creation E2E Test
**Status**: ✅ DONE  
**Assignee**: Claude AI  
**Started**: 2026-02-04  
**Completed**: 2026-02-04  
**Priority**: High  
**Estimated**: 12 hours  
**Depends on**: P6-001

**Description**: Full flow E2E test: create service → call service

**Tasks**:
- [x] Test: Create client
- [x] Test: Create service collection
- [x] Test: Create service with route
- [x] Test: Grant access to client
- [x] Test: Change phase to TEST
- [x] Test: Change phase to ACTIVE
- [x] Test: Verify deployment created
- [x] Test: View audit history
- [x] Cleanup

---

### P6-003: Rate Limiting E2E Test
**Status**: ✅ DONE  
**Assignee**: Claude AI  
**Started**: 2026-02-04  
**Completed**: 2026-02-04  
**Priority**: Medium  
**Estimated**: 6 hours  
**Depends on**: P6-001

**Description**: Test rate limiting functionality

**Tasks**:
- [x] Test: Verify access granted with rate limit
- [x] Test: Requests within limit succeed
- [x] Test: Update rate limit configuration
- [x] Test: Access revocation
- [x] Test: Different rate limit windows (SECOND, MINUTE, HOUR)

---

## Phase 7: Production Preparation

### P7-001: Kubernetes Manifests
**Status**: ✅ DONE  
**Assignee**: Claude AI  
**Started**: 2026-02-04  
**Completed**: 2026-02-04  
**Priority**: High  
**Estimated**: 12 hours  
**Depends on**: P5-005

**Description**: Production Kubernetes manifests with Kustomize

**Tasks**:
- [x] Namespace definitions
- [x] ESM Backend Deployment + Service + ServiceAccount
- [x] ESM Frontend Deployment + Service
- [x] ESB Core template with HPA
- [x] ConfigMaps for configuration
- [x] Secrets for credentials
- [x] Ingress rules (frontend, backend API, ESB wildcard)
- [x] NetworkPolicies (default-deny, ingress, egress)
- [x] Kustomize base and overlays (dev, prod)

---

### P7-002: Helm Charts
**Status**: ✅ DONE  
**Assignee**: Claude AI  
**Started**: 2026-02-04  
**Completed**: 2026-02-04  
**Priority**: Medium  
**Estimated**: 8 hours  
**Depends on**: P7-001

**Description**: Helm charts for deployment

**Tasks**:
- [x] `bita-esm` chart (Chart.yaml, values.yaml)
- [x] Backend/Frontend deployment and service templates
- [x] Ingress and ServiceAccount templates
- [x] `bita-esb` chart for per-service ESB Core deployments
- [x] HPA template for auto-scaling
- [x] Helper templates (_helpers.tpl)

---

### P7-003: CI/CD Pipeline
**Status**: ✅ DONE  
**Assignee**: Claude AI  
**Started**: 2026-02-04  
**Completed**: 2026-02-04  
**Priority**: High  
**Estimated**: 10 hours  
**Depends on**: P7-001

**Description**: GitLab CI/CD pipeline

**Tasks**:
- [x] `.gitlab-ci.yml` with 5 stages (lint, test, build, scan, deploy)
- [x] Lint stage (checkstyle, eslint)
- [x] Test stage with Postgres/Redis services and coverage
- [x] Build stage (Maven packages, Docker images)
- [x] Security scan stage (Trivy)
- [x] Deploy stage (dev auto, prod manual)

---

### P7-004: Monitoring Setup
**Status**: ✅ DONE  
**Assignee**: Claude AI  
**Started**: 2026-02-04  
**Completed**: 2026-02-04  
**Priority**: Medium  
**Estimated**: 8 hours  
**Depends on**: P7-001

**Description**: Prometheus, Grafana, Elasticsearch dashboards

**Tasks**:
- [x] Prometheus configuration with Kubernetes service discovery
- [x] Alert rules for backend errors, latency, ESB readiness
- [x] Alertmanager configuration with routing and receivers
- [x] Grafana dashboards (BITA Overview)
- [x] Grafana provisioning for datasources and dashboards

---

## Summary

| Phase | Tasks | Estimated Hours |
|-------|-------|-----------------|
| Phase 1: Infrastructure | 3 | 9 |
| Phase 2: bita-common | 5 | 28 |
| Phase 3: bita-esm-backend | 10 | 118 |
| Phase 4: bita-esm-frontend | 9 | 102 |
| Phase 5: bita-esb-core | 6 | 62 |
| Phase 6: Integration Testing | 3 | 26 |
| Phase 7: Production | 4 | 38 |
| **Total** | **40** | **383 hours** |

---

## Key Architecture Notes

1. **One Pod Group per Service (with version)**
   - Each Service (PaymentService v1.0, PaymentService v1.1, etc.) gets its own Kubernetes Deployment
   - ESM Backend auto-creates K8s resources when Service phase → ACTIVE
   - Fast startup (only routes for one Service per pod)
   - Independent scaling per Service

2. **Path-based Routing**
   - Format: `/esb/{collection}/{version}/*`
   - Example: `/esb/payment/1.0/api/pay`
   - Nginx Ingress routes to correct pod group

3. **Auto-deployment Flow**
   ```
   Service created (DRAFT) → Routes added → Phase → TEST → Phase → ACTIVE
                                                                    ↓
                                            ESM creates K8s: Deployment, Service, HPA, Ingress rule
   ```

4. **No Camel K Required**
   - ESB Core is a standard Docker image
   - Routes loaded dynamically from ESM API based on SERVICE_ID env var

---

**Document Version**: 2.0  
**Last Updated**: January 28, 2026
