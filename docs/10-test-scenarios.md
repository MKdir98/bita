# Test Scenarios Specification

## Document Information

**Project**: BITA - Next-Generation Enterprise Service Gateway  
**Version**: 4.0  
**Date**: January 28, 2026  
**Purpose**: Test scenarios for all domains

---

## 1. Testing Strategy

### 1.1 Test Pyramid

```
          ┌───────────────┐
          │   E2E Tests   │  ← Few, slow, high confidence
          │   (Cypress)   │
          ├───────────────┤
          │  Integration  │  ← Moderate number
          │    Tests      │
          │(Testcontainers)│
          ├───────────────┤
          │  Unit Tests   │  ← Many, fast
          │   (JUnit 5)   │
          └───────────────┘
```

### 1.2 Test Tools

| Layer | Tool | Purpose |
|-------|------|---------|
| Unit | JUnit 5 + Mockito | Business logic testing |
| Integration | Testcontainers | Database, Kafka, Redis |
| API | REST Assured | REST API testing |
| E2E | Cypress | Frontend + Backend |
| Performance | Gatling | Load testing |

---

## 2. Client Domain Test Scenarios

### 2.1 Unit Tests (BDD Style)

#### CreateClientCommand Tests

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("Create Client Handler")
class CreateClientHandlerTest {

    @Mock private ClientRepository repository;
    @Mock private EventPublisher eventPublisher;
    @InjectMocks private CreateClientHandler handler;

    @Nested
    @DisplayName("Given valid client data")
    class GivenValidClientData {
        
        private CreateClientCommand command;
        
        @BeforeEach
        void setup() {
            command = new CreateClientCommand(
                "TestClient", "تست", Map.of("national_id", "1234567890")
            );
            when(repository.existsByName(anyString())).thenReturn(false);
        }
        
        @Test
        @DisplayName("When creating client Then client should be saved")
        void whenCreatingClient_thenClientShouldBeSaved() {
            // When
            ClientId result = handler.handle(command);
            
            // Then
            assertNotNull(result);
            verify(repository).save(any(Client.class));
        }
        
        @Test
        @DisplayName("When creating client Then ClientCreated event should be published")
        void whenCreatingClient_thenEventShouldBePublished() {
            // When
            handler.handle(command);
            
            // Then
            verify(eventPublisher).publish(any(ClientCreated.class));
        }
    }

    @Nested
    @DisplayName("Given client name already exists")
    class GivenClientNameExists {
        
        @Test
        @DisplayName("When creating client Then DuplicateClientNameException should be thrown")
        void whenCreatingClient_thenExceptionShouldBeThrown() {
            // Given
            var command = new CreateClientCommand("ExistingClient", null, null);
            when(repository.existsByName("ExistingClient")).thenReturn(true);
            
            // When/Then
            assertThrows(DuplicateClientNameException.class, 
                () -> handler.handle(command));
        }
    }

    @Nested
    @DisplayName("Given invalid client data")
    class GivenInvalidClientData {
        
        @Test
        @DisplayName("When name is null Then ValidationException should be thrown")
        void whenNameIsNull_thenValidationException() {
            // Given
            var command = new CreateClientCommand(null, null, null);
            
            // When/Then
            assertThrows(ValidationException.class, 
                () -> handler.handle(command));
        }
        
        @Test
        @DisplayName("When name is empty Then ValidationException should be thrown")
        void whenNameIsEmpty_thenValidationException() {
            // Given
            var command = new CreateClientCommand("", null, null);
            
            // When/Then
            assertThrows(ValidationException.class, 
                () -> handler.handle(command));
        }
    }
}
```

#### Client Domain Model Tests

```java
class ClientTest {

    @Test
    @DisplayName("Should add credential to client")
    void shouldAddCredential() {
        // Given
        Client client = Client.create("TestClient", null, null);
        
        // When
        Credential credential = client.addCredential(
            CredentialType.IP_ADDRESS,
            CredentialData.ipAddress(List.of("192.168.1.1")),
            null, null
        );
        
        // Then
        assertNotNull(credential.getId());
        assertEquals(1, client.getCredentials().size());
        assertTrue(credential.isActive());
    }

    @Test
    @DisplayName("Should not add duplicate credential")
    void shouldNotAddDuplicateCredential() {
        // Given
        Client client = Client.create("TestClient", null, null);
        client.addCredential(
            CredentialType.IP_ADDRESS,
            CredentialData.ipAddress(List.of("192.168.1.1")),
            null, null
        );
        
        // When/Then
        assertThrows(DuplicateCredentialException.class, () ->
            client.addCredential(
                CredentialType.IP_ADDRESS,
                CredentialData.ipAddress(List.of("192.168.1.1")),
                null, null
            )
        );
    }

