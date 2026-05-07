# ESM (Enterprise Service Management) - Technical Specification

## Document Information

**Project**: Enterprise Service Management (ESM)  
**Version**: 3.0.0  
**Technology**: Java 8, Apache Wicket, Apache CXF, Hibernate  
**Purpose**: Central registry and management platform for enterprise services

---

## 1. System Overview

ESM serves as the central management and governance platform for the enterprise service ecosystem. It provides web-based interfaces for service registration, organization management, access control, and monitoring.

### 1.1 Key Capabilities

- **Service Registry**: WSDL-based service catalog
- **Organization Management**: Multi-tenant organization hierarchy
- **Access Control**: Permission-based service access
- **Certificate Management**: X.509 certificate repository
- **Configuration Distribution**: Centralized configuration for ESB instances
- **Monitoring Dashboard**: Real-time service usage metrics

---

## 2. Architecture

### 2.1 Layered Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Presentation Layer                        │
│  - Apache Wicket Web UI                                      │
│  - REST API (Jersey)                                         │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│                     Service Layer                            │
│  - Service Management                                        │
│  - Organization Management                                   │
│  - Access Control                                            │
│  - Certificate Management                                    │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│                    Business Logic Layer                      │
│  - Validation                                                │
│  - Workflow Management                                       │
│  - Security Enforcement                                      │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│                   Data Access Layer                          │
│  - Hibernate ORM                                             │
│  - JPA Repositories                                          │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│                    Data Layer                                │
│  - PostgreSQL Database                                       │
│  - InfluxDB (Metrics)                                        │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 Technology Stack

**Web Framework**:
- Apache Wicket 8.x (Component-based web framework)
- Bootstrap 3.x (UI styling)
- jQuery (Client-side scripting)

**REST API**:
- Jersey 2.19 (JAX-RS implementation)
- Jackson (JSON serialization)
- Gson (Alternative JSON library)

**SOAP Services**:
- Apache CXF 3.1.11 (SOAP engine)
- JAXB (XML binding)
- WSS4J (WS-Security)

**Data Layer**:
- Hibernate 5.x (ORM)
- JPA 2.1 (Persistence API)
- PostgreSQL (Primary database)
- InfluxDB (Time-series metrics)

**Integration**:
- Apache Camel 3.18.2 (Integration framework)
- Apache ActiveMQ (Message broker)
- Apache Kafka (Event streaming)

**Security**:
- Java XML Digital Signature API
- BouncyCastle (Cryptography)
- WSS4J (WS-Security)

**Monitoring**:
- JavaMelody (Application monitoring)
- InfluxDB (Metrics storage)

**Deployment**:
- Docker (Containerization)
- Kubernetes (Orchestration)
- Maven (Build tool)

---

## 3. Domain Model

### 3.1 Entity Relationship Diagram

```
┌──────────────────┐         ┌──────────────────┐
│  Organization    │◄────────┤  Employee        │
│                  │         │                  │
│ - nationalId     │         │ - id             │
│ - name           │         │ - username       │
│ - ipAddress      │         │ - role           │
│ - parent         │         └──────────────────┘
└────────┬─────────┘
         │
         │ owns
         │
┌────────▼─────────┐         ┌──────────────────┐
│ ServiceCollection│◄────────┤  Service         │
│                  │         │                  │
│ - name           │ contains│ - name           │
│ - description    │         │ - version        │
└────────┬─────────┘         │ - wsdl           │
         │                   │ - technology     │
         │                   └──────────────────┘
         │
         │ accessed by
         │
┌────────▼─────────────────────────────┐
│ AccessOrganizationToServiceCollection│
│                                      │
│ - organization                       │
│ - serviceCollection                  │
│ - status (Active/Revoked)            │
│ - enableDate                         │
│ - approvalLetter                     │
└──────────────────────────────────────┘
```

### 3.2 Core Entities

#### 3.2.1 Organization

**Package**: `ir.iais.esm.domain`

