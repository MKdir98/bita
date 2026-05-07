# Domain-Driven Design (DDD) Architecture

## Document Information

**Project**: BITA - Next-Generation Enterprise Service Gateway  
**Version**: 2.0  
**Date**: January 28, 2026  
**Purpose**: DDD Architecture and Module Specification

---

## 1. Strategic Design

### 1.1 Bounded Contexts

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            BITA System                                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                  Organization Management Context                     │   │
│  │  - Organizations                                                     │   │
│  │  - Credentials                                                       │   │
│  │  - Users                                                             │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    │                                         │
│                                    │ Organization reference                  │
│                                    ▼                                         │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    Service Management Context                        │   │
│  │  - Service Collections                                               │   │
│  │  - Services                                                          │   │
│  │  - Service Access                                                    │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    │                                         │
│                                    │ Service reference                       │
│                                    ▼                                         │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    Route Configuration Context                       │   │
│  │  - Templates (Endpoint, Component, Route)                           │   │
│  │  - Instances (Endpoint, Component, Route)                           │   │
│  │  - Route Files                                                       │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    │                                         │
│                                    │ Events                                  │
│                                    ▼                                         │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    Integration Context (ESB)                         │   │
│  │  - Route Deployment                                                  │   │
│  │  - Request Processing                                                │   │
│  │  - Credential Validation                                             │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    LLM Integration Context                           │   │
│  │  - Chat Sessions                                                     │   │
│  │  - Tool Execution                                                    │   │
│  │  - Service Generation                                                │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 Context Map

```
┌───────────────────┐         ┌───────────────────┐
│   Organization    │◄───────►│     Service       │
│   Management      │ Customer│    Management     │
│                   │ Supplier│                   │
└───────────────────┘         └───────────────────┘
                                       │
                                       │ Customer-Supplier
                                       ▼
                              ┌───────────────────┐
                              │      Route        │
                              │  Configuration    │
                              └───────────────────┘
                                       │
                         ┌─────────────┼─────────────┐
                         │ Published   │ Conformist  │
                         │ Language    │             │
                         ▼             ▼             │
               ┌───────────────┐ ┌───────────────┐  │
               │     LLM       │ │  Integration  │◄─┘
               │  Integration  │ │    (ESB)      │
               └───────────────┘ └───────────────┘
```

---

## 2. Project Structure

### 2.1 Module Overview

```
bita/
├── bita-esm-backend/
│   └── src/main/java/ir/iais/bita/esm/
│       ├── organization/          # Organization Management Context
│       ├── service/               # Service Management Context
│       ├── route/                 # Route Configuration Context
│       ├── llm/                   # LLM Integration Context
│       └── shared/                # Shared Kernel
│
├── bita-esb-core/
│   └── src/main/java/ir/iais/bita/esb/
│       └── integration/           # Integration Context
│
├── bita-common/
│   └── src/main/java/ir/iais/bita/common/
│       ├── domain/                # Shared domain primitives
│       ├── event/                 # Event definitions
│       └── dto/                   # Shared DTOs
│
└── bita-esm-frontend/
    └── src/
        ├── features/              # Feature-based organization
        │   ├── organization/
        │   ├── service/
        │   ├── route/
        │   └── llm-chat/
        └── shared/                # Shared components
```

---

## 3. Organization Management Context

### 3.1 Domain Model