    @Test
    @DisplayName("Should soft delete client")
    void shouldSoftDeleteClient() {
        // Given
        Client client = Client.create("TestClient", null, null);
        
        // When
        client.delete();
        
        // Then
        assertTrue(client.isDeleted());
        assertFalse(client.isActive());
    }

    @Test
    @DisplayName("Should validate credential expiration")
    void shouldValidateCredentialExpiration() {
        // Given
        Client client = Client.create("TestClient", null, null);
        LocalDateTime validFrom = LocalDateTime.now().plusDays(1);
        LocalDateTime validUntil = LocalDateTime.now().minusDays(1);
        
        // When/Then
        assertThrows(InvalidDateRangeException.class, () ->
            client.addCredential(
                CredentialType.API_KEY,
                CredentialData.apiKey("key", "secret"),
                validFrom, validUntil
            )
        );
    }
}
```

### 2.2 Integration Tests

```java
@SpringBootTest
@Testcontainers
@Transactional
class ClientControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ClientRepository clientRepository;

    @Test
    @DisplayName("Should create and retrieve client via API")
    void shouldCreateAndRetrieveClient() {
        // Given
        var request = new CreateClientRequest(
            "IntegrationTestClient",
            "تست یکپارچگی",
            Map.of("national_id", "9876543210")
        );
        
        // When - Create
        ResponseEntity<ClientDto> createResponse = restTemplate.postForEntity(
            "/api/v1/clients", request, ClientDto.class);
        
        // Then
        assertEquals(HttpStatus.CREATED, createResponse.getStatusCode());
        assertNotNull(createResponse.getBody().id());
        
        // When - Retrieve
        Long clientId = createResponse.getBody().id();
        ResponseEntity<ClientDto> getResponse = restTemplate.getForEntity(
            "/api/v1/clients/" + clientId, ClientDto.class);
        
        // Then
        assertEquals(HttpStatus.OK, getResponse.getStatusCode());
        assertEquals("IntegrationTestClient", getResponse.getBody().name());
    }

    @Test
    @DisplayName("Should add credential to client")
    void shouldAddCredentialToClient() {
        // Given
        Client client = clientRepository.save(
            Client.create("TestClient", null, null)
        );
        
        var request = new AddCredentialRequest(
            CredentialType.IP_ADDRESS,
            "{\"addresses\": [\"10.0.0.1\"]}",
            null, null
        );
        
        // When
        ResponseEntity<CredentialDto> response = restTemplate.postForEntity(
            "/api/v1/clients/" + client.getId().getValue() + "/credentials",
            request, CredentialDto.class);
        
        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    @DisplayName("Should list clients with pagination")
    void shouldListClientsWithPagination() {
        // Given
        for (int i = 0; i < 25; i++) {
            clientRepository.save(Client.create("Client" + i, null, null));
        }
        
        // When
        ResponseEntity<PageResponse<ClientDto>> response = restTemplate.exchange(
            "/api/v1/clients?page=0&size=10",
            HttpMethod.GET, null,
            new ParameterizedTypeReference<>() {});
        
        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(10, response.getBody().content().size());
        assertEquals(25, response.getBody().totalElements());
    }

    @Test
    @DisplayName("Should soft delete client")
    void shouldSoftDeleteClient() {
        // Given
        Client client = clientRepository.save(
            Client.create("ToDelete", null, null)
        );
        
        // When
        restTemplate.delete("/api/v1/clients/" + client.getId().getValue());
        
        // Then
        Client deleted = clientRepository.findById(client.getId()).orElseThrow();
        assertTrue(deleted.isDeleted());
        
        // Verify not returned in list
        ResponseEntity<PageResponse<ClientDto>> response = restTemplate.exchange(
            "/api/v1/clients",
            HttpMethod.GET, null,
            new ParameterizedTypeReference<>() {});
        
        assertTrue(response.getBody().content().stream()
            .noneMatch(c -> c.id().equals(client.getId().getValue())));
    }
}
```

---

## 3. Service Domain Test Scenarios

### 3.1 Unit Tests

```java
@ExtendWith(MockitoExtension.class)
class ServiceApplicationServiceTest {

    @Mock private ServiceRepository serviceRepository;
    @Mock private ServiceCollectionRepository collectionRepository;
    @Mock private EventPublisher eventPublisher;
    @InjectMocks private ServiceApplicationService service;

    @Test
    @DisplayName("Should create service in collection")
    void shouldCreateService() {
        // Given
        ServiceCollection collection = ServiceCollection.create("TestCollection", null, null, null);
        when(collectionRepository.findById(any())).thenReturn(Optional.of(collection));
        
        var command = new CreateServiceCommand(
            collection.getId().getValue(),
            "TestService", "1.0", "Description", null
        );
        
        // When
        ServiceId result = service.createService(command);
        
        // Then
        assertNotNull(result);
        verify(serviceRepository).save(any(Service.class));
        verify(eventPublisher).publish(any(ServiceCreated.class));
    }