**Attributes**:
```java
@Entity
@Table(name = "organization")
public class Organization implements Serializable {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String nationalId;  // Unique organization identifier
    
    @Column(nullable = false)
    private String name;
    
    private String title;       // Display name
    
    @Column(length = 1000)
    private String ipAddress;   // Comma-separated IP addresses
    
    @ManyToOne
    @JoinColumn(name = "parent_id")
    private Organization parent;
    
    @OneToMany(mappedBy = "parent")
    private Set<Organization> children;
    
    @OneToMany(mappedBy = "organization")
    private Set<PublicKey> certificates;
    
    @OneToMany(mappedBy = "owner")
    private Set<ServiceCollection> ownedServiceCollections;
    
    @Enumerated(EnumType.STRING)
    private OrganizationStatus status;
    
    private String approvalLetter;
    
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;
    
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedAt;
}
```

**Business Rules**:
- National ID must be unique across all organizations
- IP addresses stored as comma-separated list (e.g., "192.168.1.1,192.168.1.2")
- Parent-child relationships support hierarchical organization structure
- Organization can own multiple service collections
- Organization can have multiple X.509 certificates

#### 3.2.2 Service

**Package**: `ir.iais.bimaui.wsdl`

**Attributes**:
```java
@Entity
@Table(name = "service")
public class Service implements Serializable {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false)
    private String version;
    
    @Lob
    @Column(columnDefinition = "TEXT")
    private String wsdl;        // WSDL content
    
    @Lob
    @Column(columnDefinition = "BYTEA")
    private byte[] javaClass;   // Compiled Java class
    
    @Enumerated(EnumType.STRING)
    private ServiceTechnology technology;  // Proxy, X509EncryptionAndSign
    
    @ManyToOne
    @JoinColumn(name = "service_collection_id")
    private ServiceCollection serviceCollection;
    
    @ManyToOne
    @JoinColumn(name = "owner_organization_id")
    private Organization ownerOrganization;
    
    @ManyToOne
    @JoinColumn(name = "owner_domain_id")
    private Domain ownerDomain;
    
    @Column(nullable = false)
    private Boolean active = true;
    
    private String description;
    
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;
    
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedAt;
}
```

**Service Technologies**:
- **Proxy**: Simple HTTP proxy (no encryption)
- **X509EncryptionAndSign**: WS-Security with X.509 certificates

**Business Rules**:
- Service name + version must be unique
- WSDL must be valid XML
- Java class is optional (required for custom implementations)
- Service must belong to exactly one service collection
- Service must have owner organization and domain

#### 3.2.3 ServiceCollection

**Package**: `ir.iais.bimaui.wsdl`

**Attributes**:
```java
@Entity
@Table(name = "service_collection")
public class ServiceCollection implements Serializable {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String name;
    
    private String description;
    
    @ManyToOne
    @JoinColumn(name = "owner_organization_id")
    private Organization owner;
    
    @OneToMany(mappedBy = "serviceCollection")
    private Set<Service> services;
    
    @OneToMany(mappedBy = "serviceCollection")
    private Set<AccessOrganizationToServiceCollection> accessRecords;
    
    @Column(nullable = false)
    private Boolean active = true;
    
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;
}
```

**Business Rules**:
- Service collection name must be unique
- Service collection can contain multiple services
- Service collection has one owner organization
- Other organizations can request access to service collection

#### 3.2.4 AccessOrganizationToServiceCollection

**Package**: `ir.iais.esm.domain`

**Attributes**:
```java
@Entity
@Table(name = "access_organization_to_service_collection")
public class AccessOrganizationToServiceCollection implements Serializable {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;
    
    @ManyToOne
    @JoinColumn(name = "service_collection_id", nullable = false)
    private ServiceCollection serviceCollection;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccessStatus status;  // Active, Revoked, Deleted
    
    @Temporal(TemporalType.DATE)
    private Date enableDate;
    
    @Temporal(TemporalType.DATE)
    private Date revocationDate;
    
    private String approvalLetter;      // Reference to approval document
    
    @Lob
    private String serviceAgreement;    // Legal agreement text
    
    @ManyToOne
    @JoinColumn(name = "creator_id")
    private User creator;
    
    @ManyToOne
    @JoinColumn(name = "editor_id")
    private User editor;
    
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;
    
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedAt;
}
```

**Access Status**:
- **Active**: Organization has active access
- **Revoked**: Access has been revoked
- **Deleted**: Access record soft-deleted