```
┌─────────────────────────────────────────────────────────────┐
│                    Organization Aggregate                    │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              Organization (Aggregate Root)           │   │
│  │  - id: OrganizationId                               │   │
│  │  - nationalId: NationalId                           │   │
│  │  - name: String                                     │   │
│  │  - title: String                                    │   │
│  │  - description: String                              │   │
│  │  - parent: OrganizationId (nullable)               │   │
│  │  - isActive: boolean                                │   │
│  │  - credentials: List<Credential>                   │   │
│  └─────────────────────────────────────────────────────┘   │
│           │                                                  │
│           │ contains                                         │
│           ▼                                                  │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              Credential (Entity)                     │   │
│  │  - id: CredentialId                                 │   │
│  │  - type: CredentialType                             │   │
│  │  - data: CredentialData (Value Object)             │   │
│  │  - isActive: boolean                                │   │
│  │  - validFrom: DateTime                              │   │
│  │  - validUntil: DateTime                             │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 Package Structure

```
organization/
├── domain/
│   ├── model/
│   │   ├── Organization.java           # Aggregate root
│   │   ├── OrganizationId.java         # Value object
│   │   ├── NationalId.java             # Value object
│   │   ├── Credential.java             # Entity
│   │   ├── CredentialId.java           # Value object
│   │   ├── CredentialType.java         # Enum
│   │   └── CredentialData.java         # Value object (polymorphic)
│   ├── event/
│   │   ├── OrganizationCreated.java
│   │   ├── OrganizationUpdated.java
│   │   ├── CredentialAdded.java
│   │   ├── CredentialRemoved.java
│   │   └── CredentialUpdated.java
│   ├── repository/
│   │   └── OrganizationRepository.java # Interface
│   └── service/
│       └── OrganizationDomainService.java
├── application/
│   ├── command/
│   │   ├── CreateOrganizationCommand.java
│   │   ├── UpdateOrganizationCommand.java
│   │   ├── AddCredentialCommand.java
│   │   ├── RemoveCredentialCommand.java
│   │   └── handler/
│   │       ├── CreateOrganizationHandler.java
│   │       ├── UpdateOrganizationHandler.java
│   │       ├── AddCredentialHandler.java
│   │       └── RemoveCredentialHandler.java
│   ├── query/
│   │   ├── GetOrganizationQuery.java
│   │   ├── ListOrganizationsQuery.java
│   │   ├── GetOrganizationCredentialsQuery.java
│   │   └── handler/
│   │       ├── GetOrganizationHandler.java
│   │       ├── ListOrganizationsHandler.java
│   │       └── GetOrganizationCredentialsHandler.java
│   └── dto/
│       ├── OrganizationDto.java
│       ├── CredentialDto.java
│       └── CreateOrganizationRequest.java
├── infrastructure/
│   ├── persistence/
│   │   ├── JpaOrganizationRepository.java
│   │   └── entity/
│   │       ├── OrganizationJpaEntity.java
│   │       └── CredentialJpaEntity.java
│   └── event/
│       └── OrganizationEventPublisher.java
└── interfaces/
    └── rest/
        └── OrganizationController.java
```

### 3.3 Commands and Handlers

#### CreateOrganizationCommand

```java
public record CreateOrganizationCommand(
    String nationalId,
    String name,
    String title,
    String description,
    Long parentId
) implements Command<OrganizationId> {}

@Component
@RequiredArgsConstructor
public class CreateOrganizationHandler implements CommandHandler<CreateOrganizationCommand, OrganizationId> {
    
    private final OrganizationRepository repository;
    private final EventPublisher eventPublisher;
    
    @Override
    @Transactional
    public OrganizationId handle(CreateOrganizationCommand command) {
        // Validate national ID uniqueness
        if (repository.existsByNationalId(command.nationalId())) {
            throw new DuplicateNationalIdException(command.nationalId());
        }
        
        // Create organization
        Organization organization = Organization.create(
            NationalId.of(command.nationalId()),
            command.name(),
            command.title(),
            command.description(),
            command.parentId() != null ? OrganizationId.of(command.parentId()) : null
        );
        
        // Save
        repository.save(organization);
        
        // Publish event
        eventPublisher.publish(new OrganizationCreated(
            organization.getId(),
            organization.getNationalId(),
            organization.getName()
        ));
        
        return organization.getId();
    }
}
```

#### AddCredentialCommand

```java
public record AddCredentialCommand(
    Long organizationId,
    CredentialType type,
    String credentialData,
    LocalDateTime validFrom,
    LocalDateTime validUntil
) implements Command<CredentialId> {}

@Component
@RequiredArgsConstructor
public class AddCredentialHandler implements CommandHandler<AddCredentialCommand, CredentialId> {
    
    private final OrganizationRepository repository;
    private final EventPublisher eventPublisher;
    
    @Override
    @Transactional
    public CredentialId handle(AddCredentialCommand command) {
        // Load organization
        Organization organization = repository.findById(OrganizationId.of(command.organizationId()))
            .orElseThrow(() -> new OrganizationNotFoundException(command.organizationId()));
        
        // Parse credential data based on type
        CredentialData data = CredentialData.parse(command.type(), command.credentialData());
        
        // Add credential (domain logic validates)
        Credential credential = organization.addCredential(
            command.type(),
            data,
            command.validFrom(),
            command.validUntil()
        );
        
        // Save
        repository.save(organization);
        
        // Publish event
        eventPublisher.publish(new CredentialAdded(
            organization.getId(),
            credential.getId(),
            credential.getType()
        ));
        
        return credential.getId();
    }
}
```

### 3.4 REST API

```java
@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
public class OrganizationController {
    
    private final CommandBus commandBus;
    private final QueryBus queryBus;
    