    @Test
    @DisplayName("Should change service phase with validation")
    void shouldChangePhaseWithValidation() {
        // Given
        Service srv = Service.create("Test", Version.of("1.0"), "desc", null, ServicePhase.DRAFT);
        when(serviceRepository.findById(any())).thenReturn(Optional.of(srv));
        
        // When - DRAFT -> TEST (allowed)
        service.changePhase(new ChangeServicePhaseCommand(1L, ServicePhase.TEST));
        
        // Then
        assertEquals(ServicePhase.TEST, srv.getPhase());
    }

    @Test
    @DisplayName("Should not allow invalid phase transition")
    void shouldNotAllowInvalidPhaseTransition() {
        // Given
        Service srv = Service.create("Test", Version.of("1.0"), "desc", null, ServicePhase.ACTIVE);
        when(serviceRepository.findById(any())).thenReturn(Optional.of(srv));
        
        // When/Then - ACTIVE -> DRAFT (not allowed)
        assertThrows(InvalidPhaseTransitionException.class, () ->
            service.changePhase(new ChangeServicePhaseCommand(1L, ServicePhase.DRAFT))
        );
    }
}

class ServiceAccessTest {

    @Test
    @DisplayName("Should grant access with rate limit")
    void shouldGrantAccessWithRateLimit() {
        // Given
        ServiceAccess access = ServiceAccess.grant(
            ClientId.of(1L),
            ServiceId.of(1L),
            null,
            LocalDateTime.now(),
            LocalDateTime.now().plusYears(1),
            RateLimit.of(1000, 60)
        );
        
        // Then
        assertEquals(AccessStatus.ACTIVE, access.getStatus());
        assertEquals(1000, access.getRateLimit().getCount());
        assertEquals(60, access.getRateLimit().getWindowSeconds());
    }

    @Test
    @DisplayName("Should revoke access")
    void shouldRevokeAccess() {
        // Given
        ServiceAccess access = ServiceAccess.grant(
            ClientId.of(1L), ServiceId.of(1L), null,
            LocalDateTime.now(), LocalDateTime.now().plusYears(1), null
        );
        
        // When
        access.revoke();
        
        // Then
        assertEquals(AccessStatus.REVOKED, access.getStatus());
    }

    @Test
    @DisplayName("Should expire access based on time")
    void shouldExpireAccessBasedOnTime() {
        // Given
        ServiceAccess access = ServiceAccess.grant(
            ClientId.of(1L), ServiceId.of(1L), null,
            LocalDateTime.now().minusDays(10),
            LocalDateTime.now().minusDays(1),  // Already expired
            null
        );
        
        // When
        access.checkExpiration();
        
        // Then
        assertEquals(AccessStatus.EXPIRED, access.getStatus());
    }
}
```

### 3.2 Integration Tests

```java
@SpringBootTest
@Testcontainers
class ServiceAccessIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Test
    @DisplayName("Should grant access and publish event")
    void shouldGrantAccessAndPublishEvent() {
        // Given
        Client client = clientRepository.save(Client.create("Client1", null, null));
        Service service = serviceRepository.save(
            Service.create("Service1", Version.of("1.0"), null, null, ServicePhase.ACTIVE)
        );
        
        var request = new GrantAccessRequest(
            client.getId().getValue(),
            service.getId().getValue(),
            null,
            LocalDateTime.now(),
            LocalDateTime.now().plusYears(1),
            1000, 60
        );
        
        // When
        ResponseEntity<ServiceAccessDto> response = restTemplate.postForEntity(
            "/api/v1/service-access", request, ServiceAccessDto.class);
        
        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        
        // Verify Kafka event was published
        // (using embedded Kafka consumer in test)
    }

    @Test
    @DisplayName("Should check access correctly")
    void shouldCheckAccessCorrectly() {
        // Given
        Client client = clientRepository.save(Client.create("Client2", null, null));
        Service service = serviceRepository.save(
            Service.create("Service2", Version.of("1.0"), null, null, ServicePhase.ACTIVE)
        );
        
        // Grant access
        restTemplate.postForEntity("/api/v1/service-access", 
            new GrantAccessRequest(
                client.getId().getValue(),
                service.getId().getValue(),
                null, LocalDateTime.now(), LocalDateTime.now().plusYears(1),
                null, null
            ), ServiceAccessDto.class);
        
        // When - Check access
        ResponseEntity<Boolean> response = restTemplate.getForEntity(
            "/api/v1/service-access/check?clientId=" + client.getId().getValue() +
            "&serviceId=" + service.getId().getValue(),
            Boolean.class);
        
        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody());
    }
}
```

---

## 4. Route Domain Test Scenarios

### 4.1 Unit Tests

```java
class RouteTest {