**Business Rules**:
- Organization + ServiceCollection must be unique
- Enable date must be before revocation date
- Approval letter required for active access
- Status changes must be audited (creator/editor tracking)

#### 3.2.5 PublicKey (Certificate)

**Package**: `ir.iais.bimaui.publicKey`

**Attributes**:
```java
@Entity
@Table(name = "public_key")
public class PublicKey implements Serializable {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;
    
    @Lob
    @Column(columnDefinition = "TEXT")
    private String certificate;     // PEM-encoded X.509 certificate
    
    private String alias;           // Certificate alias
    
    @Temporal(TemporalType.DATE)
    private Date expirationDate;
    
    @Column(nullable = false)
    private Boolean active = true;
    
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;
}
```

**Business Rules**:
- Certificate must be valid X.509 format
- Certificate must be PEM-encoded
- Organization can have multiple certificates
- Expired certificates should be marked inactive

#### 3.2.6 Domain

**Package**: `ir.iais.bimaui.wsdl`

**Attributes**:
```java
@Entity
@Table(name = "domain")
public class Domain implements Serializable {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String name;        // e.g., "Tehran", "Mashhad"
    
    private String description;
    
    @OneToMany(mappedBy = "ownerDomain")
    private Set<Service> services;
    
    @Column(nullable = false)
    private Boolean active = true;
}
```

**Business Rules**:
- Domain name must be unique
- Domain represents physical or logical service deployment region
- Services owned by domain are deployed on ESB instances in that domain

#### 3.2.7 ActiveMQConfig

**Package**: `ir.iais.bimaui.wsdl`

**Attributes**:
```java
@Entity
@Table(name = "activemq_config")
public class ActiveMQConfig implements Serializable {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String name;        // Switch name
    
    @Column(nullable = false)
    private String brokerUrl;   // ssl://host:port
    
    private String username;
    private String password;
    
    @Lob
    private String trustStore;          // Base64-encoded trust store
    
    private String trustStorePassword;
    
    @ElementCollection
    @CollectionTable(name = "activemq_queues")
    private List<String> queues;        // Queue names
    
    @Column(nullable = false)
    private Boolean active = true;
}
```

