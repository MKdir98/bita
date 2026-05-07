# ESB-Master (BITA) - Technical Specification

## Document Information

**Project**: ESB-Master (BITA - Enterprise Service Bus)  
**Version**: 2.5.10  
**Technology**: Spring Boot 2.4.2, Apache Camel 3.9.0, Apache CXF 3.4.3  
**Purpose**: Service gateway and routing infrastructure

---

## 1. System Overview

ESB-Master (BITA) serves as the enterprise service bus and gateway for the service ecosystem. It dynamically creates routes based on ESM configuration, enforces security policies, validates access control, and provides comprehensive transaction logging.

### 1.1 Key Capabilities

- **Dynamic Service Routing**: Runtime route creation from ESM metadata
- **WS-Security Enforcement**: X.509-based encryption and signing
- **Access Control Validation**: IP-based and organization-based authorization
- **Multi-Domain Support**: Cross-domain service invocation via ActiveMQ
- **Load Balancing**: Round-robin distribution across service instances
- **Transaction Logging**: Comprehensive audit trail in InfluxDB
- **Protocol Transformation**: SOAP proxy and message transformation

---

## 2. Architecture

### 2.1 Component Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Entry Point Layer                        │
│  - CXF Servlet (/services/*)                                │
│  - Camel Servlet (/rest/*)                                  │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│                   Interceptor Chain                          │
│  - SetNeedHeadersInInterceptor                              │
│  - SetCallerNationalIdHeaderInInterceptor                   │
│  - SetRequestSoapHeaderInInterceptor                        │
│  - WSS4JInInterceptor (decrypt & validate)                  │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│                    Routing Layer                             │
│  - BITARouteBuilder (Apache Camel)                          │
│  - CheckAccessProcessor                                      │
│  - PrepareHeadersProcessor                                   │
└────────────────────────┬────────────────────────────────────┘
                         │
                    ┌────┴────┐
                    │Decision │
                    └────┬────┘
              ┌──────────┴──────────┐
              │                     │
    ┌─────────▼────────┐  ┌────────▼─────────┐
    │ Local Invocation │  │ Remote Invocation│
    │ (Direct Call)    │  │ (ActiveMQ)       │
    └─────────┬────────┘  └────────┬─────────┘
              │                     │
              └──────────┬──────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│                   Response Processing                        │
│  - PrepareResultProcessor                                    │
│  - WSS4JOutInterceptor (encrypt & sign)                     │
│  - SetProviderNationalIdInResponseInterceptor               │
│  - SaveInfluxDataInterceptor                                │
│  - RemoveBITAHeaderInterceptor                              │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 Technology Stack

**Core Framework**:
- Spring Boot 2.4.2
- Apache Camel 3.9.0
- Apache CXF 3.4.3

**Messaging**:
- Apache ActiveMQ 5.15.3
- Apache Kafka 2.3.0

**Security**:
- WSS4J (WS-Security)
- Java Cryptography Extension (JCE)

**HTTP Client**:
- Jersey 2.33
- OkHttp 3.x

**Monitoring**:
- InfluxDB Client
- Micrometer (metrics)

**Utilities**:
- Jackson (JSON)
- Gson (JSON)
- Commons IO
- Commons Codec

---

## 3. Request Processing Flow

### 3.1 Detailed Flow Diagram

```
┌─────────────────────────────────────────────────────────────┐
│ 1. Client Request Arrival                                    │
│    URL: /services/ServiceName_v1.0                          │
│    Protocol: SOAP 1.2 with WS-Security                      │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│ 2. CXF Servlet Processing                                    │
│    - Parse SOAP envelope                                     │
│    - Extract WS-Security headers                             │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│ 3. Inbound Interceptors (Sequential)                         │
│                                                              │
│    a) SetNeedHeadersInInterceptor                           │
│       - Extract caller IP from X-Forwarded-For              │
│       - Parse service name and version from URL             │
│       - Generate request ID (UUID)                          │
│       - Set request timestamp                               │
│       - Extract operation name from SOAP body               │
│                                                              │
│    b) SetCallerNationalIdHeaderInInterceptor                │
│       - Resolve organization by caller IP                   │
│       - Set callerNationalId header                         │
│       - Set callerOrganization header                       │
│                                                              │
│    c) SetRequestSoapHeaderInInterceptor                     │
│       - Extract WS-Addressing headers                       │
│       - Store MessageID, Action, To                         │
│                                                              │
│    d) WSS4JInInterceptor                                    │
│       - Validate XML signature                              │
│       - Decrypt SOAP body                                   │
│       - Validate timestamp                                  │
│       - Extract caller certificate                          │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│ 4. Camel Route Execution                                     │
│                                                              │
│    a) PrepareHeadersProcessor                               │
│       - Validate required headers present                   │
│       - Enrich headers with service metadata                │
│                                                              │
│    b) CheckAccessProcessor                                  │
│       - Validate caller IP recognized                       │
│       - Check organization access to service                │
│       - Throw UnauthorizedException if denied               │
│                                                              │
│    c) Routing Decision                                      │
│       if (service.ownerDomain == localDomain)               │
│           → Local Invocation                                │
│       else                                                  │
│           → Remote Invocation via ActiveMQ                  │
└────────────────────────┬────────────────────────────────────┘
                         │
              ┌──────────┴──────────┐
              │                     │
    ┌─────────▼────────┐  ┌────────▼─────────┐
    │ 5a. Local Call   │  │ 5b. Remote Call  │
    │                  │  │                  │
    │ CallingService   │  │ LoadBalance      │
    │ Processor        │  │ RoundRobin       │
    │                  │  │                  │
    │ - Invoke service │  │ - Send to queue  │
    │   implementation │  │ - Wait for reply │
    │ - Handle errors  │  │ - Timeout: 30s   │
    └─────────┬────────┘  └────────┬─────────┘
              │                     │
              └──────────┬──────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│ 6. Response Preparation                                      │
│                                                              │
│    a) PrepareResultProcessor                                │
│       - Wrap result in SOAP envelope                        │
│       - Add WS-Addressing correlation headers               │
│                                                              │
│    b) Outbound Interceptors (Sequential)                    │
│                                                              │
│       i) WSS4JOutInterceptor                                │
│          - Sign response with provider certificate          │
│          - Encrypt response with caller public key          │
│                                                              │
│       ii) SetProviderNationalIdInResponseInterceptor        │
│           - Add provider organization ID header             │
│           - Add request ID for correlation                  │
│                                                              │
│       iii) SaveInfluxDataInterceptor                        │
│            - Calculate response time                        │
│            - Log transaction to InfluxDB                    │
│            - Include caller, provider, duration             │
│                                                              │
│       iv) RemoveBITAHeaderInterceptor                       │
│           - Remove internal BITA headers                    │
│           - Clean up temporary headers                      │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│ 7. Response Return                                           │
│    - HTTP 200 OK                                            │
│    - SOAP 1.2 response with WS-Security                     │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 Error Handling Flow

```
┌─────────────────────────────────────────────────────────────┐
│ Error Occurs at Any Stage                                    │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│ Exception Handler                                            │
│                                                              │
│ if (UnauthorizedException)                                  │
│     → Return SOAP Fault: Unauthorized                       │
│     → Log to InfluxDB with status=unauthorized              │
│                                                              │
│ else if (SecurityException)                                 │
│     → Return SOAP Fault: Security Error                     │
│     → Log to InfluxDB with status=security_error            │
│                                                              │
│ else if (TimeoutException)                                  │
│     → Return SOAP Fault: Service Timeout                    │
│     → Log to InfluxDB with status=timeout                   │
│                                                              │
│ else                                                        │
│     → Return SOAP Fault: Internal Error                     │
│     → Log to InfluxDB with status=error                     │
│     → Include exception message and stack trace             │
└─────────────────────────────────────────────────────────────┘
```

---

## 4. Dynamic Route Creation

### 4.1 Route Builder Implementation

**BITARouteBuilder** (`ir.iais.bita.initiall.BITARouteBuilder`):

```java
@Component
public class BITARouteBuilder extends RouteBuilder {
    
    @Autowired
    private BitaDataCacheManager cacheManager;
    
    @Autowired
    private KeyStoreManager keyStoreManager;
    
    @Override
    public void configure() throws Exception {
        // 1. Load configuration from ESM
        loadConfigurationFromESM();
        
        // 2. Create temporary WSDL files
        createWSDLFiles();
        
        // 3. Load organization certificates
        loadCertificates();
        
        // 4. Configure ActiveMQ connections
        configureActiveMQConnections();
        
        // 5. Create routes for each service
        createServiceRoutes();
    }
    
    private void loadConfigurationFromESM() {
        // Fetch data from ESM REST API
        List<Organization> organizations = cacheManager.loadOrganizations();
        List<Service> services = cacheManager.loadServices();
        List<AccessOrganizationToServiceCollection> accessRules = 
            cacheManager.loadAccessRules();
        List<ActiveMQConfig> activeMQConfigs = 
            cacheManager.loadActiveMQConfigs();
        String serverName = cacheManager.loadServerName();
        
        // Store in factory
        BitaDataFactory.setOrganizations(organizations);
        BitaDataFactory.setServices(services);
        BitaDataFactory.setAccessRules(accessRules);
        BitaDataFactory.setActiveMQConfigs(activeMQConfigs);
        BitaDataFactory.setServerName(serverName);
    }
    
    private void createWSDLFiles() throws IOException {
        List<Service> services = BitaDataFactory.getServices();
        
        for (Service service : services) {
            String wsdlContent = service.getWsdl();
            String fileName = service.getName() + "_v" + 
                              service.getVersion() + ".wsdl";
            Path wsdlPath = Paths.get("/tmp/wsdl", fileName);
            
            Files.createDirectories(wsdlPath.getParent());
            Files.write(wsdlPath, wsdlContent.getBytes(StandardCharsets.UTF_8));
            
            service.setWsdlPath(wsdlPath.toString());
        }
    }
    
    private void loadCertificates() throws Exception {
        List<Organization> organizations = BitaDataFactory.getOrganizations();
        
        for (Organization org : organizations) {
            for (PublicKey cert : org.getCertificates()) {
                keyStoreManager.addCertificate(
                    org.getNationalId(),
                    cert.getAlias(),
                    cert.getCertificate()
                );
            }
        }
        
        // Create unified trust store
        keyStoreManager.createUnifiedTrustStore();
    }
    
    private void configureActiveMQConnections() {
        List<ActiveMQConfig> configs = BitaDataFactory.getActiveMQConfigs();
        
        for (ActiveMQConfig config : configs) {
            // Create ActiveMQ component
            ActiveMQComponent activeMQ = new ActiveMQComponent();
            activeMQ.setBrokerURL(config.getBrokerUrl());
            activeMQ.setUserName(config.getUsername());
            activeMQ.setPassword(config.getPassword());
            
            // Configure SSL
            if (config.getBrokerUrl().startsWith("ssl://")) {
                SslConfiguration sslConfig = new SslConfiguration();
                sslConfig.setTrustStore(config.getTrustStore());
                sslConfig.setTrustStorePassword(config.getTrustStorePassword());
                activeMQ.setSslConfiguration(sslConfig);
            }
            
            // Register component
            getContext().addComponent("activemq-" + config.getName(), activeMQ);
        }
    }
    
    private void createServiceRoutes() throws Exception {
        List<Service> services = BitaDataFactory.getServices();
        String localDomain = BitaDataFactory.getServerName();
        
        for (Service service : services) {
            if (!service.getActive()) {
                continue;
            }
            
            if (service.getTechnology() == ServiceTechnology.X509EncryptionAndSign) {
                createX509Route(service, localDomain);
            } else if (service.getTechnology() == ServiceTechnology.Proxy) {
                createProxyRoute(service, localDomain);
            }
        }
    }
    
    private void createX509Route(Service service, String localDomain) {
        String serviceName = service.getName() + "_v" + service.getVersion();
        String wsdlPath = "file://" + service.getWsdlPath();
        String ownerDomain = service.getOwnerDomain().getName();
        
        // Create CXF endpoint
        CxfEndpoint cxfEndpoint = new CxfEndpoint();
        cxfEndpoint.setWsdlURL(wsdlPath);
        cxfEndpoint.setServiceClass(service.getServiceClass());
        cxfEndpoint.setAddress("/" + serviceName);
        cxfEndpoint.setDataFormat(DataFormat.PAYLOAD);
        
        // Add security interceptors
        cxfEndpoint.getInInterceptors().add(new SetNeedHeadersInInterceptor());
        cxfEndpoint.getInInterceptors().add(new SetCallerNationalIdHeaderInInterceptor());
        cxfEndpoint.getInInterceptors().add(createWSS4JInInterceptor());
        
        cxfEndpoint.getOutInterceptors().add(createWSS4JOutInterceptor());
        cxfEndpoint.getOutInterceptors().add(new SetProviderNationalIdInResponseInterceptor());
        cxfEndpoint.getOutInterceptors().add(new SaveInfluxDataInterceptor());
        cxfEndpoint.getOutInterceptors().add(new RemoveBITAHeaderInterceptor());
        
        // Create route
        from(cxfEndpoint)
            .routeId("route-" + serviceName)
            .process(new PrepareHeadersProcessor())
            .process(new CheckAccessProcessor())
            .choice()
                .when(simple("${header.ownerDomain} == '" + localDomain + "'"))
                    // Local invocation
                    .process(new CallingServiceProcessor())
                .otherwise()
                    // Remote invocation via ActiveMQ
                    .loadBalance().roundRobin()
                        .to("activemq-" + ownerDomain + ":queue:x509-push")
                    .end()
            .end()
            .process(new PrepareResultProcessor());
    }
    
    private void createProxyRoute(Service service, String localDomain) {
        String serviceName = service.getName() + "_v" + service.getVersion();
        String serviceUrl = service.getServiceUrl();
        
        from("cxf:/Proxy/" + serviceName)
            .routeId("route-proxy-" + serviceName)
            .process(new CheckAccessProcessor())
            .log("Input: ${body}")
            .setHeader(Exchange.HTTP_METHOD, constant("POST"))
            .setHeader(Exchange.CONTENT_TYPE, constant("text/xml"))
            .to(serviceUrl)
            .log("Output: ${body}")
            .process(new ProxyLoggerReceiver());
    }
    
    private WSS4JInInterceptor createWSS4JInInterceptor() {
        Map<String, Object> inProps = new HashMap<>();
        inProps.put(WSHandlerConstants.ACTION, 
            WSHandlerConstants.SIGNATURE + " " + WSHandlerConstants.ENCRYPT);
        inProps.put(WSHandlerConstants.SIG_PROP_FILE, "keystore2.properties");
        inProps.put(WSHandlerConstants.DEC_PROP_FILE, "keystore2.properties");
        inProps.put(WSHandlerConstants.PW_CALLBACK_CLASS, 
            CertificatePasswordCallbackHandler.class.getName());
        
        return new WSS4JInInterceptor(inProps);
    }
    
    private WSS4JOutInterceptor createWSS4JOutInterceptor() {
        Map<String, Object> outProps = new HashMap<>();
        outProps.put(WSHandlerConstants.ACTION, 
            WSHandlerConstants.SIGNATURE + " " + WSHandlerConstants.ENCRYPT);
        outProps.put(WSHandlerConstants.SIGNATURE_USER, "esb-key");
        outProps.put(WSHandlerConstants.ENCRYPTION_USER, "useReqSigCert");
        outProps.put(WSHandlerConstants.SIG_KEY_ID, "DirectReference");
        outProps.put(WSHandlerConstants.ENC_KEY_ID, "DirectReference");
        outProps.put(WSHandlerConstants.SIG_PROP_FILE, "keystore2.properties");
        outProps.put(WSHandlerConstants.ENC_PROP_FILE, "truststore.properties");
        outProps.put(WSHandlerConstants.PW_CALLBACK_CLASS, 
            CertificatePasswordCallbackHandler.class.getName());
        
        return new WSS4JOutInterceptor(outProps);
    }
}
```

### 4.2 Route Lifecycle

**Startup**:
1. Spring Boot application starts
2. `BITARouteBuilder.configure()` called
3. Configuration loaded from ESM
4. WSDL files created
5. Certificates loaded
6. ActiveMQ connections configured
7. Routes created and started

**Runtime**:
1. Routes process incoming requests
2. Access control enforced
3. Messages routed to local or remote services
4. Transactions logged

**Shutdown**:
1. Routes stopped gracefully
2. In-flight requests completed
3. ActiveMQ connections closed
4. Temporary files cleaned up

---

## 5. Processors

### 5.1 PrepareHeadersProcessor

**Purpose**: Validate and enrich request headers

**Implementation**:
```java
public class PrepareHeadersProcessor implements Processor {
    
    @Override
    public void process(Exchange exchange) throws Exception {
        Message message = exchange.getIn();
        
        // Validate required headers
        String callerIp = message.getHeader("callerIp", String.class);
        String serviceName = message.getHeader("serviceName", String.class);
        String version = message.getHeader("version", String.class);
        String requestId = message.getHeader("requestId", String.class);
        
        if (callerIp == null || serviceName == null || version == null) {
            throw new IllegalArgumentException("Missing required headers");
        }
        
        // Load service metadata
        Service service = BitaDataFactory.getService(serviceName, version);
        if (service == null) {
            throw new ServiceNotFoundException(
                "Service not found: " + serviceName + " v" + version);
        }
        
        // Enrich headers
        message.setHeader("serviceId", service.getId());
        message.setHeader("ownerDomain", service.getOwnerDomain().getName());
        message.setHeader("ownerOrganization", 
            service.getOwnerOrganization().getNationalId());
        message.setHeader("technology", service.getTechnology().name());
        
        // Store request time
        message.setHeader("requestTime", System.currentTimeMillis());
    }
}
```

### 5.2 CheckAccessProcessor

**Purpose**: Validate caller access to service

**Implementation**:
```java
public class CheckAccessProcessor implements Processor {
    
    @Override
    public void process(Exchange exchange) throws Exception {
        Message message = exchange.getIn();
        
        // Extract headers
        String callerIp = message.getHeader("callerIp", String.class);
        String serviceName = message.getHeader("serviceName", String.class);
        String version = message.getHeader("version", String.class);
        
        // 1. Resolve organization by IP
        Organization callerOrg = resolveOrganizationByIp(callerIp);
        if (callerOrg == null) {
            logUnauthorizedAccess(callerIp, serviceName, version, 
                "IP not recognized");
            throw new UnauthorizedException(
                "Caller IP not recognized: " + callerIp);
        }
        
        // 2. Load service
        Service service = BitaDataFactory.getService(serviceName, version);
        if (service == null) {
            throw new ServiceNotFoundException(
                "Service not found: " + serviceName + " v" + version);
        }
        
        // 3. Check if organization has access to service collection
        ServiceCollection serviceCollection = service.getServiceCollection();
        boolean hasAccess = checkAccess(callerOrg, serviceCollection);
        
        if (!hasAccess) {
            logUnauthorizedAccess(callerIp, serviceName, version, 
                "Organization does not have access");
            throw new UnauthorizedException(
                "Organization " + callerOrg.getNationalId() + 
                " does not have access to " + serviceName);
        }
        
        // 4. Set caller headers
        message.setHeader("callerNationalId", callerOrg.getNationalId());
        message.setHeader("callerOrganization", callerOrg.getName());
    }
    
    private Organization resolveOrganizationByIp(String callerIp) {
        List<Organization> organizations = BitaDataFactory.getOrganizations();
        
        for (Organization org : organizations) {
            String[] ipAddresses = org.getIpAddress().split(",");
            for (String ip : ipAddresses) {
                if (ip.trim().equals(callerIp)) {
                    return org;
                }
            }
        }
        
        return null;
    }
    
    private boolean checkAccess(Organization org, ServiceCollection sc) {
        List<AccessOrganizationToServiceCollection> accessRules = 
            BitaDataFactory.getAccessRules();
        
        for (AccessOrganizationToServiceCollection access : accessRules) {
            if (access.getOrganization().equals(org) &&
                access.getServiceCollection().equals(sc) &&
                access.getStatus() == AccessStatus.Active) {
                
                // Check enable/revocation dates
                Date now = new Date();
                if (access.getEnableDate() != null && 
                    now.before(access.getEnableDate())) {
                    return false;
                }
                if (access.getRevocationDate() != null && 
                    now.after(access.getRevocationDate())) {
                    return false;
                }
                
                return true;
            }
        }
        
        return false;
    }
    
    private void logUnauthorizedAccess(String callerIp, String serviceName, 
                                       String version, String reason) {
        Point point = Point.measurement("unauthorized_access")
            .tag("caller_ip", callerIp)
            .tag("service_name", serviceName)
            .tag("version", version)
            .addField("reason", reason)
            .time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .build();
        
        influxDB.write(point);
    }
}
```

### 5.3 CallingServiceProcessor

**Purpose**: Invoke local service implementation

**Implementation**:
```java
public class CallingServiceProcessor implements Processor {
    
    @Override
    public void process(Exchange exchange) throws Exception {
        Message message = exchange.getIn();
        
        // Extract service metadata
        String serviceName = message.getHeader("serviceName", String.class);
        String version = message.getHeader("version", String.class);
        String operationName = message.getHeader("operationName", String.class);
        
        // Load service
        Service service = BitaDataFactory.getService(serviceName, version);
        
        // Load service implementation class
        Class<?> serviceClass = loadServiceClass(service);
        Object serviceInstance = serviceClass.newInstance();
        
        // Extract SOAP body
        List<Source> bodyElements = message.getBody(List.class);
        Source bodySource = bodyElements.get(0);
        
        // Convert to DOM
        Document document = convertToDocument(bodySource);
        Element bodyElement = document.getDocumentElement();
        
        // Invoke service method
        Method method = findMethod(serviceClass, operationName);
        Object[] parameters = extractParameters(method, bodyElement);
        Object result = method.invoke(serviceInstance, parameters);
        
        // Convert result to SOAP body
        Source resultSource = convertToSource(result);
        message.setBody(Collections.singletonList(resultSource));
    }
    
    private Class<?> loadServiceClass(Service service) throws Exception {
        byte[] classBytes = service.getJavaClass();
        
        // Create custom class loader
        ClassLoader classLoader = new ByteArrayClassLoader(
            getClass().getClassLoader(), classBytes);
        
        return classLoader.loadClass(service.getClassName());
    }
    
    private Method findMethod(Class<?> clazz, String operationName) {
        for (Method method : clazz.getMethods()) {
            if (method.getName().equals(operationName)) {
                return method;
            }
        }
        throw new NoSuchMethodException("Method not found: " + operationName);
    }
    
    private Object[] extractParameters(Method method, Element bodyElement) {
        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] parameters = new Object[paramTypes.length];
        
        NodeList children = bodyElement.getChildNodes();
        int paramIndex = 0;
        
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element paramElement = (Element) child;
                parameters[paramIndex] = unmarshalParameter(
                    paramElement, paramTypes[paramIndex]);
                paramIndex++;
            }
        }
        
        return parameters;
    }
}
```

### 5.4 PrepareResultProcessor

**Purpose**: Wrap service result in SOAP envelope

**Implementation**:
```java
public class PrepareResultProcessor implements Processor {
    
    @Override
    public void process(Exchange exchange) throws Exception {
        Message message = exchange.getIn();
        
        // Get result from body
        Object result = message.getBody();
        
        // Create SOAP envelope
        SOAPMessage soapMessage = MessageFactory.newInstance(
            SOAPConstants.SOAP_1_2_PROTOCOL).createMessage();
        
        SOAPEnvelope envelope = soapMessage.getSOAPPart().getEnvelope();
        SOAPBody body = envelope.getBody();
        
        // Add WS-Addressing headers
        SOAPHeader header = envelope.getHeader();
        addWSAddressingHeaders(header, message);
        
        // Marshal result to SOAP body
        marshalResult(result, body);
        
        // Set as message body
        message.setBody(soapMessage);
    }
    
    private void addWSAddressingHeaders(SOAPHeader header, Message message) 
            throws SOAPException {
        String requestMessageId = message.getHeader("MessageID", String.class);
        String requestAction = message.getHeader("Action", String.class);
        
        // Add RelatesTo header
        SOAPHeaderElement relatesTo = header.addHeaderElement(
            new QName("http://www.w3.org/2005/08/addressing", "RelatesTo"));
        relatesTo.setTextContent(requestMessageId);
        
        // Add Action header
        SOAPHeaderElement action = header.addHeaderElement(
            new QName("http://www.w3.org/2005/08/addressing", "Action"));
        action.setTextContent(requestAction + "Response");
    }
}
```

---

## 6. Interceptors

### 6.1 Inbound Interceptors

#### 6.1.1 SetNeedHeadersInInterceptor

**Phase**: `PRE_PROTOCOL`

**Purpose**: Extract request metadata

**Implementation**:
```java
public class SetNeedHeadersInInterceptor extends AbstractPhaseInterceptor<Message> {
    
    public SetNeedHeadersInInterceptor() {
        super(Phase.PRE_PROTOCOL);
    }
    
    @Override
    public void handleMessage(Message message) throws Fault {
        // Extract caller IP
        HttpServletRequest request = (HttpServletRequest) 
            message.get(AbstractHTTPDestination.HTTP_REQUEST);
        
        String callerIp = request.getHeader("X-Forwarded-For");
        if (callerIp == null) {
            callerIp = request.getRemoteAddr();
        }
        
        // Parse service name and version from URL
        String requestURI = request.getRequestURI();
        // Format: /services/ServiceName_v1.0
        String[] parts = requestURI.split("/");
        String serviceNameVersion = parts[parts.length - 1];
        String[] nameVersion = serviceNameVersion.split("_v");
        
        String serviceName = nameVersion[0];
        String version = nameVersion.length > 1 ? nameVersion[1] : "1.0";
        
        // Generate request ID
        String requestId = UUID.randomUUID().toString();
        
        // Set timestamp
        long requestTime = System.currentTimeMillis();
        
        // Store in message headers
        message.put("callerIp", callerIp);
        message.put("serviceName", serviceName);
        message.put("version", version);
        message.put("requestId", requestId);
        message.put("requestTime", requestTime);
        
        // Extract operation name (will be set later from SOAP body)
        message.getExchange().put("callerIp", callerIp);
        message.getExchange().put("serviceName", serviceName);
        message.getExchange().put("version", version);
        message.getExchange().put("requestId", requestId);
        message.getExchange().put("requestTime", requestTime);
    }
}
```

#### 6.1.2 SetCallerNationalIdHeaderInInterceptor

**Phase**: `PRE_INVOKE`

**Purpose**: Resolve caller organization

**Implementation**:
```java
public class SetCallerNationalIdHeaderInInterceptor 
        extends AbstractPhaseInterceptor<Message> {
    
    public SetCallerNationalIdHeaderInInterceptor() {
        super(Phase.PRE_INVOKE);
    }
    
    @Override
    public void handleMessage(Message message) throws Fault {
        String callerIp = (String) message.getExchange().get("callerIp");
        
        // Resolve organization by IP
        Organization org = BitaDataFactory.getOrganizationByIp(callerIp);
        
        if (org != null) {
            message.getExchange().put("callerNationalId", org.getNationalId());
            message.getExchange().put("callerOrganization", org.getName());
        }
    }
}
```

#### 6.1.3 SetRequestSoapHeaderInInterceptor

**Phase**: `PRE_INVOKE`

**Purpose**: Extract WS-Addressing headers

**Implementation**:
```java
public class SetRequestSoapHeaderInInterceptor 
        extends AbstractPhaseInterceptor<Message> {
    
    public SetRequestSoapHeaderInInterceptor() {
        super(Phase.PRE_INVOKE);
    }
    
    @Override
    public void handleMessage(Message message) throws Fault {
        // Get SOAP headers
        List<Header> headers = (List<Header>) message.get(Header.HEADER_LIST);
        
        if (headers != null) {
            for (Header header : headers) {
                QName qname = header.getName();
                
                if ("http://www.w3.org/2005/08/addressing".equals(
                        qname.getNamespaceURI())) {
                    
                    String localName = qname.getLocalPart();
                    Object value = header.getObject();
                    
                    message.getExchange().put(localName, value.toString());
                }
            }
        }
    }
}
```

### 6.2 Outbound Interceptors

#### 6.2.1 SetProviderNationalIdAndRequestIdInResponseInterceptor

**Phase**: `PRE_PROTOCOL`

**Purpose**: Add provider metadata to response

**Implementation**:
```java
public class SetProviderNationalIdAndRequestIdInResponseInterceptor 
        extends AbstractPhaseInterceptor<Message> {
    
    public SetProviderNationalIdAndRequestIdInResponseInterceptor() {
        super(Phase.PRE_PROTOCOL);
    }
    
    @Override
    public void handleMessage(Message message) throws Fault {
        Exchange exchange = message.getExchange();
        
        String serviceName = (String) exchange.get("serviceName");
        String version = (String) exchange.get("version");
        String requestId = (String) exchange.get("requestId");
        
        // Load service
        Service service = BitaDataFactory.getService(serviceName, version);
        
        if (service != null) {
            String providerNationalId = 
                service.getOwnerOrganization().getNationalId();
            
            // Add custom SOAP header
            addCustomHeader(message, "ProviderNationalId", providerNationalId);
            addCustomHeader(message, "RequestId", requestId);
        }
    }
    
    private void addCustomHeader(Message message, String name, String value) {
        try {
            QName qname = new QName("http://bita.iais.ir/headers", name);
            Header header = new Header(qname, value);
            
            List<Header> headers = (List<Header>) message.get(Header.HEADER_LIST);
            if (headers == null) {
                headers = new ArrayList<>();
                message.put(Header.HEADER_LIST, headers);
            }
            headers.add(header);
        } catch (Exception e) {
            throw new Fault(e);
        }
    }
}
```

#### 6.2.2 SaveInfluxDataInterceptor

**Phase**: `SEND`

**Purpose**: Log transaction to InfluxDB

**Implementation**:
```java
public class SaveInfluxDataInterceptor extends AbstractPhaseInterceptor<Message> {
    
    @Autowired
    private InfluxService influxService;
    
    public SaveInfluxDataInterceptor() {
        super(Phase.SEND);
    }
    
    @Override
    public void handleMessage(Message message) throws Fault {
        Exchange exchange = message.getExchange();
        
        // Extract transaction data
        String requestId = (String) exchange.get("requestId");
        String callerIp = (String) exchange.get("callerIp");
        String callerNationalId = (String) exchange.get("callerNationalId");
        String serviceName = (String) exchange.get("serviceName");
        String version = (String) exchange.get("version");
        String operationName = (String) exchange.get("operationName");
        Long requestTime = (Long) exchange.get("requestTime");
        
        Service service = BitaDataFactory.getService(serviceName, version);
        String providerNationalId = 
            service.getOwnerOrganization().getNationalId();
        
        long responseTime = System.currentTimeMillis();
        long duration = responseTime - requestTime;
        
        // Determine status
        String status = "success";
        String exceptionMessage = null;
        
        if (exchange.getProperty(Exception.class.getName()) != null) {
            Exception exception = exchange.getProperty(
                Exception.class.getName(), Exception.class);
            status = "error";
            exceptionMessage = exception.getMessage();
        }
        
        // Write to InfluxDB
        Point point = Point.measurement("transactions")
            .tag("caller_national_id", callerNationalId)
            .tag("provider_national_id", providerNationalId)
            .tag("service_name", serviceName)
            .tag("version", version)
            .tag("operation_name", operationName)
            .tag("status", status)
            .addField("request_id", requestId)
            .addField("caller_ip", callerIp)
            .addField("request_time", requestTime)
            .addField("response_time", responseTime)
            .addField("duration_ms", duration)
            .addField("exception_message", exceptionMessage)
            .time(responseTime, TimeUnit.MILLISECONDS)
            .build();
        
        influxService.write(point);
    }
}
```

#### 6.2.3 RemoveBITAHeaderInterceptor

**Phase**: `SEND`

**Purpose**: Remove internal headers from response

**Implementation**:
```java
public class RemoveBITAHeaderInterceptor extends AbstractPhaseInterceptor<Message> {
    
    private static final Set<String> INTERNAL_HEADERS = Set.of(
        "callerIp", "serviceName", "version", "requestId", "requestTime",
        "callerNationalId", "callerOrganization", "ownerDomain",
        "ownerOrganization", "technology", "serviceId"
    );
    
    public RemoveBITAHeaderInterceptor() {
        super(Phase.SEND);
    }
    
    @Override
    public void handleMessage(Message message) throws Fault {
        Exchange exchange = message.getExchange();
        
        // Remove internal headers
        for (String headerName : INTERNAL_HEADERS) {
            exchange.remove(headerName);
            message.remove(headerName);
        }
    }
}
```

---

## 7. Multi-Domain Architecture

### 7.1 Domain Concept

**Domain**: Logical or physical deployment region

**Examples**:
- Tehran (primary data center)
- Mashhad (secondary data center)
- Shiraz (regional office)

**Characteristics**:
- Each ESB instance belongs to one domain
- Services are owned by specific domains
- Cross-domain communication via ActiveMQ

### 7.2 ActiveMQ Integration

**Queue Types**:

| Queue Type | Direction | Purpose |
|------------|-----------|---------|
| `x509-push` | Outbound | Send X509-encrypted requests to remote domain |
| `x509-pop` | Inbound | Receive X509-encrypted requests from remote domains |
| `proxy-push` | Outbound | Send proxy requests to remote domain |
| `proxy-pop` | Inbound | Receive proxy requests from remote domains |

**Message Flow**:

```
┌─────────────────────────────────────────────────────────────┐
│ Domain: Tehran                                               │
│                                                              │
│ Client → ESB (Tehran)                                       │
│           │                                                  │
│           │ Service owned by Mashhad domain                 │
│           │                                                  │
│           └──→ ActiveMQ Queue: x509-push (Mashhad)         │
└────────────────────────┬────────────────────────────────────┘
                         │
                         │ SSL Connection
                         │
┌────────────────────────▼────────────────────────────────────┐
│ Domain: Mashhad                                              │
│                                                              │
│ ActiveMQ Queue: x509-pop                                    │
│           │                                                  │
│           └──→ ESB (Mashhad)                                │
│                  │                                           │
│                  └──→ Local Service                         │
│                         │                                    │
│                         └──→ Response                        │
│                                │                             │
│                  ActiveMQ Queue: x509-push (Tehran) ←───────┘
└────────────────────────┬────────────────────────────────────┘
                         │
                         │ SSL Connection
                         │
┌────────────────────────▼────────────────────────────────────┐
│ Domain: Tehran                                               │
│                                                              │
│ ActiveMQ Queue: x509-pop                                    │
│           │                                                  │
│           └──→ ESB (Tehran)                                 │
│                  │                                           │
│                  └──→ Client                                │
└─────────────────────────────────────────────────────────────┘
```

### 7.3 Load Balancing

**Strategy**: Round-robin across multiple ActiveMQ brokers

**Configuration**:
```java
.loadBalance().roundRobin()
    .to("activemq-mashhad:queue:x509-push?broker1")
    .to("activemq-mashhad:queue:x509-push?broker2")
    .to("activemq-mashhad:queue:x509-push?broker3")
.end()
```

**Failover**:
- If one broker fails, requests routed to other brokers
- ActiveMQ client handles reconnection
- Message persistence ensures no data loss

---

## 8. Configuration

### 8.1 Application Configuration

**BITA.properties**:
```properties
# ESM integration
esmUrl=http://esm.example.com:9000

# Server identification
serverName=Tehran
dnsName=esb.tehran.example.com

# InfluxDB configuration
influxDbUrl=http://influxdb.example.com:8086
influxDbDatabase=esb_transactions
influxDbUsername=esb_user
influxDbPassword=encrypted_password
influxDbRetentionPolicy=autogen

# Test mode
testMode=false

# Cache configuration
cacheDirectory=/var/lib/bita/ESMCaches
cacheRefreshInterval=300000

# Temporary directories
wsdlDirectory=/tmp/wsdl
classDirectory=/tmp/classes
certificateDirectory=/tmp/certificates
```

**keystore2.properties**:
```properties
org.apache.ws.security.crypto.provider=org.apache.ws.security.components.crypto.Merlin
org.apache.ws.security.crypto.merlin.keystore.type=jks
org.apache.ws.security.crypto.merlin.keystore.password=changeit
org.apache.ws.security.crypto.merlin.keystore.file=/etc/bita/keystore.jks
org.apache.ws.security.crypto.merlin.keystore.alias=esb-key
org.apache.ws.security.crypto.merlin.keystore.private.password=changeit
```

**truststore.properties**:
```properties
org.apache.ws.security.crypto.provider=org.apache.ws.security.components.crypto.Merlin
org.apache.ws.security.crypto.merlin.truststore.type=jks
org.apache.ws.security.crypto.merlin.truststore.password=changeit
org.apache.ws.security.crypto.merlin.truststore.file=/var/lib/bita/allTrustStore.jks
```

### 8.2 Spring Boot Configuration

**application.yml**:
```yaml
server:
  port: 8080
  servlet:
    context-path: /

spring:
  application:
    name: esb-master
  
  # CXF configuration
  cxf:
    path: /services
    servlet:
      init:
        service-list-path: /info
  
  # Camel configuration
  camel:
    springboot:
      name: esb-camel-context
      main-run-controller: true
      jmx-enabled: true

# Logging
logging:
  level:
    root: INFO
    ir.iais.bita: DEBUG
    org.apache.cxf: INFO
    org.apache.camel: INFO
  file:
    name: /var/log/bita/esb.log
    max-size: 100MB
    max-history: 30

# Management endpoints
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      influx:
        enabled: true
        uri: ${influxDbUrl}
        db: ${influxDbDatabase}
        user-name: ${influxDbUsername}
        password: ${influxDbPassword}
```

---

## 9. Monitoring and Logging

### 9.1 InfluxDB Schema

**Measurement**: `transactions`

**Tags** (indexed):
- `caller_national_id`: Caller organization ID
- `provider_national_id`: Provider organization ID
- `service_name`: Service name
- `version`: Service version
- `operation_name`: SOAP operation name
- `status`: success, error, unauthorized, timeout

**Fields** (not indexed):
- `request_id`: UUID for correlation
- `caller_ip`: Caller IP address
- `request_time`: Request timestamp (milliseconds)
- `response_time`: Response timestamp (milliseconds)
- `duration_ms`: Request duration (milliseconds)
- `exception_message`: Error message (if status=error)
- `exception_stack_trace`: Stack trace (if status=error)

**Sample Query**:
```sql
SELECT 
    COUNT(*) as total_requests,
    MEAN(duration_ms) as avg_duration,
    PERCENTILE(duration_ms, 95) as p95_duration
FROM transactions
WHERE time > now() - 1h
GROUP BY service_name, status
```

### 9.2 Metrics

**Application Metrics** (Micrometer):
- `esb.requests.total`: Total request count
- `esb.requests.duration`: Request duration histogram
- `esb.requests.errors`: Error count
- `esb.routes.active`: Active Camel routes
- `esb.activemq.connections`: ActiveMQ connection count

**JVM Metrics**:
- `jvm.memory.used`: Memory usage
- `jvm.threads.live`: Thread count
- `jvm.gc.pause`: GC pause time

**System Metrics**:
- `system.cpu.usage`: CPU usage
- `system.load.average.1m`: Load average

### 9.3 Logging

**Log Format**:
```
[%d{yyyy-MM-dd HH:mm:ss.SSS}] [%thread] %-5level %logger{36} - %msg%n
```

**Log Levels**:
- **ERROR**: Critical errors requiring immediate attention
- **WARN**: Warnings (e.g., slow requests, deprecated features)
- **INFO**: Important events (e.g., route creation, service invocation)
- **DEBUG**: Detailed debugging information

**Sample Logs**:
```
[2026-01-28 10:15:30.123] [Camel (esb-camel-context) thread #1] INFO  ir.iais.bita.initiall.BITARouteBuilder - Creating route for service: UserService_v1.0
[2026-01-28 10:15:35.456] [http-nio-8080-exec-1] INFO  ir.iais.bita.processors.CheckAccessProcessor - Access granted: org=10100000002, service=UserService_v1.0
[2026-01-28 10:15:35.789] [http-nio-8080-exec-1] DEBUG ir.iais.bita.processors.CallingServiceProcessor - Invoking operation: getUserById, params=[123]
[2026-01-28 10:15:36.012] [http-nio-8080-exec-1] INFO  ir.iais.bita.interceptors.SaveInfluxDataInterceptor - Transaction logged: requestId=abc-123, duration=556ms, status=success
```

---

## 10. Deployment

### 10.1 WAR Deployment

**Build**:
```bash
mvn clean package
```

**Output**: `target/ESB-2.5.10.war`

**Deployment**:
1. Copy WAR to Tomcat `webapps/` directory
2. Configure `BITA.properties` in Tomcat `conf/` directory
3. Configure keystores and truststores
4. Start Tomcat

**Tomcat Configuration** (`server.xml`):
```xml
<Connector port="8080" protocol="HTTP/1.1"
           connectionTimeout="20000"
           redirectPort="8443"
           maxThreads="200"
           minSpareThreads="10"
           acceptCount="100" />

<Connector port="8443" protocol="org.apache.coyote.http11.Http11NioProtocol"
           maxThreads="200" SSLEnabled="true">
    <SSLHostConfig>
        <Certificate certificateKeystoreFile="/etc/bita/keystore.jks"
                     certificateKeystorePassword="changeit"
                     type="RSA" />
    </SSLHostConfig>
</Connector>
```

### 10.2 Docker Deployment

**Dockerfile**:
```dockerfile
FROM tomcat:9-jdk8

# Copy WAR
COPY target/ESB-2.5.10.war /usr/local/tomcat/webapps/ROOT.war

# Copy configuration
COPY config/BITA.properties /usr/local/tomcat/conf/
COPY config/keystore.jks /etc/bita/
COPY config/truststore.jks /etc/bita/

# Set environment variables
ENV JAVA_OPTS="-Xms2g -Xmx4g -XX:+UseG1GC"

# Expose ports
EXPOSE 8080 8443

# Start Tomcat
CMD ["catalina.sh", "run"]
```

**Build and Run**:
```bash
docker build -t esb-master:2.5.10 .
docker run -d -p 8080:8080 -p 8443:8443 \
    -v /var/lib/bita:/var/lib/bita \
    -v /var/log/bita:/var/log/bita \
    --name esb-master \
    esb-master:2.5.10
```

### 10.3 Kubernetes Deployment

**deployment.yml**:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: esb-master
  namespace: enterprise-services
spec:
  replicas: 3
  selector:
    matchLabels:
      app: esb-master
  template:
    metadata:
      labels:
        app: esb-master
    spec:
      containers:
      - name: esb-master
        image: registry.example.com/esb-master:2.5.10
        ports:
        - containerPort: 8080
          name: http
        - containerPort: 8443
          name: https
        env:
        - name: ESM_URL
          value: "http://esm-service:9000"
        - name: SERVER_NAME
          value: "Tehran"
        - name: INFLUXDB_URL
          value: "http://influxdb:8086"
        resources:
          limits:
            memory: "4Gi"
            cpu: "4"
          requests:
            memory: "2Gi"
            cpu: "2"
        volumeMounts:
        - name: config
          mountPath: /usr/local/tomcat/conf
        - name: certificates
          mountPath: /etc/bita
        - name: cache
          mountPath: /var/lib/bita
        - name: logs
          mountPath: /var/log/bita
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 120
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 10
      volumes:
      - name: config
        configMap:
          name: esb-config
      - name: certificates
        secret:
          secretName: esb-certificates
      - name: cache
        persistentVolumeClaim:
          claimName: esb-cache-pvc
      - name: logs
        persistentVolumeClaim:
          claimName: esb-logs-pvc
---
apiVersion: v1
kind: Service
metadata:
  name: esb-service
  namespace: enterprise-services
spec:
  type: LoadBalancer
  selector:
    app: esb-master
  ports:
  - port: 8080
    targetPort: 8080
    name: http
  - port: 8443
    targetPort: 8443
    name: https
```

---

## 11. Performance Tuning

### 11.1 JVM Tuning

**Recommended JVM Options**:
```bash
JAVA_OPTS="
  -Xms2g -Xmx4g
  -XX:+UseG1GC
  -XX:MaxGCPauseMillis=200
  -XX:ParallelGCThreads=4
  -XX:ConcGCThreads=2
  -XX:InitiatingHeapOccupancyPercent=45
  -XX:+HeapDumpOnOutOfMemoryError
  -XX:HeapDumpPath=/var/log/bita/heapdump.hprof
  -Djava.security.egd=file:/dev/./urandom
"
```

### 11.2 Thread Pool Tuning

**Tomcat Connector**:
```xml
<Connector port="8080" protocol="HTTP/1.1"
           maxThreads="200"
           minSpareThreads="25"
           acceptCount="100"
           connectionTimeout="20000" />
```

**Camel Thread Pool**:
```java
ThreadPoolProfile profile = new ThreadPoolProfileBuilder("custom")
    .poolSize(10)
    .maxPoolSize(50)
    .maxQueueSize(1000)
    .build();

camelContext.getExecutorServiceManager().registerThreadPoolProfile(profile);
```

### 11.3 Connection Pool Tuning

**ActiveMQ Connection Pool**:
```java
PooledConnectionFactory pooledFactory = new PooledConnectionFactory();
pooledFactory.setMaxConnections(10);
pooledFactory.setMaximumActiveSessionPerConnection(100);
pooledFactory.setConnectionFactory(connectionFactory);
```

---

## 12. Security Considerations

### 12.1 Certificate Management

**Certificate Storage**:
- Private keys in keystore (JKS format)
- Public certificates in truststore (JKS format)
- Organization certificates in separate directories

**Certificate Rotation**:
1. Generate new certificate
2. Upload to ESM
3. ESB downloads new certificate
4. Update truststore
5. Restart ESB (or reload routes)

**Certificate Validation**:
- Expiration date checking
- Certificate chain validation
- Revocation checking (optional)

### 12.2 Network Security

**Firewall Rules**:
- Allow inbound: 8080 (HTTP), 8443 (HTTPS)
- Allow outbound: ESM (9000), ActiveMQ (61617), InfluxDB (8086)
- Block all other ports

**SSL/TLS**:
- TLS 1.2 or higher
- Strong cipher suites only
- Certificate-based authentication for ActiveMQ

### 12.3 Access Control

**IP Whitelisting**:
- Organization IP addresses stored in ESM
- ESB validates caller IP on every request
- Unauthorized IPs rejected with SOAP Fault

**Service-Level Authorization**:
- Access control rules stored in ESM
- ESB enforces access on every request
- Unauthorized access logged to InfluxDB

---

## 13. Troubleshooting

### 13.1 Common Issues

**Issue**: Service not found
**Cause**: Service not registered in ESM or ESB cache outdated
**Solution**: 
1. Verify service exists in ESM
2. Restart ESB to refresh cache
3. Check ESB logs for route creation errors

**Issue**: Unauthorized access
**Cause**: Caller IP not recognized or access not granted
**Solution**:
1. Verify organization IP addresses in ESM
2. Verify access granted in ESM
3. Check ESB logs for access denial reason

**Issue**: WS-Security validation failed
**Cause**: Certificate mismatch or expired certificate
**Solution**:
1. Verify caller certificate uploaded to ESM
2. Verify certificate not expired
3. Verify ESB truststore contains caller certificate
4. Check ESB logs for validation errors

**Issue**: Service timeout
**Cause**: Slow service or network issues
**Solution**:
1. Check service performance
2. Increase timeout configuration
3. Check ActiveMQ connection (for remote services)
4. Review InfluxDB metrics for slow services

### 13.2 Debugging

**Enable Debug Logging**:
```properties
logging.level.ir.iais.bita=DEBUG
logging.level.org.apache.cxf=DEBUG
logging.level.org.apache.camel=DEBUG
```

**CXF Message Logging**:
```xml
<bean id="loggingFeature" class="org.apache.cxf.feature.LoggingFeature">
    <property name="prettyLogging" value="true"/>
    <property name="verbose" value="true"/>
</bean>
```

**Camel Tracer**:
```java
camelContext.setTracing(true);
```

---

## 14. Conclusion

ESB-Master provides a robust, secure, and scalable service gateway for the enterprise service ecosystem. It dynamically creates routes based on ESM configuration, enforces WS-Security policies, validates access control, and provides comprehensive transaction logging.

The system supports multi-domain deployments with cross-domain communication via ActiveMQ, load balancing, and failover. It integrates seamlessly with ESM for centralized configuration and monitoring.

---

**Document Version**: 1.0  
**Last Updated**: January 28, 2026  
**Maintained By**: ESB Development Team