    @Test
    @DisplayName("Should create route from template")
    void shouldCreateRouteFromTemplate() {
        // Given
        RouteTemplate template = RouteTemplate.create(
            "test-template",
            new RouteDefinition("{\"from\": \"{{endpoint}}\", \"steps\": []}"),
            ConfigSchema.builder()
                .property("endpoint", "string", true)
                .build(),
            "Test template"
        );
        
        Map<String, Object> config = Map.of("endpoint", "cxf:bean:MyService");
        
        // When
        Route route = Route.fromTemplate(template, "my-route", config, null, false);
        
        // Then
        assertNotNull(route.getRouteDefinition());
        assertTrue(route.getRouteDefinition().toString().contains("cxf:bean:MyService"));
    }

    @Test
    @DisplayName("Should validate required config parameters")
    void shouldValidateRequiredConfigParameters() {
        // Given
        RouteTemplate template = RouteTemplate.create(
            "test-template",
            new RouteDefinition("{\"from\": \"{{endpoint}}\"}"),
            ConfigSchema.builder()
                .property("endpoint", "string", true)  // Required
                .property("timeout", "integer", false) // Optional
                .build(),
            null
        );
        
        Map<String, Object> config = Map.of("timeout", 30); // Missing required 'endpoint'
        
        // When/Then
        assertThrows(MissingRequiredConfigException.class, () ->
            Route.fromTemplate(template, "my-route", config, null, false)
        );
    }

    @Test
    @DisplayName("Should add file to route")
    void shouldAddFileToRoute() {
        // Given
        Route route = Route.create("test-route", null, null, null, false);
        
        // When
        RouteFile file = route.addFile(
            "service.wsdl",
            FileType.WSDL,
            "<definitions>...</definitions>",
            null
        );
        
        // Then
        assertEquals(1, route.getFiles().size());
        assertEquals("service.wsdl", file.getFileName());
    }
}

class TemplateResolverTest {

    @Test
    @DisplayName("Should resolve placeholders in URI pattern")
    void shouldResolvePlaceholders() {
        // Given
        TemplateResolver resolver = new TemplateResolver();
        String pattern = "cxf:bean:{{serviceName}}?dataFormat={{dataFormat}}";
        Map<String, Object> config = Map.of(
            "serviceName", "PaymentService",
            "dataFormat", "PAYLOAD"
        );
        
        // When
        String resolved = resolver.resolve(pattern, config);
        
        // Then
        assertEquals("cxf:bean:PaymentService?dataFormat=PAYLOAD", resolved);
    }

    @Test
    @DisplayName("Should use default values for missing config")
    void shouldUseDefaultValues() {
        // Given
        TemplateResolver resolver = new TemplateResolver();
        ConfigSchema schema = ConfigSchema.builder()
            .property("serviceName", "string", true, null)
            .property("dataFormat", "string", false, "PAYLOAD")
            .build();
        
        String pattern = "cxf:bean:{{serviceName}}?dataFormat={{dataFormat}}";
        Map<String, Object> config = Map.of("serviceName", "MyService");
        
        // When
        String resolved = resolver.resolve(pattern, config, schema);
        
        // Then
        assertEquals("cxf:bean:MyService?dataFormat=PAYLOAD", resolved);
    }
}
```

### 4.2 Integration Tests

```java
@SpringBootTest
@Testcontainers
class RouteIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("Should create endpoint from template")
    void shouldCreateEndpointFromTemplate() {
        // Given - Create endpoint template first
        var templateRequest = new CreateEndpointTemplateRequest(
            "test-http-endpoint",
            "platform-http:{{path}}",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "path", Map.of("type", "string", "required", true)
                )
            ),
            "Test HTTP endpoint"
        );
        
        ResponseEntity<EndpointTemplateDto> templateResponse = restTemplate.postForEntity(
            "/api/v1/endpoint-templates", templateRequest, EndpointTemplateDto.class);
        
        assertEquals(HttpStatus.CREATED, templateResponse.getStatusCode());
        Long templateId = templateResponse.getBody().id();
        
        // When - Create endpoint from template
        var endpointRequest = new CreateEndpointRequest(
            templateId,
            "my-api-endpoint",
            Map.of("path", "/api/test"),
            100, 60
        );
        
        ResponseEntity<EndpointDto> endpointResponse = restTemplate.postForEntity(
            "/api/v1/endpoints", endpointRequest, EndpointDto.class);
        
        // Then
        assertEquals(HttpStatus.CREATED, endpointResponse.getStatusCode());
        assertEquals("platform-http:/api/test", endpointResponse.getBody().resolvedUri());
    }

    @Test
    @DisplayName("Should generate Kamel YAML for route")
    void shouldGenerateKamelYaml() {
        // Given - Create complete route with endpoints
        // ... (setup code)
        
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/v1/routes/{routeId}/kamel-yaml", String.class, routeId);
        
        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("apiVersion: camel.apache.org/v1"));
        assertTrue(response.getBody().contains("kind: Integration"));
    }
}
```

---

## 5. LLM Integration Test Scenarios

### 5.1 Unit Tests

```java
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock private LlmProvider llmProvider;
    @Mock private ToolRegistry toolRegistry;
    @Mock private ChatSessionRepository sessionRepository;
    @InjectMocks private ChatService chatService;

    @Test
    @DisplayName("Should handle message and return tool calls for confirmation")
    void shouldReturnToolCallsForConfirmation() {
        // Given
        ChatSession session = new ChatSession();
        when(sessionRepository.findById(any())).thenReturn(Optional.of(session));
        
        LlmResponse response = LlmResponse.builder()
            .message("برای ساخت این سرویس نیاز دارم که...")
            .toolCalls(List.of(
                new ToolCall("create_service", Map.of("name", "PaymentService"))
            ))
            .build();
        when(llmProvider.chat(any(), any())).thenReturn(response);
        
        // When
        ChatResponse result = chatService.sendMessage(
            new SendMessageCommand(1L, "یک سرویس پرداخت بساز")
        );
        
        // Then
        assertTrue(result.requiresConfirmation());
        assertEquals(1, result.pendingToolCalls().size());
    }

    @Test
    @DisplayName("Should execute tools after confirmation")
    void shouldExecuteToolsAfterConfirmation() {
        // Given
        ChatSession session = new ChatSession();
        session.setPendingToolCalls(List.of(
            new ToolCall("create_service", Map.of("name", "PaymentService"))
        ));
        when(sessionRepository.findById(any())).thenReturn(Optional.of(session));
        
        LlmTool mockTool = mock(LlmTool.class);
        when(toolRegistry.getTool("create_service")).thenReturn(mockTool);
        when(mockTool.execute(any())).thenReturn(
            ToolResult.success(Map.of("serviceId", 123L))
        );
        
        // When
        chatService.confirmToolCalls(new ConfirmToolCallCommand(1L, true));
        
        // Then
        verify(mockTool).execute(any());
        assertTrue(session.getPendingToolCalls().isEmpty());
    }
}