**Business Rules**:
- ActiveMQ configuration defines inter-domain communication
- Broker URL must use SSL (ssl://)
- Trust store required for SSL connection
- Queues define available message channels

---

## 4. Web UI (Apache Wicket)

### 4.1 Page Structure

**Base Page** (`ir.iais.bimaui.page.BasePage`):
- Common header and footer
- Navigation menu
- User session management
- SSO integration

**Key Pages**:

1. **Service Management**:
   - `ServiceListPage`: List all services
   - `ServiceEditPage`: Create/edit service
   - `ServiceDetailPage`: View service details
   - `WsdlUploadPage`: Upload WSDL file

2. **Organization Management**:
   - `OrganizationListPage`: List all organizations
   - `OrganizationEditPage`: Create/edit organization
   - `OrganizationDetailPage`: View organization details
   - `CertificateManagementPage`: Manage organization certificates

3. **Access Control**:
   - `AccessManagementPage`: Manage service access
   - `AccessRequestPage`: Request service access
   - `AccessApprovalPage`: Approve access requests

4. **Monitoring**:
   - `DashboardPage`: Service usage dashboard
   - `TransactionLogPage`: View transaction logs
   - `ServiceMetricsPage`: Service performance metrics

### 4.2 Authentication and Authorization

**SSO Integration** (`ir.iais.bimaui.sso.SSOAuthenticator`):

```java
public class SSOAuthenticator {
    
    private String ssoUrl;
    private String aesEncryptionKey;
    private String callbackUrl;
    
    public boolean authenticate(String token) {
        // 1. Decrypt token using AES key
        String decryptedToken = AESUtil.decrypt(token, aesEncryptionKey);
        
        // 2. Parse token (JSON format)
        SSOToken ssoToken = parseToken(decryptedToken);
        
        // 3. Validate token expiration
        if (ssoToken.isExpired()) {
            return false;
        }
        
        // 4. Load user roles from SSO
        List<String> roles = loadUserRoles(ssoToken.getUsername());
        
        // 5. Create user session
        createUserSession(ssoToken.getUsername(), roles);
        
        return true;
    }
    
    public String getLoginUrl() {
        return ssoUrl + "/login?callback=" + callbackUrl;
    }
}
```

**Authorization Strategy** (`ir.iais.bimaui.auth.AuthorizationStrategy`):

```java
public class AuthorizationStrategy extends AbstractPageAuthorizationStrategy {
    
    @Override
    protected boolean isAuthorized() {
        // Check if user is authenticated
        if (!isUserAuthenticated()) {
            return false;
        }
        
        // Check page-level authorization
        Class<? extends Page> pageClass = getPageClass();
        RequiredRoles annotation = pageClass.getAnnotation(RequiredRoles.class);
        
        if (annotation != null) {
            String[] requiredRoles = annotation.value();
            return hasAnyRole(requiredRoles);
        }
        
        return true;
    }
    
    private boolean hasAnyRole(String[] roles) {
        User user = getCurrentUser();
        for (String role : roles) {
            if (user.hasRole(role)) {
                return true;
            }
        }
        return false;
    }
}
```

**Role Definitions** (`roles.properties`):
```properties
ADMIN=مدیر سیستم
superadmin=مدیر ارشد
representativeOrg=نماینده سازمان
managementOrg=مدیریت سازمان
supervisorWsdl=ناظر سرویس
developers=توسعه دهنده
reports=گزارش گیر
```

---

## 5. REST API

### 5.1 ESB Integration Endpoints

**Base URL**: `/rest/api/`

**Authentication**: None (internal network only)

#### 5.1.1 Load All Organizations

**Endpoint**: `GET /rest/services/loadAllOrganizations`

**Response**:
```json
[
  {
    "id": 1,
    "nationalId": "10100000001",
    "name": "Organization A",
    "title": "سازمان الف",
    "ipAddress": "192.168.1.1,192.168.1.2",
    "parentId": null,
    "status": "ACTIVE",
    "certificates": [
      {
        "id": 1,
        "alias": "org-a-cert",
        "certificate": "-----BEGIN CERTIFICATE-----\n...\n-----END CERTIFICATE-----",
        "expirationDate": "2027-01-01"
      }
    ]
  }
]
```

#### 5.1.2 Load All WSDLs

**Endpoint**: `GET /rest/services/loadAllWsdl`

**Response**:
```json
[
  {
    "id": 1,
    "name": "UserService",
    "version": "1.0",
    "wsdl": "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<definitions ...>",
    "technology": "X509EncryptionAndSign",
    "serviceCollectionName": "UserManagement",
    "ownerOrganizationNationalId": "10100000001",
    "ownerDomainName": "Tehran",
    "active": true
  }
]
```

#### 5.1.3 Load All Java Classes

**Endpoint**: `GET /rest/services/loadAllClasses`

**Response**:
```json
[
  {
    "serviceName": "UserService",
    "version": "1.0",
    "className": "ir.iais.services.UserServiceImpl",
    "classData": "yv66vgAAADQAOwoAC..."  // Base64-encoded bytecode
  }
]
```

#### 5.1.4 Load All Access Services

**Endpoint**: `GET /rest/services/loadAllAccessServices`

**Response**:
```json
[
  {
    "organizationNationalId": "10100000002",
    "serviceCollectionName": "UserManagement",
    "status": "Active",
    "enableDate": "2025-01-01",
    "revocationDate": null
  }
]
```

#### 5.1.5 Load All ActiveMQ Switches

**Endpoint**: `GET /rest/services/loadAllActivemqSwitches`

**Response**:
```json
[
  {
    "name": "Tehran-Mashhad",
    "brokerUrl": "ssl://activemq.mashhad.example.com:61617",
    "username": "esb-user",
    "password": "encrypted-password",
    "trustStore": "MIIKEAIBAzCCCc...",  // Base64-encoded
    "trustStorePassword": "changeit",
    "queues": [
      "x509-push",
      "x509-pop",
      "proxy-push",
      "proxy-pop"
    ]
  }
]
```

#### 5.1.6 Load Server Name

**Endpoint**: `GET /rest/services/loadServerName`

**Response**:
```json
{
  "serverName": "Tehran",
  "dnsName": "esb.tehran.example.com"
}
```

#### 5.1.7 Get Service WSDL

**Endpoint**: `GET /rest/services/desc/{serviceName}/{version}`

**Parameters**:
- `serviceName`: Service name
- `version`: Service version

**Response**: WSDL XML content

---

## 6. WS-Security Implementation

### 6.1 Security Configuration

**Keystore Configuration** (`keystore2.properties`):
```properties
org.apache.ws.security.crypto.provider=org.apache.ws.security.components.crypto.Merlin
org.apache.ws.security.crypto.merlin.keystore.type=jks
org.apache.ws.security.crypto.merlin.keystore.password=changeit
org.apache.ws.security.crypto.merlin.keystore.file=/path/to/keystore.jks
org.apache.ws.security.crypto.merlin.keystore.alias=esm-key
org.apache.ws.security.crypto.merlin.keystore.private.password=changeit
```

**Truststore Configuration** (`truststore.properties`):
```properties
org.apache.ws.security.crypto.provider=org.apache.ws.security.components.crypto.Merlin
org.apache.ws.security.crypto.merlin.truststore.type=jks
org.apache.ws.security.crypto.merlin.truststore.password=changeit
org.apache.ws.security.crypto.merlin.truststore.file=/path/to/truststore.jks
```

### 6.2 Certificate Validation

**CheckSignature Class** (`ir.iais.Signature.CheckSignature`):

```java
public class CheckSignature {
    
    public boolean validateSignature(Document soapMessage) throws Exception {
        // 1. Extract Signature element
        NodeList signatureNodes = soapMessage.getElementsByTagNameNS(
            XMLSignature.XMLNS, "Signature");
        
        if (signatureNodes.getLength() == 0) {
            throw new SecurityException("No signature found");
        }
        
        Element signatureElement = (Element) signatureNodes.item(0);
        
        // 2. Create XMLSignature object
        XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");
        DOMValidateContext validateContext = new DOMValidateContext(
            new X509KeySelector(), signatureElement);
        
        XMLSignature signature = factory.unmarshalXMLSignature(validateContext);
        
        // 3. Validate signature
        boolean isValid = signature.validate(validateContext);
        
        if (!isValid) {
            // Check each reference
            for (Reference ref : signature.getSignedInfo().getReferences()) {
                boolean refValid = ref.validate(validateContext);
                if (!refValid) {
                    System.err.println("Invalid reference: " + ref.getURI());
                }
            }
        }
        
        return isValid;
    }
    
    private static class X509KeySelector extends KeySelector {
        @Override
        public KeySelectorResult select(KeyInfo keyInfo, 
                                        Purpose purpose,
                                        AlgorithmMethod method,
                                        XMLCryptoContext context) {
            // Extract X.509 certificate from KeyInfo
            for (XMLStructure xmlStructure : keyInfo.getContent()) {
                if (xmlStructure instanceof X509Data) {
                    X509Data x509Data = (X509Data) xmlStructure;
                    for (Object obj : x509Data.getContent()) {
                        if (obj instanceof X509Certificate) {
                            final X509Certificate cert = (X509Certificate) obj;
                            return new KeySelectorResult() {
                                public Key getKey() {
                                    return cert.getPublicKey();
                                }
                            };
                        }
                    }
                }
            }
            throw new KeySelectorException("No X509Certificate found");
        }
    }
}
```

### 6.3 WSDL Security Policy

**Sample WSDL with WS-Security Policy 1.1**:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://schemas.xmlsoap.org/wsdl/"
             xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap12/"
             xmlns:wsp="http://www.w3.org/ns/ws-policy"
             xmlns:sp="http://docs.oasis-open.org/ws-sx/ws-securitypolicy/200702"
             targetNamespace="http://example.com/service">
    
    <wsp:Policy wsu:Id="ServicePolicy">
        <wsp:ExactlyOne>
            <wsp:All>
                <!-- Asymmetric Binding -->
                <sp:AsymmetricBinding>
                    <wsp:Policy>
                        <sp:InitiatorToken>
                            <wsp:Policy>
                                <sp:X509Token sp:IncludeToken="...AlwaysToRecipient">
                                    <wsp:Policy>
                                        <sp:WssX509V3Token10/>
                                    </wsp:Policy>
                                </sp:X509Token>
                            </wsp:Policy>
                        </sp:InitiatorToken>
                        <sp:RecipientToken>
                            <wsp:Policy>
                                <sp:X509Token sp:IncludeToken="...Never">
                                    <wsp:Policy>
                                        <sp:WssX509V3Token10/>
                                    </wsp:Policy>
                                </sp:X509Token>
                            </wsp:Policy>
                        </sp:RecipientToken>
                        <sp:AlgorithmSuite>
                            <wsp:Policy>
                                <sp:Basic256Sha256Rsa15/>
                            </wsp:Policy>
                        </sp:AlgorithmSuite>
                        <sp:Layout>
                            <wsp:Policy>
                                <sp:Strict/>
                            </wsp:Policy>
                        </sp:Layout>
                        <sp:IncludeTimestamp/>
                        <sp:ProtectTokens/>
                        <sp:OnlySignEntireHeadersAndBody/>
                    </wsp:Policy>
                </sp:AsymmetricBinding>
                
                <!-- WS-Addressing -->
                <wsam:Addressing wsp:Optional="false"/>
                
                <!-- Signed Parts -->
                <sp:SignedParts>
                    <sp:Body/>
                    <sp:Header Name="To" Namespace="http://www.w3.org/2005/08/addressing"/>
                    <sp:Header Name="From" Namespace="http://www.w3.org/2005/08/addressing"/>
                    <sp:Header Name="FaultTo" Namespace="http://www.w3.org/2005/08/addressing"/>
                    <sp:Header Name="ReplyTo" Namespace="http://www.w3.org/2005/08/addressing"/>
                    <sp:Header Name="MessageID" Namespace="http://www.w3.org/2005/08/addressing"/>
                    <sp:Header Name="RelatesTo" Namespace="http://www.w3.org/2005/08/addressing"/>
                    <sp:Header Name="Action" Namespace="http://www.w3.org/2005/08/addressing"/>
                </sp:SignedParts>
                
                <!-- Encrypted Parts -->
                <sp:EncryptedParts>
                    <sp:Body/>
                </sp:EncryptedParts>
                
                <!-- Trust 1.3 -->
                <sp:Wss11>
                    <wsp:Policy>
                        <sp:MustSupportRefKeyIdentifier/>
                        <sp:MustSupportRefIssuerSerial/>
                        <sp:MustSupportRefThumbprint/>
                        <sp:MustSupportRefEncryptedKey/>
                    </wsp:Policy>
                </sp:Wss11>
            </wsp:All>
        </wsp:ExactlyOne>
    </wsp:Policy>
    
    <!-- Service and Port definitions -->
    <portType name="ServicePortType">
        <operation name="Operation1">
            <input message="tns:InputMessage"/>
            <output message="tns:OutputMessage"/>
        </operation>
    </portType>
    
    <binding name="ServiceBinding" type="tns:ServicePortType">
        <wsp:PolicyReference URI="#ServicePolicy"/>
        <soap:binding transport="http://schemas.xmlsoap.org/soap/http"/>
        <operation name="Operation1">
            <soap:operation soapAction="http://example.com/Operation1"/>
            <input>
                <soap:body use="literal"/>
            </input>
            <output>
                <soap:body use="literal"/>
            </output>
        </operation>
    </binding>
    
    <service name="Service">
        <port name="ServicePort" binding="tns:ServiceBinding">
            <soap:address location="http://example.com/service"/>
        </port>
    </service>
</definitions>
```

---

## 7. Database Schema

### 7.1 Core Tables

**organization**:
```sql
CREATE TABLE organization (
    id BIGSERIAL PRIMARY KEY,
    national_id VARCHAR(20) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    title VARCHAR(255),
    ip_address VARCHAR(1000),
    parent_id BIGINT REFERENCES organization(id),
    status VARCHAR(50) NOT NULL,
    approval_letter VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_org_national_id ON organization(national_id);
CREATE INDEX idx_org_parent ON organization(parent_id);
```

**service_collection**:
```sql
CREATE TABLE service_collection (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL,
    description TEXT,
    owner_organization_id BIGINT REFERENCES organization(id),
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sc_name ON service_collection(name);
CREATE INDEX idx_sc_owner ON service_collection(owner_organization_id);
```

**service**:
```sql
CREATE TABLE service (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    version VARCHAR(50) NOT NULL,
    wsdl TEXT,
    java_class BYTEA,
    technology VARCHAR(100) NOT NULL,
    service_collection_id BIGINT REFERENCES service_collection(id),
    owner_organization_id BIGINT REFERENCES organization(id),
    owner_domain_id BIGINT REFERENCES domain(id),
    active BOOLEAN DEFAULT TRUE,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(name, version)
);

CREATE INDEX idx_service_name_version ON service(name, version);
CREATE INDEX idx_service_collection ON service(service_collection_id);
CREATE INDEX idx_service_owner_org ON service(owner_organization_id);
CREATE INDEX idx_service_owner_domain ON service(owner_domain_id);
```

**access_organization_to_service_collection**:
```sql
CREATE TABLE access_organization_to_service_collection (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organization(id),
    service_collection_id BIGINT NOT NULL REFERENCES service_collection(id),
    status VARCHAR(50) NOT NULL,
    enable_date DATE,
    revocation_date DATE,
    approval_letter VARCHAR(500),
    service_agreement TEXT,
    creator_id BIGINT REFERENCES users(id),
    editor_id BIGINT REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(organization_id, service_collection_id)
);

CREATE INDEX idx_access_org ON access_organization_to_service_collection(organization_id);
CREATE INDEX idx_access_sc ON access_organization_to_service_collection(service_collection_id);
CREATE INDEX idx_access_status ON access_organization_to_service_collection(status);
```

**public_key**:
```sql
CREATE TABLE public_key (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organization(id),
    certificate TEXT NOT NULL,
    alias VARCHAR(255),
    expiration_date DATE,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_pk_org ON public_key(organization_id);
CREATE INDEX idx_pk_active ON public_key(active);
```

**domain**:
```sql
CREATE TABLE domain (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL,
    description TEXT,
    active BOOLEAN DEFAULT TRUE
);
```

**activemq_config**:
```sql
CREATE TABLE activemq_config (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL,
    broker_url VARCHAR(500) NOT NULL,
    username VARCHAR(255),
    password VARCHAR(255),
    trust_store TEXT,
    trust_store_password VARCHAR(255),
    active BOOLEAN DEFAULT TRUE
);

CREATE TABLE activemq_queues (
    activemq_config_id BIGINT REFERENCES activemq_config(id),
    queue_name VARCHAR(255)
);
```

---

## 8. Configuration

### 8.1 Application Configuration

**BITA.properties**:
```properties
# WSDL2Java tool path
wsdl2java=/path/to/apache-cxf/bin/wsdl2java

# Server identification
serverName=Tehran
dnsName=esb.tehran.example.com

# InfluxDB configuration
influxDbUrl=http://influxdb.example.com:8086
influxDbDatabase=esm_metrics
influxDbUsername=esm_user
influxDbPassword=encrypted_password

# Client certificate (Base64-encoded)
clientCertData=MIIKEAIBAzCCCc...
clientKeyData=MIIEvQIBADANBgkq...
```

**ssoconfig.properties**:
```properties
# SSO configuration
ssoUrl=http://sso.irica.ir
aesEncryptionKey=1234567890123456
callbackUrl=http://esm.example.com:9000/sso/callback
migrationDate=2024-01-01
userSessionTTL=3600
```

**roles.properties**:
```properties
ADMIN=مدیر سیستم
USER=کاربر
superadmin=مدیر ارشد
representativeOrg=نماینده سازمان
managementOrg=مدیریت سازمان
supervisorWsdl=ناظر سرویس
developers=توسعه دهنده
reports=گزارش گیر
```

### 8.2 Deployment Configuration

**deployment.yml** (Kubernetes):
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: esm
  namespace: enterprise-services
spec:
  replicas: 1
  selector:
    matchLabels:
      app: esm
  template:
    metadata:
      labels:
        app: esm
    spec:
      containers:
      - name: esm
        image: registry.example.com/esm:3.0.0
        ports:
        - containerPort: 9000
          name: http
        env:
        - name: DB_HOST
          value: postgresql.enterprise-services.svc.cluster.local
        - name: DB_PORT
          value: "5432"
        - name: DB_NAME
          value: esm
        - name: DB_USER
          valueFrom:
            secretKeyRef:
              name: esm-db-credentials
              key: username
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: esm-db-credentials
              key: password
        resources:
          limits:
            memory: "4Gi"
            cpu: "4"
          requests:
            memory: "2Gi"
            cpu: "2"
        volumeMounts:
        - name: config
          mountPath: /app/config
        livenessProbe:
          httpGet:
            path: /health
            port: 9000
          initialDelaySeconds: 60
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /ready
            port: 9000
          initialDelaySeconds: 30
          periodSeconds: 10
      volumes:
      - name: config
        configMap:
          name: esm-config
---
apiVersion: v1
kind: Service
metadata:
  name: esm-service
  namespace: enterprise-services
spec:
  type: NodePort
  selector:
    app: esm
  ports:
  - port: 9000
    targetPort: 9000
    nodePort: 31700
    name: http
```

---

## 9. Monitoring and Logging

### 9.1 InfluxDB Metrics

**Measurement**: `esm_events`

**Tags**:
- `event_type`: service_registered, organization_created, access_granted, etc.
- `user`: Username performing action
- `organization`: Organization national ID

**Fields**:
- `timestamp`: Event timestamp
- `details`: JSON-encoded event details

**Example Write**:
```java
public void logServiceRegistration(Service service, User user) {
    Point point = Point.measurement("esm_events")
        .tag("event_type", "service_registered")
        .tag("user", user.getUsername())
        .tag("organization", service.getOwnerOrganization().getNationalId())
        .addField("service_name", service.getName())
        .addField("service_version", service.getVersion())
        .addField("technology", service.getTechnology().name())
        .time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
        .build();
    
    influxDB.write(point);
}
```

### 9.2 JavaMelody Monitoring

**Enabled Metrics**:
- HTTP request statistics
- SQL query performance
- JVM memory usage
- Thread pool utilization
- Session count

**Access**: `/monitoring` (admin role required)

---

## 10. API Usage Examples

### 10.1 Service Registration Flow

**Step 1: Create Organization**:
```http
POST /rest/api/organizations
Content-Type: application/json

{
  "nationalId": "10100000003",
  "name": "Organization C",
  "title": "سازمان ج",
  "ipAddress": "192.168.3.1,192.168.3.2"
}
```

**Step 2: Upload Certificate**:
```http
POST /rest/api/organizations/10100000003/certificates
Content-Type: application/json

{
  "alias": "org-c-cert",
  "certificate": "-----BEGIN CERTIFICATE-----\nMIID...\n-----END CERTIFICATE-----"
}
```

**Step 3: Create Service Collection**:
```http
POST /rest/api/service-collections
Content-Type: application/json

{
  "name": "PaymentServices",
  "description": "Payment processing services",
  "ownerOrganizationNationalId": "10100000003"
}
```

**Step 4: Register Service**:
```http
POST /rest/api/services
Content-Type: application/json

{
  "name": "PaymentService",
  "version": "1.0",
  "wsdl": "<?xml version=\"1.0\"?>...",
  "technology": "X509EncryptionAndSign",
  "serviceCollectionName": "PaymentServices",
  "ownerOrganizationNationalId": "10100000003",
  "ownerDomainName": "Tehran"
}
```

**Step 5: Grant Access**:
```http
POST /rest/api/access
Content-Type: application/json

{
  "organizationNationalId": "10100000002",
  "serviceCollectionName": "PaymentServices",
  "enableDate": "2026-02-01",
  "approvalLetter": "Approval-2026-001"
}
```

---

## 11. Conclusion

ESM provides a comprehensive platform for managing enterprise services with strong security, access control, and monitoring capabilities. The system serves as the central registry and governance layer for the ESB infrastructure, enabling secure inter-organizational service integration.

---

**Document Version**: 1.0  
**Last Updated**: January 28, 2026  
**Maintained By**: ESM Development Team