    @PostMapping
    public ResponseEntity<OrganizationDto> createOrganization(
            @Valid @RequestBody CreateOrganizationRequest request) {
        
        OrganizationId id = commandBus.dispatch(new CreateOrganizationCommand(
            request.nationalId(),
            request.name(),
            request.title(),
            request.description(),
            request.parentId()
        ));
        
        OrganizationDto dto = queryBus.dispatch(new GetOrganizationQuery(id.getValue()));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<OrganizationDto> getOrganization(@PathVariable Long id) {
        OrganizationDto dto = queryBus.dispatch(new GetOrganizationQuery(id));
        return ResponseEntity.ok(dto);
    }
    
    @GetMapping
    public ResponseEntity<Page<OrganizationDto>> listOrganizations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search) {
        
        Page<OrganizationDto> result = queryBus.dispatch(
            new ListOrganizationsQuery(page, size, search));
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/{id}/credentials")
    public ResponseEntity<CredentialDto> addCredential(
            @PathVariable Long id,
            @Valid @RequestBody AddCredentialRequest request) {
        
        CredentialId credentialId = commandBus.dispatch(new AddCredentialCommand(
            id,
            request.type(),
            request.data(),
            request.validFrom(),
            request.validUntil()
        ));
        
        CredentialDto dto = queryBus.dispatch(
            new GetCredentialQuery(id, credentialId.getValue()));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }
    
    @DeleteMapping("/{id}/credentials/{credentialId}")
    public ResponseEntity<Void> removeCredential(
            @PathVariable Long id,
            @PathVariable Long credentialId) {
        
        commandBus.dispatch(new RemoveCredentialCommand(id, credentialId));
        return ResponseEntity.noContent().build();
    }
}
```

---

## 4. Service Management Context

### 4.1 Domain Model

```
┌─────────────────────────────────────────────────────────────┐
│                 ServiceCollection Aggregate                  │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────┐   │
│  │        ServiceCollection (Aggregate Root)            │   │
│  │  - id: ServiceCollectionId                          │   │
│  │  - name: String                                     │   │
│  │  - description: String                              │   │
│  │  - tags: Set<String>                               │   │
│  │  - ownerOrganizationId: OrganizationId (nullable)  │   │
│  │  - services: List<Service>                         │   │
│  └─────────────────────────────────────────────────────┘   │
│           │                                                  │
│           │ contains                                         │
│           ▼                                                  │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              Service (Entity)                        │   │
│  │  - id: ServiceId                                    │   │
│  │  - name: String                                     │   │
│  │  - version: Version                                 │   │
│  │  - description: String                              │   │
│  │  - phase: ServicePhase (DRAFT/TEST/ACTIVE)         │   │
│  │  - ownerOrganizationId: OrganizationId             │   │
│  │  - routes: List<RouteId>                           │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                  ServiceAccess Aggregate                     │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────┐   │
│  │         ServiceAccess (Aggregate Root)               │   │
│  │  - id: ServiceAccessId                              │   │
│  │  - organizationId: OrganizationId                   │   │
│  │  - serviceId: ServiceId (nullable)                  │   │
│  │  - collectionId: ServiceCollectionId (nullable)    │   │
│  │  - status: AccessStatus                             │   │
│  │  - validFrom: DateTime                              │   │
│  │  - validUntil: DateTime                             │   │
│  │  - rateLimit: RateLimit (Value Object)             │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 Package Structure

```
service/
├── domain/
│   ├── model/
│   │   ├── ServiceCollection.java
│   │   ├── Service.java
│   │   ├── ServicePhase.java
│   │   ├── ServiceAccess.java
│   │   ├── AccessStatus.java
│   │   ├── RateLimit.java            # Value object
│   │   └── Version.java              # Value object
│   ├── event/
│   │   ├── ServiceCreated.java
│   │   ├── ServicePhaseChanged.java
│   │   ├── AccessGranted.java
│   │   ├── AccessRevoked.java
│   │   └── AccessExpired.java
│   └── repository/
│       ├── ServiceCollectionRepository.java
│       ├── ServiceRepository.java
│       └── ServiceAccessRepository.java
├── application/
│   ├── command/
│   │   ├── CreateServiceCollectionCommand.java
│   │   ├── CreateServiceCommand.java
│   │   ├── ChangeServicePhaseCommand.java
│   │   ├── GrantAccessCommand.java
│   │   ├── RevokeAccessCommand.java
│   │   └── handler/
│   │       └── ...
│   ├── query/
│   │   ├── GetServiceQuery.java
│   │   ├── ListServicesQuery.java
│   │   ├── GetServiceAccessQuery.java
│   │   ├── CheckAccessQuery.java      # Used by ESB
│   │   └── handler/
│   │       └── ...
│   └── scheduler/
│       └── AccessExpirationScheduler.java  # Check and expire access
├── infrastructure/
│   └── ...
└── interfaces/
    └── rest/
        ├── ServiceCollectionController.java
        ├── ServiceController.java
        └── ServiceAccessController.java
```

### 4.3 Key Commands

```java
// Create Service
public record CreateServiceCommand(
    Long collectionId,
    String name,
    String version,
    String description,
    Long ownerOrganizationId
) implements Command<ServiceId> {}

// Change Phase
public record ChangeServicePhaseCommand(
    Long serviceId,
    ServicePhase newPhase
) implements Command<Void> {}

// Grant Access
public record GrantAccessCommand(
    Long organizationId,
    Long serviceId,           // Either serviceId
    Long serviceCollectionId, // Or collectionId
    LocalDateTime validFrom,
    LocalDateTime validUntil,
    Integer rateLimitCount,
    Integer rateLimitWindowSeconds
) implements Command<ServiceAccessId> {}

// Revoke Access
public record RevokeAccessCommand(
    Long accessId,
    String reason
) implements Command<Void> {}
```

### 4.4 Access Expiration Scheduler

```java
@Component
@RequiredArgsConstructor
public class AccessExpirationScheduler {
    
    private final ServiceAccessRepository repository;
    private final EventPublisher eventPublisher;
    
    @Scheduled(fixedRate = 60000) // Every minute
    @Transactional
    public void checkExpiredAccess() {
        List<ServiceAccess> expiredAccesses = repository
            .findByStatusAndValidUntilBefore(AccessStatus.ACTIVE, LocalDateTime.now());
        
        for (ServiceAccess access : expiredAccesses) {
            access.expire();
            repository.save(access);
            
            eventPublisher.publish(new AccessExpired(
                access.getOrganizationId(),
                access.getServiceId(),
                access.getServiceCollectionId()
            ));
        }
    }
}
```

---

## 5. Route Configuration Context

### 5.1 Domain Model

```
┌─────────────────────────────────────────────────────────────┐
│                    Template Aggregates                       │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────────┐ ┌──────────────────┐ ┌──────────────┐│
│  │EndpointTemplate  │ │ComponentTemplate │ │RouteTemplate ││
│  │  - id            │ │  - id            │ │  - id        ││
│  │  - name          │ │  - name          │ │  - name      ││
│  │  - uriPattern    │ │  - componentClass│ │  - definition││
│  │  - configSchema  │ │  - configSchema  │ │  - configSch.││
│  └──────────────────┘ └──────────────────┘ └──────────────┘│
│                                                              │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                      Route Aggregate                         │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────┐   │
│  │              Route (Aggregate Root)                  │   │
│  │  - id: RouteId                                      │   │
│  │  - templateId: RouteTemplateId (nullable)          │   │
│  │  - serviceId: ServiceId (nullable for internal)    │   │
│  │  - name: String                                     │   │
│  │  - config: JsonObject                              │   │
│  │  - routeDefinition: RouteDefinition                │   │
│  │  - kamelYaml: String                               │   │
│  │  - isInternal: boolean                              │   │
│  │  - endpoints: List<RouteEndpoint>                  │   │
│  │  - components: List<RouteComponent>                │   │
│  │  - files: List<RouteFile>                          │   │
│  └─────────────────────────────────────────────────────┘   │
│           │                                                  │
│           │ contains                                         │
│           ▼                                                  │
│  ┌──────────────────┐ ┌──────────────────┐ ┌──────────────┐│
│  │ RouteEndpoint    │ │ RouteComponent   │ │ RouteFile    ││
│  │  - endpointId    │ │  - componentId   │ │  - fileName  ││
│  │  - role          │ │  - position      │ │  - fileType  ││
│  │  - position      │ │                  │ │  - content   ││
│  └──────────────────┘ └──────────────────┘ └──────────────┘│
└─────────────────────────────────────────────────────────────┘
```

### 5.2 Package Structure

```
route/
├── domain/
│   ├── model/
│   │   ├── template/
│   │   │   ├── EndpointTemplate.java
│   │   │   ├── ComponentTemplate.java
│   │   │   ├── RouteTemplate.java
│   │   │   └── ConfigSchema.java       # Value object
│   │   ├── instance/
│   │   │   ├── Endpoint.java
│   │   │   ├── Component.java
│   │   │   ├── Route.java              # Aggregate root
│   │   │   ├── RouteEndpoint.java
│   │   │   ├── RouteComponent.java
│   │   │   ├── RouteFile.java
│   │   │   └── RouteDefinition.java    # Value object
│   │   └── shared/
│   │       └── EndpointRole.java       # FROM, TO, ERROR_HANDLER
│   ├── event/
│   │   ├── RouteCreated.java
│   │   ├── RouteUpdated.java
│   │   ├── RouteDeleted.java
│   │   ├── EndpointCreated.java
│   │   └── ComponentCreated.java
│   ├── repository/
│   │   ├── EndpointTemplateRepository.java
│   │   ├── ComponentTemplateRepository.java
│   │   ├── RouteTemplateRepository.java
│   │   ├── EndpointRepository.java
│   │   ├── ComponentRepository.java
│   │   └── RouteRepository.java
│   └── service/
│       ├── TemplateResolver.java       # Resolves placeholders
│       └── KamelYamlGenerator.java     # Generates Kamel YAML
├── application/
│   ├── command/
│   │   ├── template/
│   │   │   ├── CreateEndpointTemplateCommand.java
│   │   │   ├── CreateComponentTemplateCommand.java
│   │   │   ├── CreateRouteTemplateCommand.java
│   │   │   └── handler/
│   │   │       └── ...
│   │   ├── instance/
│   │   │   ├── CreateEndpointCommand.java
│   │   │   ├── CreateComponentCommand.java
│   │   │   ├── CreateRouteCommand.java
│   │   │   ├── UpdateRouteCommand.java
│   │   │   ├── DeleteRouteCommand.java
│   │   │   ├── AddRouteFileCommand.java
│   │   │   └── handler/
│   │   │       └── ...
│   ├── query/
│   │   ├── GetRouteQuery.java
│   │   ├── ListRoutesQuery.java
│   │   ├── GetRouteKamelYamlQuery.java
│   │   ├── ListRoutesByServiceQuery.java
│   │   └── handler/
│   │       └── ...
│   └── dto/
│       └── ...
├── infrastructure/
│   └── ...
└── interfaces/
    └── rest/
        ├── EndpointTemplateController.java
        ├── ComponentTemplateController.java
        ├── RouteTemplateController.java
        ├── EndpointController.java
        ├── ComponentController.java
        └── RouteController.java
```

### 5.3 Key Commands

```java
// Create Endpoint from Template
public record CreateEndpointCommand(
    Long templateId,          // Nullable for custom endpoints
    String name,
    Map<String, Object> config,
    Integer defaultRateLimitCount,
    Integer defaultRateLimitWindowSeconds
) implements Command<EndpointId> {}

// Create Route
public record CreateRouteCommand(
    Long templateId,          // Nullable for custom routes
    Long serviceId,           // Nullable for internal routes
    String name,
    Map<String, Object> config,
    List<Long> endpointIds,
    List<Long> componentIds,
    boolean isInternal
) implements Command<RouteId> {}

// Add Route File (WSDL, Java class, etc.)
public record AddRouteFileCommand(
    Long routeId,
    String fileName,
    String fileType,          // WSDL, JAVA_CLASS, PROPERTIES, CONFIG
    String content,
    byte[] binaryContent      // For compiled classes
) implements Command<RouteFileId> {}
```

### 5.4 Kamel YAML Generation

```java
@Component
@RequiredArgsConstructor
public class KamelYamlGenerator {
    
    private final TemplateResolver templateResolver;
    
    public String generate(Route route) {
        Map<String, Object> model = new HashMap<>();
        model.put("routeName", route.getName());
        model.put("routeId", route.getId().getValue());
        
        // Resolve endpoints
        List<Map<String, Object>> endpoints = route.getEndpoints().stream()
            .map(this::resolveEndpoint)
            .toList();
        model.put("endpoints", endpoints);
        
        // Resolve components
        List<Map<String, Object>> components = route.getComponents().stream()
            .map(this::resolveComponent)
            .toList();
        model.put("components", components);
        
        // Resolve route definition
        String resolvedDefinition = templateResolver.resolve(
            route.getRouteDefinition().toJson(),
            route.getConfig()
        );
        model.put("routeDefinition", resolvedDefinition);
        
        // Add files
        model.put("files", route.getFiles());
        
        // Generate YAML from template
        return renderKamelTemplate(model);
    }
    
    private Map<String, Object> resolveEndpoint(RouteEndpoint re) {
        Endpoint endpoint = re.getEndpoint();
        return Map.of(
            "name", endpoint.getName(),
            "role", re.getRole().name(),
            "uri", endpoint.getResolvedUri()
        );
    }
}
```

---

## 6. LLM Integration Context

### 6.1 Package Structure

```
llm/
├── domain/
│   ├── model/
│   │   ├── ChatSession.java
│   │   ├── ChatMessage.java
│   │   ├── ToolCall.java
│   │   └── ToolResult.java
│   └── service/
│       └── LlmProvider.java           # Interface
├── application/
│   ├── command/
│   │   ├── StartChatSessionCommand.java
│   │   ├── SendMessageCommand.java
│   │   ├── ConfirmToolCallCommand.java
│   │   └── handler/
│   │       └── ...
│   ├── query/
│   │   └── GetChatHistoryQuery.java
│   └── tools/
│       ├── LlmTool.java               # Interface
│       ├── CreateEndpointTemplateTool.java
│       ├── CreateEndpointTool.java
│       ├── CreateRouteTool.java
│       ├── CreateServiceTool.java
│       ├── ListTemplatesTools.java
│       └── ToolRegistry.java
├── infrastructure/
│   ├── provider/
│   │   ├── OpenAiProvider.java
│   │   ├── AnthropicProvider.java
│   │   └── OllamaProvider.java       # For local LLM
│   └── persistence/
│       └── JpaChatSessionRepository.java
└── interfaces/
    └── rest/
        └── ChatController.java
```

### 6.2 LLM Tool Definition

```java
public interface LlmTool {
    String getName();
    String getDescription();
    JsonSchema getParameterSchema();
    ToolResult execute(Map<String, Object> parameters);
}

@Component
@RequiredArgsConstructor
public class CreateServiceTool implements LlmTool {
    
    private final CommandBus commandBus;
    
    @Override
    public String getName() {
        return "create_service";
    }
    
    @Override
    public String getDescription() {
        return "Create a new service in a service collection";
    }
    
    @Override
    public JsonSchema getParameterSchema() {
        return JsonSchema.builder()
            .type("object")
            .property("collectionId", JsonSchema.integer().description("Service collection ID").required())
            .property("name", JsonSchema.string().description("Service name").required())
            .property("version", JsonSchema.string().description("Service version").required())
            .property("description", JsonSchema.string().description("Service description"))
            .build();
    }
    
    @Override
    public ToolResult execute(Map<String, Object> parameters) {
        try {
            ServiceId serviceId = commandBus.dispatch(new CreateServiceCommand(
                ((Number) parameters.get("collectionId")).longValue(),
                (String) parameters.get("name"),
                (String) parameters.get("version"),
                (String) parameters.get("description"),
                null // Owner set separately
            ));
            
            return ToolResult.success(Map.of(
                "serviceId", serviceId.getValue(),
                "message", "Service created successfully"
            ));
        } catch (Exception e) {
            return ToolResult.error(e.getMessage());
        }
    }
}
```

### 6.3 Chat Flow with Confirmation

```java
@Component
@RequiredArgsConstructor
public class SendMessageHandler implements CommandHandler<SendMessageCommand, ChatResponse> {
    
    private final LlmProvider llmProvider;
    private final ToolRegistry toolRegistry;
    private final ChatSessionRepository sessionRepository;
    
    @Override
    public ChatResponse handle(SendMessageCommand command) {
        ChatSession session = sessionRepository.findById(command.sessionId())
            .orElseThrow();
        
        // Add user message
        session.addMessage(ChatMessage.user(command.message()));
        
        // Call LLM
        LlmResponse response = llmProvider.chat(session.getMessages(), toolRegistry.getToolDefinitions());
        
        if (response.hasToolCalls()) {
            // LLM wants to call tools - ask for confirmation
            List<ToolCall> toolCalls = response.getToolCalls();
            session.setPendingToolCalls(toolCalls);
            sessionRepository.save(session);
            
            // Return response asking for confirmation
            return ChatResponse.builder()
                .message(response.getMessage())
                .pendingToolCalls(toolCalls.stream().map(this::toDto).toList())
                .requiresConfirmation(true)
                .build();
        }
        
        // No tool calls - just return message
        session.addMessage(ChatMessage.assistant(response.getMessage()));
        sessionRepository.save(session);
        
        return ChatResponse.builder()
            .message(response.getMessage())
            .requiresConfirmation(false)
            .build();
    }
}

@Component
@RequiredArgsConstructor
public class ConfirmToolCallHandler implements CommandHandler<ConfirmToolCallCommand, ChatResponse> {
    
    private final ToolRegistry toolRegistry;
    private final ChatSessionRepository sessionRepository;
    private final LlmProvider llmProvider;
    
    @Override
    public ChatResponse handle(ConfirmToolCallCommand command) {
        ChatSession session = sessionRepository.findById(command.sessionId())
            .orElseThrow();
        
        if (!command.confirmed()) {
            // User rejected - clear pending and inform LLM
            session.clearPendingToolCalls();
            session.addMessage(ChatMessage.user("کاربر این عملیات را تایید نکرد."));
            // Continue conversation...
        }
        
        // Execute pending tool calls
        List<ToolResult> results = new ArrayList<>();
        for (ToolCall toolCall : session.getPendingToolCalls()) {
            LlmTool tool = toolRegistry.getTool(toolCall.getName());
            ToolResult result = tool.execute(toolCall.getParameters());
            results.add(result);
        }
        
        // Add results to conversation
        session.addMessage(ChatMessage.toolResults(results));
        session.clearPendingToolCalls();
        
        // Get LLM's response to tool results
        LlmResponse response = llmProvider.chat(session.getMessages(), toolRegistry.getToolDefinitions());
        session.addMessage(ChatMessage.assistant(response.getMessage()));
        sessionRepository.save(session);
        
        return ChatResponse.builder()
            .message(response.getMessage())
            .toolResults(results)
            .build();
    }
}
```

---

## 7. Integration Context (ESB)

### 7.1 Package Structure

```
bita-esb-core/
└── src/main/java/ir/iais/bita/esb/
    ├── integration/
    │   ├── config/
    │   │   ├── VertxConfig.java
    │   │   ├── CamelConfig.java
    │   │   └── KafkaConfig.java
    │   ├── http/
    │   │   ├── RequestHandler.java
    │   │   └── ResponseHandler.java
    │   ├── route/
    │   │   ├── DynamicRouteManager.java
    │   │   └── RouteDeployer.java
    │   ├── sync/
    │   │   ├── ConfigSyncService.java
    │   │   ├── EventConsumer.java
    │   │   └── EsmApiClient.java
    │   └── cache/
    │       ├── OrganizationCache.java
    │       ├── CredentialCache.java
    │       └── AccessCache.java
    ├── processor/
    │   ├── CheckAccessProcessor.java
    │   ├── PrepareHeadersProcessor.java
    │   ├── CallingServiceProcessor.java
    │   ├── PrepareResultProcessor.java
    │   └── TimingLogProcessor.java
    └── security/
        ├── CredentialValidator.java
        ├── RateLimiter.java
        └── PasswordCallbackHandler.java
```

### 7.2 Event Consumer

```java
@Component
@RequiredArgsConstructor
public class EventConsumer {
    
    private final ConfigSyncService syncService;
    private final RouteDeployer routeDeployer;
    
    @KafkaListener(topics = "bita.config.changes", groupId = "esb-${esb.domain}")
    public void handleConfigChange(ConfigChangeEvent event) {
        switch (event.getEventType()) {
            case "ORGANIZATION_CREDENTIAL_ADDED":
            case "ORGANIZATION_CREDENTIAL_UPDATED":
            case "ORGANIZATION_CREDENTIAL_REMOVED":
                syncService.syncCredential(event.getPayload().getCredentialId());
                break;
                
            case "SERVICE_ACCESS_GRANTED":
            case "SERVICE_ACCESS_REVOKED":
            case "SERVICE_ACCESS_EXPIRED":
                syncService.syncAccess(event.getPayload().getAccessId());
                break;
                
            case "ROUTE_CREATED":
            case "ROUTE_UPDATED":
                routeDeployer.deployRoute(event.getPayload().getRouteId());
                break;
                
            case "ROUTE_DELETED":
                routeDeployer.undeployRoute(event.getPayload().getRouteId());
                break;
                
            case "FULL_SYNC":
                syncService.fullSync();
                break;
        }
    }
}
```

---

## 8. Shared Kernel

### 8.1 Common Domain Primitives

```java
// Value Objects
public record OrganizationId(Long value) {
    public static OrganizationId of(Long value) {
        return new OrganizationId(Objects.requireNonNull(value));
    }
}

public record ServiceId(Long value) { /* ... */ }
public record RouteId(Long value) { /* ... */ }

// Enums
public enum CredentialType {
    IP_ADDRESS,
    X509_CERTIFICATE,
    API_KEY,
    OAUTH2,
    BASIC_AUTH
}

public enum ServicePhase {
    DRAFT,
    TEST,
    ACTIVE
}

public enum AccessStatus {
    ACTIVE,
    REVOKED,
    EXPIRED
}
```

### 8.2 Event Base Classes

```java
public abstract class DomainEvent {
    private final String eventId = UUID.randomUUID().toString();
    private final LocalDateTime occurredAt = LocalDateTime.now();
    
    public abstract String getEventType();
    public abstract String getAggregateType();
    public abstract Long getAggregateId();
}

// Example
public class OrganizationCreated extends DomainEvent {
    private final OrganizationId organizationId;
    private final NationalId nationalId;
    private final String name;
    
    @Override
    public String getEventType() { return "ORGANIZATION_CREATED"; }
    
    @Override
    public String getAggregateType() { return "ORGANIZATION"; }
    
    @Override
    public Long getAggregateId() { return organizationId.value(); }
}
```

---

## 9. API Summary

### 9.1 Organization Management APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /api/v1/organizations | Create organization |
| GET | /api/v1/organizations | List organizations |
| GET | /api/v1/organizations/{id} | Get organization |
| PUT | /api/v1/organizations/{id} | Update organization |
| DELETE | /api/v1/organizations/{id} | Delete organization |
| POST | /api/v1/organizations/{id}/credentials | Add credential |
| GET | /api/v1/organizations/{id}/credentials | List credentials |
| DELETE | /api/v1/organizations/{id}/credentials/{credId} | Remove credential |

### 9.2 Service Management APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /api/v1/service-collections | Create collection |
| GET | /api/v1/service-collections | List collections |
| POST | /api/v1/services | Create service |
| GET | /api/v1/services | List services |
| PATCH | /api/v1/services/{id}/phase | Change phase |
| POST | /api/v1/service-access | Grant access |
| DELETE | /api/v1/service-access/{id} | Revoke access |

### 9.3 Route Configuration APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /api/v1/endpoint-templates | Create endpoint template |
| GET | /api/v1/endpoint-templates | List endpoint templates |
| POST | /api/v1/component-templates | Create component template |
| POST | /api/v1/route-templates | Create route template |
| POST | /api/v1/endpoints | Create endpoint |
| POST | /api/v1/components | Create component |
| POST | /api/v1/routes | Create route |
| GET | /api/v1/routes/{id}/kamel-yaml | Get Kamel YAML |
| POST | /api/v1/routes/{id}/files | Add route file |

### 9.4 LLM Chat API

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /api/v1/chat/sessions | Start chat session |
| POST | /api/v1/chat/sessions/{id}/messages | Send message |
| POST | /api/v1/chat/sessions/{id}/confirm | Confirm tool calls |
| GET | /api/v1/chat/sessions/{id}/history | Get chat history |

### 9.5 ESB Sync APIs (Internal)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /internal/v1/organizations | Get all organizations with credentials |
| GET | /internal/v1/services | Get all services |
| GET | /internal/v1/routes | Get all routes with full config |
| GET | /internal/v1/access | Get all access records |
| POST | /internal/v1/sync/full | Trigger full sync event |

---

## 10. Testing Strategy

### 10.1 Unit Tests

```java
@ExtendWith(MockitoExtension.class)
class CreateOrganizationHandlerTest {
    
    @Mock
    private OrganizationRepository repository;
    
    @Mock
    private EventPublisher eventPublisher;
    
    @InjectMocks
    private CreateOrganizationHandler handler;
    
    @Test
    void shouldCreateOrganization() {
        // Given
        var command = new CreateOrganizationCommand(
            "1234567890", "Test Org", "تست", "Description", null
        );
        when(repository.existsByNationalId(anyString())).thenReturn(false);
        
        // When
        OrganizationId result = handler.handle(command);
        
        // Then
        assertNotNull(result);
        verify(repository).save(any(Organization.class));
        verify(eventPublisher).publish(any(OrganizationCreated.class));
    }
    
    @Test
    void shouldThrowWhenDuplicateNationalId() {
        // Given
        var command = new CreateOrganizationCommand(
            "1234567890", "Test Org", "تست", "Description", null
        );
        when(repository.existsByNationalId("1234567890")).thenReturn(true);
        
        // When/Then
        assertThrows(DuplicateNationalIdException.class, 
            () -> handler.handle(command));
    }
}
```

### 10.2 Integration Tests

```java
@SpringBootTest
@Testcontainers
class OrganizationControllerIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Test
    void shouldCreateAndRetrieveOrganization() {
        // Create
        var request = new CreateOrganizationRequest(
            "1234567890", "Test Org", "تست", "Description", null
        );
        
        ResponseEntity<OrganizationDto> createResponse = restTemplate.postForEntity(
            "/api/v1/organizations", request, OrganizationDto.class);
        
        assertEquals(HttpStatus.CREATED, createResponse.getStatusCode());
        
        // Retrieve
        Long id = createResponse.getBody().id();
        ResponseEntity<OrganizationDto> getResponse = restTemplate.getForEntity(
            "/api/v1/organizations/" + id, OrganizationDto.class);
        
        assertEquals(HttpStatus.OK, getResponse.getStatusCode());
        assertEquals("Test Org", getResponse.getBody().name());
    }
}
```

---

**Document Version**: 1.0  
**Last Updated**: January 28, 2026