class ListClientToolTest {

    @Mock private ClientQueryService queryService;
    @InjectMocks private ListClientsTool tool;

    @Test
    @DisplayName("Should return client list")
    void shouldReturnClientList() {
        // Given
        when(queryService.listClients(any())).thenReturn(
            List.of(
                new ClientDto(1L, "Client1", null, null, true),
                new ClientDto(2L, "Client2", null, null, true)
            )
        );
        
        // When
        ToolResult result = tool.execute(Map.of("limit", 10));
        
        // Then
        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        List<ClientDto> clients = (List<ClientDto>) result.getData().get("clients");
        assertEquals(2, clients.size());
    }
}
```

---

## 6. ESB Core Test Scenarios

### 6.1 Unit Tests

```java
class CheckAccessProcessorTest {

    @Mock private AccessCache accessCache;
    @Mock private CredentialCache credentialCache;
    @InjectMocks private CheckAccessProcessor processor;

    @Test
    @DisplayName("Should allow access for valid credentials")
    void shouldAllowAccessForValidCredentials() throws Exception {
        // Given
        Exchange exchange = mock(Exchange.class);
        Message message = mock(Message.class);
        when(exchange.getIn()).thenReturn(message);
        when(message.getHeader("X-Client-IP")).thenReturn("192.168.1.1");
        when(message.getHeader("ServiceName")).thenReturn("PaymentService");
        
        when(credentialCache.findClientByIp("192.168.1.1"))
            .thenReturn(Optional.of(new ClientDto(1L, "Client1", null, null, true)));
        when(accessCache.hasAccess(1L, "PaymentService"))
            .thenReturn(true);
        
        // When
        processor.process(exchange);
        
        // Then
        verify(message).setHeader("X-Client-Id", 1L);
    }

    @Test
    @DisplayName("Should reject access for unknown IP")
    void shouldRejectAccessForUnknownIp() {
        // Given
        Exchange exchange = mock(Exchange.class);
        Message message = mock(Message.class);
        when(exchange.getIn()).thenReturn(message);
        when(message.getHeader("X-Client-IP")).thenReturn("10.0.0.1");
        
        when(credentialCache.findClientByIp("10.0.0.1"))
            .thenReturn(Optional.empty());
        
        // When/Then
        assertThrows(UnauthorizedAccessException.class, () ->
            processor.process(exchange)
        );
    }

    @Test
    @DisplayName("Should check rate limits")
    void shouldCheckRateLimits() throws Exception {
        // Given
        Exchange exchange = mock(Exchange.class);
        Message message = mock(Message.class);
        when(exchange.getIn()).thenReturn(message);
        when(message.getHeader("X-Client-IP")).thenReturn("192.168.1.1");
        when(message.getHeader("ServiceName")).thenReturn("PaymentService");
        
        when(credentialCache.findClientByIp(any())).thenReturn(
            Optional.of(new ClientDto(1L, "Client1", null, null, true))
        );
        when(accessCache.hasAccess(anyLong(), anyString())).thenReturn(true);
        when(accessCache.checkRateLimit(anyLong(), anyString())).thenReturn(false); // Rate limited
        
        // When/Then
        assertThrows(RateLimitExceededException.class, () ->
            processor.process(exchange)
        );
    }
}
```

### 6.2 Integration Tests

```java
@SpringBootTest
@Testcontainers
class EsbCoreIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @Autowired
    private SyncService syncService;

    @Autowired
    private ClientCache clientCache;

    @Test
    @DisplayName("Should sync clients from ESM")
    void shouldSyncClientsFromEsm() {
        // Given
        // Mock ESM API response
        
        // When
        syncService.fullSync();
        
        // Then
        assertFalse(clientCache.isEmpty());
    }

    @Test
    @DisplayName("Should handle config change event")
    void shouldHandleConfigChangeEvent() {
        // Given
        var event = new ConfigChangeEvent(
            "CLIENT_UPDATED",
            "CLIENT",
            1L,
            Map.of("name", "UpdatedClient")
        );
        
        // When
        // Publish to Kafka
        
        // Then
        // Verify cache was updated
    }
}
```

---

## 7. API Contract Tests

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ApiContractTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("GET /api/v1/clients should return paginated list")
    void getClientsShouldReturnPaginatedList() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/v1/clients", String.class);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        // Validate JSON structure
        DocumentContext json = JsonPath.parse(response.getBody());
        assertNotNull(json.read("$.content"));
        assertNotNull(json.read("$.totalElements"));
        assertNotNull(json.read("$.totalPages"));
    }

    @Test
    @DisplayName("POST /api/v1/clients should validate request body")
    void postClientsShouldValidateRequestBody() {
        var invalidRequest = Map.of("name", ""); // Empty name
        
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/v1/clients", invalidRequest, String.class);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("Should return 404 for non-existent resource")
    void shouldReturn404ForNonExistentResource() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/v1/clients/999999", String.class);
        
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    @DisplayName("Should require authentication for protected endpoints")
    void shouldRequireAuthentication() {
        // Without auth header
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/v1/clients", String.class);
        
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}
```

---

## 8. E2E Test Scenarios (Cypress)

```javascript
// cypress/e2e/client-management.cy.ts

describe('Client Management', () => {
  beforeEach(() => {
    cy.login('admin', 'admin123');
  });

  it('should create a new client', () => {
    cy.visit('/clients');
    cy.get('[data-testid="create-client-btn"]').click();
    
    cy.get('[data-testid="client-name"]').type('E2E Test Client');
    cy.get('[data-testid="client-display-name"]').type('کلاینت تست');
    cy.get('[data-testid="client-tags"]').type('national_id:1234567890');
    
    cy.get('[data-testid="submit-btn"]').click();
    
    cy.url().should('include', '/clients/');
    cy.contains('E2E Test Client').should('be.visible');
  });

  it('should add credential to client', () => {
    cy.visit('/clients/1');
    cy.get('[data-testid="add-credential-btn"]').click();
    
    cy.get('[data-testid="credential-type"]').select('IP_ADDRESS');
    cy.get('[data-testid="credential-data"]').type('192.168.1.100');
    
    cy.get('[data-testid="save-credential-btn"]').click();
    
    cy.contains('192.168.1.100').should('be.visible');
  });

  it('should search clients', () => {
    cy.visit('/clients');
    cy.get('[data-testid="search-input"]').type('Test');
    
    cy.get('[data-testid="client-list"]')
      .should('contain', 'Test')
      .and('not.contain', 'Other Client');
  });
});

describe('LLM Chat', () => {
  beforeEach(() => {
    cy.login('admin', 'admin123');
    cy.visit('/chat');
  });

  it('should start chat and receive response', () => {
    cy.get('[data-testid="chat-input"]')
      .type('لیست سازمان‌ها رو بهم بده');
    cy.get('[data-testid="send-btn"]').click();
    
    cy.get('[data-testid="chat-messages"]', { timeout: 10000 })
      .should('contain', 'سازمان');
  });

  it('should confirm tool execution', () => {
    cy.get('[data-testid="chat-input"]')
      .type('یک سرویس جدید بساز');
    cy.get('[data-testid="send-btn"]').click();
    
    // Wait for confirmation dialog
    cy.get('[data-testid="tool-confirmation"]', { timeout: 10000 })
      .should('be.visible');
    
    cy.get('[data-testid="confirm-btn"]').click();
    
    cy.get('[data-testid="chat-messages"]')
      .should('contain', 'موفقیت');
  });
});
```

---

## 9. Full Flow E2E Tests (Service Creation → Invocation)

These tests verify the complete flow: create service in ESM, wait for ESB sync, call service.

```java
@SpringBootTest
@Testcontainers
@DisplayName("WS-Security Service Full Flow E2E Tests")
class WsSecurityServiceE2ETest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");
    
    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @Autowired
    private TestRestTemplate esmApi;
    
    @Autowired
    private SoapClient soapClient;

    private Long clientId;
    private Long serviceId;
    private Long routeId;

    @Nested
    @DisplayName("Given a WS-Security service is created in ESM")
    class GivenWsSecurityServiceCreated {

        @BeforeEach
        void setup() throws Exception {
            // Given - Create client with X.509 certificate
            clientId = createClientWithCertificate();
            
            // Given - Create WS-Security service
            serviceId = createWsSecurityService();
            
            // Given - Create route with WS-Security endpoint and attach files
            routeId = createWsSecurityRoute(serviceId);
            
            // Given - Grant access to client
            grantAccess(clientId, serviceId);
            
            // Given - Wait for ESB to sync (event-driven)
            waitForEsbSync(routeId, Duration.ofSeconds(10));
        }

        @Nested
        @DisplayName("When calling the service")
        class WhenCallingService {

            @Test
            @DisplayName("Then with valid certificate, response should be successful")
            void thenWithValidCertificate_responseShouldBeSuccessful() throws Exception {
                // Given
                KeyStore clientKeystore = loadClientKeystore();
                
                // When
                SoapResponse response = soapClient.call(
                    "https://localhost/esb/payment",
                    buildSoapRequest(),
                    clientKeystore
                );
                
                // Then
                assertThat(response.isSuccess()).isTrue();
                assertThat(response.getBody()).contains("<paymentResult>");
            }

            @Test
            @DisplayName("Then with invalid certificate, should return fault")
            void thenWithInvalidCertificate_shouldReturnFault() {
                // Given
                KeyStore invalidKeystore = loadInvalidKeystore();
                
                // When/Then
                SoapFaultException exception = assertThrows(SoapFaultException.class, () -> {
                    soapClient.call(
                        "https://localhost/esb/payment",
                        buildSoapRequest(),
                        invalidKeystore
                    );
                });
                
                assertThat(exception.getFaultCode()).contains("Security");
            }

            @Test
            @DisplayName("Then logs should be written to Elasticsearch with step timings")
            void thenLogsShouldIncludeStepTimings() throws Exception {
                // Given
                KeyStore clientKeystore = loadClientKeystore();
                
                // When
                soapClient.call(
                    "https://localhost/esb/payment",
                    buildSoapRequest(),
                    clientKeystore
                );
                
                // Then - Wait for async log processing
                await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                    List<LogEntry> logs = searchLogs("routeId:" + routeId);
                    
                    assertThat(logs).isNotEmpty();
                    
                    LogEntry entry = logs.get(0);
                    assertThat(entry.getClientId()).isEqualTo(clientId);
                    assertThat(entry.getStatus()).isEqualTo("SUCCESS");
                    assertThat(entry.getSteps()).containsKeys(
                        "checkAccess", "prepareHeaders", "serviceCall", "prepareResult"
                    );
                    
                    // Each step should have timing info
                    entry.getSteps().values().forEach(step -> {
                        assertThat(step.getStartTime()).isNotNull();
                        assertThat(step.getDurationMs()).isGreaterThanOrEqualTo(0);
                    });
                });
            }
        }

        @Nested
        @DisplayName("When service route is deactivated")
        class WhenRouteDeactivated {

            @Test
            @DisplayName("Then calling service should return ServiceNotFound")
            void thenCallingShouldReturnNotFound() throws Exception {
                // Given
                KeyStore clientKeystore = loadClientKeystore();
                
                // When - Deactivate route
                esmApi.patch("/api/v1/routes/" + routeId + "/deactivate", null, Void.class);
                
                // Wait for ESB to process ROUTE_DEACTIVATED event
                await().atMost(Duration.ofSeconds(5)).until(() -> 
                    !isRouteAvailable(routeId)
                );
                
                // Then
                SoapFaultException exception = assertThrows(SoapFaultException.class, () -> {
                    soapClient.call(
                        "https://localhost/esb/payment",
                        buildSoapRequest(),
                        clientKeystore
                    );
                });
                
                assertThat(exception.getFaultCode()).contains("NotFound");
            }
        }

        @Nested
        @DisplayName("When request exceeds rate limit")
        class WhenRateLimitExceeded {

            @Test
            @DisplayName("Then subsequent requests should be rejected")
            void thenRequestsShouldBeRejected() throws Exception {
                // Given - Rate limit is 10 requests per minute
                updateRateLimit(clientId, serviceId, 10, 60);
                KeyStore clientKeystore = loadClientKeystore();
                
                // When - Make 10 successful requests
                for (int i = 0; i < 10; i++) {
                    SoapResponse response = soapClient.call(
                        "https://localhost/esb/payment",
                        buildSoapRequest(),
                        clientKeystore
                    );
                    assertThat(response.isSuccess()).isTrue();
                }
                
                // Then - 11th request should fail
                SoapFaultException exception = assertThrows(SoapFaultException.class, () -> {
                    soapClient.call(
                        "https://localhost/esb/payment",
                        buildSoapRequest(),
                        clientKeystore
                    );
                });
                
                assertThat(exception.getFaultCode()).contains("RateLimitExceeded");
            }
        }

        @Nested
        @DisplayName("When request fails at service")
        class WhenServiceFails {

            @Test
            @DisplayName("Then error should be logged with stack trace")
            void thenErrorShouldBeLogged() throws Exception {
                // Given - Configure mock service to fail
                configureServiceToFail();
                KeyStore clientKeystore = loadClientKeystore();
                
                // When
                assertThrows(SoapFaultException.class, () -> {
                    soapClient.call(
                        "https://localhost/esb/payment",
                        buildSoapRequest(),
                        clientKeystore
                    );
                });
                
                // Then - Error should be logged
                await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                    List<LogEntry> logs = searchLogs("routeId:" + routeId + " AND status:FAILED");
                    
                    assertThat(logs).isNotEmpty();
                    
                    LogEntry entry = logs.get(0);
                    assertThat(entry.getStatus()).isEqualTo("FAILED");
                    assertThat(entry.getErrorMessage()).isNotBlank();
                    assertThat(entry.getStackTrace()).isNotBlank();
                });
            }
        }
    }

    // Helper methods
    
    private Long createClientWithCertificate() {
        // Create client
        var request = new CreateClientRequest(
            "TestClient", "تست", Map.of("national_id", "1234567890")
        );
        ClientDto client = esmApi.postForObject("/api/v1/clients", request, ClientDto.class);
        
        // Add X.509 certificate
        var certRequest = new AddCredentialRequest(
            CredentialType.X509_CERTIFICATE,
            "{\"certificate\": \"" + loadTestCertificate() + "\", \"alias\": \"test-client\"}",
            null, null
        );
        esmApi.postForObject(
            "/api/v1/clients/" + client.id() + "/credentials",
            certRequest, 
            CredentialDto.class
        );
        
        return client.id();
    }
    
    private Long createWsSecurityRoute(Long serviceId) {
        // Create endpoint from WS-Security template
        var endpointRequest = new CreateEndpointRequest(
            getTemplateId("cxf-wssecurity-11"),
            "payment-endpoint",
            Map.of(
                "serviceName", "PaymentService",
                "wsdlLocation", "payment.wsdl",
                "serviceClass", "ir.iais.test.PaymentService"
            ),
            1000, 60
        );
        var endpoint = esmApi.postForObject("/api/v1/endpoints", endpointRequest, EndpointDto.class);
        
        // Attach WSDL file
        uploadFile("/api/v1/endpoints/" + endpoint.id() + "/files", 
            "payment.wsdl", FileType.WSDL, loadTestWsdl());
        
        // Attach interceptor configuration (as properties file)
        uploadFile("/api/v1/endpoints/" + endpoint.id() + "/files",
            "keystore.properties", FileType.PROPERTIES, loadKeystoreProps());
        
        // Create route
        var routeRequest = new CreateRouteRequest(
            null, serviceId, "payment-route",
            buildRouteDefinition(endpoint.id()),
            false
        );
        var route = esmApi.postForObject("/api/v1/routes", routeRequest, RouteDto.class);
        
        return route.id();
    }
}
```

---

## 10. Performance Test Scenarios

```scala
// gatling/src/test/scala/LoadTest.scala

class ClientApiLoadTest extends Simulation {

  val httpProtocol = http
    .baseUrl("http://localhost:8080")
    .acceptHeader("application/json")
    .authorizationHeader("Bearer ${token}")

  val scn = scenario("Client API Load Test")
    .exec(http("List Clients")
      .get("/api/v1/clients")
      .check(status.is(200)))
    .pause(1)
    .exec(http("Get Client")
      .get("/api/v1/clients/1")
      .check(status.is(200)))

  setUp(
    scn.inject(
      rampUsers(100).during(30.seconds),
      constantUsersPerSec(10).during(1.minute)
    )
  ).protocols(httpProtocol)
   .assertions(
     global.responseTime.max.lt(2000),
     global.successfulRequests.percent.gt(99)
   )
}
```

---

**Document Version**: 1.0  
**Last Updated**: January 28, 2026
