# Database Schema Specification

## Document Information

**Project**: BITA - Next-Generation Enterprise Service Gateway  
**Version**: 2.0  
**Date**: January 28, 2026  
**Database**: PostgreSQL 15+  
**ORM**: Hibernate 6.x with Envers

---

## 1. Schema Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            Database Schema                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────┐      ┌─────────────────┐      ┌─────────────────┐     │
│  │  Organization   │──────│   Credential    │      │     Users       │     │
│  └────────┬────────┘      └─────────────────┘      └─────────────────┘     │
│           │                                                                  │
│           │ owns                                                             │
│           │                                                                  │
│  ┌────────▼────────┐      ┌─────────────────┐                               │
│  │ServiceCollection│──────│     Service     │                               │
│  └────────┬────────┘      └────────┬────────┘                               │
│           │                        │                                         │
│           │                        │ has                                     │
│           │                        │                                         │
│  ┌────────▼────────────────────────▼────────┐                               │
│  │              Service Access              │                               │
│  └──────────────────────────────────────────┘                               │
│                                                                              │
│  ┌─────────────────┐      ┌─────────────────┐      ┌─────────────────┐     │
│  │EndpointTemplate │──────│    Endpoint     │──────│                 │     │
│  └─────────────────┘      └────────┬────────┘      │                 │     │
│                                    │               │                 │     │
│  ┌─────────────────┐               │               │      Route      │     │
│  │ComponentTemplate│──────│    Component    │──────│                 │     │
│  └─────────────────┘      └────────┬────────┘      │                 │     │
│                                    │               │                 │     │
│  ┌─────────────────┐               │               │                 │     │
│  │  RouteTemplate  │───────────────┴───────────────│                 │     │
│  └─────────────────┘                               └─────────────────┘     │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Core Tables

### 2.1 Users

```sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    mobile_number VARCHAR(11) UNIQUE NOT NULL,  -- Iranian format: 09xxxxxxxxx
    password_hash VARCHAR(255),  -- Optional, can use OTP only
    full_name VARCHAR(255),
    is_active BOOLEAN DEFAULT true,
    is_deleted BOOLEAN DEFAULT false,  -- Soft delete
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_mobile_format CHECK (mobile_number ~ '^09[0-9]{9}$')
);

CREATE INDEX idx_users_mobile ON users(mobile_number);
CREATE INDEX idx_users_active ON users(is_active, is_deleted);

-- OTP storage for login/registration/password reset
CREATE TABLE user_otp (
    id BIGSERIAL PRIMARY KEY,
    mobile_number VARCHAR(11) NOT NULL,
    otp_code VARCHAR(6) NOT NULL,
    otp_type VARCHAR(20) NOT NULL,  -- 'LOGIN', 'REGISTER', 'RESET_PASSWORD'
    expires_at TIMESTAMP NOT NULL,
    is_used BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_otp_mobile_format CHECK (mobile_number ~ '^09[0-9]{9}$')
);

CREATE INDEX idx_user_otp_mobile ON user_otp(mobile_number);
CREATE INDEX idx_user_otp_expires ON user_otp(expires_at) WHERE is_used = false;
```

### 2.2 User Roles

```sql
CREATE TYPE user_role AS ENUM (
    'ADMIN',           -- Full access
    'SERVICE_MANAGER', -- Manage services and routes
    'CLIENT_MANAGER',  -- Manage clients and credentials
    'ACCESS_MANAGER',  -- Manage service access
    'VIEWER'           -- Read-only
);

CREATE TABLE user_role_mapping (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    role user_role NOT NULL,
    UNIQUE(user_id, role)
);

CREATE INDEX idx_user_role_user ON user_role_mapping(user_id);
```

### 2.3 Client (replaces Organization)

Client is more extensible than Organization. Uses tags for flexible attributes.

```sql
CREATE TABLE client (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL,
    display_name VARCHAR(255),
    description TEXT,
    tags JSONB,  -- {"national_id": "1234567890", "type": "government", "region": "Tehran"}
    metadata JSONB,  -- Any additional data
    parent_id BIGINT REFERENCES client(id),
    is_active BOOLEAN DEFAULT true,
    is_deleted BOOLEAN DEFAULT false,  -- Soft delete
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT REFERENCES users(id),
    updated_by BIGINT REFERENCES users(id)
);

CREATE INDEX idx_client_name ON client(name);
CREATE INDEX idx_client_tags ON client USING GIN(tags);
CREATE INDEX idx_client_parent ON client(parent_id);
CREATE INDEX idx_client_active ON client(is_active, is_deleted);
```

### 2.4 Client Credential

```sql
-- Credential types
CREATE TYPE credential_type AS ENUM (
    'IP_ADDRESS',
    'X509_CERTIFICATE', 
    'API_KEY',
    'OAUTH2',
    'BASIC_AUTH'
);

CREATE TABLE client_credential (
    id BIGSERIAL PRIMARY KEY,
    client_id BIGINT NOT NULL REFERENCES client(id),
    credential_type credential_type NOT NULL,
    credential_data TEXT NOT NULL,  -- JSON format based on type
    is_active BOOLEAN DEFAULT true,
    is_deleted BOOLEAN DEFAULT false,  -- Soft delete
    valid_from TIMESTAMP,
    valid_until TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT REFERENCES users(id)
);

CREATE INDEX idx_client_credential_client ON client_credential(client_id);
CREATE INDEX idx_client_credential_type ON client_credential(credential_type);
CREATE INDEX idx_client_credential_active ON client_credential(is_active, is_deleted);

-- Example credential_data formats:
-- IP_ADDRESS: {"addresses": ["192.168.1.1", "192.168.1.0/24"]}
-- X509_CERTIFICATE: {"certificate": "-----BEGIN CERTIFICATE-----...", "alias": "client-cert", "keystore_password": "..."}
-- API_KEY: {"key": "ak_xxx", "secret": "sk_xxx"}
-- OAUTH2: {"client_id": "xxx", "client_secret": "xxx", "token_url": "..."}
-- BASIC_AUTH: {"username": "xxx", "password_hash": "xxx"}
```

---

## 3. Service Tables

### 3.1 Service Collection

```sql
CREATE TABLE service_collection (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL,       -- e.g., 'payment'
    display_name VARCHAR(255),                -- e.g., 'سرویس پرداخت'
    description TEXT,
    
    -- Routing: all services in this collection use this base path
    base_path VARCHAR(255) UNIQUE NOT NULL,  -- e.g., '/esb/payment'
    
    -- Optional owner
    owner_client_id BIGINT REFERENCES client(id),
    
    tags JSONB,
    is_active BOOLEAN DEFAULT true,
    is_deleted BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT REFERENCES users(id),
    updated_by BIGINT REFERENCES users(id)
);

CREATE INDEX idx_service_collection_name ON service_collection(name);
CREATE INDEX idx_service_collection_base_path ON service_collection(base_path);
CREATE INDEX idx_service_collection_owner ON service_collection(owner_client_id);
CREATE INDEX idx_service_collection_tags ON service_collection USING GIN(tags);
```

### 3.2 Service

Each Service (with version) gets its own Kubernetes pod group when phase is ACTIVE.

```sql
-- Service phases
CREATE TYPE service_phase AS ENUM ('DRAFT', 'TEST', 'ACTIVE');

CREATE TABLE service (
    id BIGSERIAL PRIMARY KEY,
    collection_id BIGINT NOT NULL REFERENCES service_collection(id),
    name VARCHAR(255) NOT NULL,               -- e.g., 'PaymentService'
    version VARCHAR(20) NOT NULL,             -- e.g., '1.0', '1.1', '2.0'
    description TEXT,
    phase service_phase DEFAULT 'DRAFT',
    
    -- Kubernetes deployment info (auto-generated when ACTIVE)
    k8s_deployment_name VARCHAR(255),         -- e.g., 'payment-1-0-esb'
    k8s_service_name VARCHAR(255),            -- e.g., 'payment-1-0-esb-svc'
    k8s_hpa_name VARCHAR(255),                -- e.g., 'payment-1-0-esb-hpa'
    
    -- Scaling configuration
    min_replicas INTEGER DEFAULT 1,
    max_replicas INTEGER DEFAULT 5,
    target_cpu_percent INTEGER DEFAULT 70,
    
    -- Optional owner (inherits from collection if NULL)
    owner_client_id BIGINT REFERENCES client(id),
    
    is_deleted BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT REFERENCES users(id),
    updated_by BIGINT REFERENCES users(id),
    
    UNIQUE(collection_id, version)
);

-- Computed path: {collection.base_path}/{service.version}
-- Example: /esb/payment/1.0, /esb/payment/1.1, /esb/user/2.0

CREATE INDEX idx_service_collection ON service(collection_id);
CREATE INDEX idx_service_name ON service(name);
CREATE INDEX idx_service_phase ON service(phase);
CREATE INDEX idx_service_k8s ON service(k8s_deployment_name);
```

### 3.3 Service Access

```sql
CREATE TYPE access_status AS ENUM ('ACTIVE', 'REVOKED', 'EXPIRED');

CREATE TABLE service_access (
    id BIGSERIAL PRIMARY KEY,
    client_id BIGINT NOT NULL REFERENCES client(id),
    
    -- Can grant access to service OR service_collection
    service_id BIGINT REFERENCES service(id),
    service_collection_id BIGINT REFERENCES service_collection(id),
    
    status access_status DEFAULT 'ACTIVE',
    valid_from TIMESTAMP,
    valid_until TIMESTAMP,
    
    -- Rate limiting (per client per service)
    rate_limit_count INTEGER,          -- Number of requests allowed
    rate_limit_window_seconds INTEGER, -- Time window in seconds
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT REFERENCES users(id),
    updated_by BIGINT REFERENCES users(id),
    
    -- Either service_id or service_collection_id must be set
    CONSTRAINT chk_access_target CHECK (
        (service_id IS NOT NULL AND service_collection_id IS NULL) OR
        (service_id IS NULL AND service_collection_id IS NOT NULL)
    ),
    UNIQUE(organization_id, service_id),
    UNIQUE(organization_id, service_collection_id)
);

CREATE INDEX idx_service_access_org ON service_access(organization_id);
CREATE INDEX idx_service_access_service ON service_access(service_id);
CREATE INDEX idx_service_access_collection ON service_access(service_collection_id);
CREATE INDEX idx_service_access_status ON service_access(status);
```

---

## 4. Template Tables

### 4.1 Endpoint Template

```sql
CREATE TABLE endpoint_template (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL,
    uri_pattern TEXT NOT NULL,  -- Camel URI with placeholders: cxf:bean:{{serviceName}}?dataFormat={{dataFormat}}
    config_schema JSONB NOT NULL,  -- JSON Schema defining parameters
    description TEXT,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT REFERENCES users(id)
);

CREATE INDEX idx_endpoint_template_name ON endpoint_template(name);

-- Example config_schema:
-- {
--   "type": "object",
--   "properties": {
--     "serviceName": {"type": "string", "required": true, "description": "Service bean name"},
--     "dataFormat": {"type": "string", "enum": ["PAYLOAD", "MESSAGE"], "default": "PAYLOAD"},
--     "enableLogging": {"type": "boolean", "default": true}
--   },
--   "required": ["serviceName"]
-- }
```

### 4.2 Component Template

Components are things that can be used in Camel's `to()` or `.bean()` - NOT interceptors.
Interceptors (like WSS4J) are passed as attached files (Java/JAR) to endpoints/routes.

```sql
-- Component types (only things usable in Camel to/bean)
CREATE TYPE component_type AS ENUM (
    'PROCESSOR',    -- Camel Processor - used with .bean() in route
    'BEAN'          -- Generic bean - used with to("bean:name") or .bean()
);

CREATE TABLE component_template (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL,
    component_type component_type NOT NULL,
    component_class VARCHAR(500) NOT NULL,  -- Full Java class name
    config_schema JSONB,  -- JSON Schema defining parameters (optional)
    required_files JSONB,  -- Files this component needs: [{"name": "keystore.jks", "type": "BINARY"}]
    description TEXT,
    is_active BOOLEAN DEFAULT true,
    is_deleted BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT REFERENCES users(id)
);

CREATE INDEX idx_component_template_name ON component_template(name);
CREATE INDEX idx_component_template_type ON component_template(component_type);

-- Examples:
-- PROCESSOR: CheckAccessProcessor - used as .bean(CheckAccessProcessor.class)
-- BEAN: CustomService - used as to("bean:customService")

-- NOTE: CXF Interceptors (WSS4JInInterceptor, WSS4JOutInterceptor, etc.)
-- are NOT components. They are passed as attached files (Java source or JAR)
-- to endpoints or routes and configured via endpoint template config.
```

### 4.3 Route Template

```sql
CREATE TABLE route_template (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL,
    route_definition JSONB NOT NULL,  -- Route structure with placeholders
    config_schema JSONB NOT NULL,  -- JSON Schema for template parameters
    description TEXT,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT REFERENCES users(id)
);

CREATE INDEX idx_route_template_name ON route_template(name);

-- Example route_definition:
-- {
--   "from": "{{fromEndpoint}}",
--   "steps": [
--     {"type": "bean", "ref": "checkAccessProcessor"},
--     {"type": "choice", "when": [...], "otherwise": {...}},
--     {"type": "to", "uri": "seda:logChannel"}
--   ]
-- }
```

---

## 5. Instance Tables

### 5.1 Endpoint

```sql
CREATE TABLE endpoint (
    id BIGSERIAL PRIMARY KEY,
    template_id BIGINT REFERENCES endpoint_template(id),  -- NULL if custom
    name VARCHAR(255) NOT NULL,
    config JSONB NOT NULL,  -- Configuration based on template schema
    resolved_uri TEXT,  -- Computed Camel URI after parameter substitution
    
    -- Default rate limiting for this endpoint
    default_rate_limit_count INTEGER,
    default_rate_limit_window_seconds INTEGER,
    
    description TEXT,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT REFERENCES users(id),
    updated_by BIGINT REFERENCES users(id)
);

CREATE INDEX idx_endpoint_template ON endpoint(template_id);
CREATE INDEX idx_endpoint_name ON endpoint(name);
```

### 5.2 Component

```sql
CREATE TABLE component (
    id BIGSERIAL PRIMARY KEY,
    template_id BIGINT REFERENCES component_template(id),  -- NULL if custom
    name VARCHAR(255) NOT NULL,
    config JSONB NOT NULL,  -- Configuration based on template schema
    description TEXT,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT REFERENCES users(id)
);

CREATE INDEX idx_component_template ON component(template_id);
CREATE INDEX idx_component_name ON component(name);
```

### 5.3 Route

```sql
CREATE TABLE route (
    id BIGSERIAL PRIMARY KEY,
    template_id BIGINT REFERENCES route_template(id),  -- NULL if custom route
    service_id BIGINT REFERENCES service(id),  -- NULL for internal routes
    name VARCHAR(255) NOT NULL,
    description TEXT,
    
    -- Route definition contains the final resolved structure
    -- Example: {"from": "cxf:bean:PaymentService", "steps": [...]}
    route_definition JSONB NOT NULL,
    
    -- Generated Kamel YAML (auto-generated from route_definition)
    kamel_yaml TEXT,
    
    -- Internal routes are system routes (logging, health, etc.) not exposed to clients
    is_internal BOOLEAN DEFAULT false,
    
    is_active BOOLEAN DEFAULT true,
    is_deleted BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT REFERENCES users(id),
    updated_by BIGINT REFERENCES users(id)
);

CREATE INDEX idx_route_template ON route(template_id);
CREATE INDEX idx_route_service ON route(service_id);
CREATE INDEX idx_route_name ON route(name);
CREATE INDEX idx_route_internal ON route(is_internal);
CREATE INDEX idx_route_active ON route(is_active, is_deleted);

-- route_definition example:
-- {
--   "from": "platform-http:/api/payment",
--   "steps": [
--     {"type": "processor", "ref": "checkAccessProcessor"},
--     {"type": "processor", "ref": "prepareHeadersProcessor"},
--     {"type": "to", "uri": "cxf:bean:PaymentService"},
--     {"type": "processor", "ref": "prepareResultProcessor"},
--     {"type": "wireTap", "uri": "seda:logChannel"}
--   ]
-- }
```

### 5.4 Route Endpoints (Many-to-Many)

```sql
CREATE TABLE route_endpoint (
    id BIGSERIAL PRIMARY KEY,
    route_id BIGINT NOT NULL REFERENCES route(id) ON DELETE CASCADE,
    endpoint_id BIGINT NOT NULL REFERENCES endpoint(id),
    endpoint_role VARCHAR(50) NOT NULL,  -- 'FROM', 'TO', 'ERROR_HANDLER'
    position INTEGER NOT NULL,  -- Order in route
    UNIQUE(route_id, endpoint_id, endpoint_role)
);

CREATE INDEX idx_route_endpoint_route ON route_endpoint(route_id);
CREATE INDEX idx_route_endpoint_endpoint ON route_endpoint(endpoint_id);
```

### 5.5 Route Components (Many-to-Many)

```sql
CREATE TABLE route_component (
    id BIGSERIAL PRIMARY KEY,
    route_id BIGINT NOT NULL REFERENCES route(id) ON DELETE CASCADE,
    component_id BIGINT NOT NULL REFERENCES component(id),
    position INTEGER NOT NULL,  -- Order in route processing
    UNIQUE(route_id, component_id)
);

CREATE INDEX idx_route_component_route ON route_component(route_id);
CREATE INDEX idx_route_component_component ON route_component(component_id);
```

---

## 6. Additional Files Table

For routes that need additional files (WSDLs, Java classes, configs):

```sql
CREATE TABLE route_file (
    id BIGSERIAL PRIMARY KEY,
    route_id BIGINT NOT NULL REFERENCES route(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(50) NOT NULL,  -- 'WSDL', 'JAVA_CLASS', 'PROPERTIES', 'CONFIG'
    file_content TEXT NOT NULL,  -- For text files
    file_binary BYTEA,  -- For binary files (compiled classes)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT REFERENCES users(id)
);

CREATE INDEX idx_route_file_route ON route_file(route_id);
CREATE INDEX idx_route_file_type ON route_file(file_type);
```

---

## 7. Audit Tables (Hibernate Envers)

Hibernate Envers automatically creates audit tables for entities annotated with `@Audited`:

```sql
-- Revision info table (custom with user info)
CREATE TABLE revinfo (
    rev BIGSERIAL PRIMARY KEY,
    revtstmp BIGINT NOT NULL,
    username VARCHAR(100),
    ip_address VARCHAR(45),
    user_agent TEXT
);

-- Example audit table (auto-generated by Envers)
-- organization_AUD, service_AUD, route_AUD, etc.
CREATE TABLE organization_aud (
    id BIGINT NOT NULL,
    rev BIGINT NOT NULL REFERENCES revinfo(rev),
    revtype SMALLINT,  -- 0=INSERT, 1=UPDATE, 2=DELETE
    national_id VARCHAR(20),
    name VARCHAR(255),
    title VARCHAR(255),
    description TEXT,
    parent_id BIGINT,
    is_active BOOLEAN,
    PRIMARY KEY (id, rev)
);
```

---

## 8. Event Publishing Strategy

### Option A: Direct Kafka Publishing (Simpler)

For most cases, direct publishing is sufficient:

```java
@Transactional
public void createClient(CreateClientCommand cmd) {
    Client client = repository.save(Client.create(...));
    
    // Publish directly - if Kafka is down, transaction still succeeds
    // ESB will do full sync on startup anyway
    try {
        kafkaTemplate.send("bita.config.changes", new ClientCreated(client.getId()));
    } catch (Exception e) {
        log.warn("Failed to publish event, ESB will sync on next startup", e);
    }
}
```

**When to use:** Most scenarios where eventual consistency is acceptable.

### Option B: Outbox Pattern (Guaranteed Delivery)

If you need guaranteed event delivery:

```sql
CREATE TABLE event_outbox (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,  -- 'CLIENT', 'SERVICE', 'ROUTE'
    aggregate_id BIGINT NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP,
    is_published BOOLEAN DEFAULT false
);

CREATE INDEX idx_event_outbox_unpublished ON event_outbox(is_published) WHERE is_published = false;
```

**When to use:** Critical events where you cannot afford to lose them.

### Recommendation

Start with **Option A (Direct Publishing)** for simplicity. ESB already handles:
- Full sync on startup
- Missing events via periodic sync
- Manual full sync trigger

Add Outbox pattern later if needed for specific critical events.

---

## 9. Domain Configuration Table

For system-level configuration:

```sql
CREATE TABLE domain_config (
    id BIGSERIAL PRIMARY KEY,
    config_key VARCHAR(255) UNIQUE NOT NULL,
    config_value TEXT NOT NULL,
    description TEXT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT REFERENCES users(id)
);

-- Example configs:
-- 'esb.domain.name' = 'Tehran'
-- 'esb.default_rate_limit_count' = '1000'
-- 'esb.default_rate_limit_window' = '60'
-- 'llm.provider' = 'openai'
-- 'llm.model' = 'gpt-4'
```

---

## 10. Sample Data

### 10.1 Endpoint Templates

```sql
-- CXF WS-Security 1.1 Endpoint Template
INSERT INTO endpoint_template (name, uri_pattern, config_schema, description) VALUES (
    'cxf-wssecurity-11',
    'cxf:bean:{{serviceName}}?dataFormat={{dataFormat}}&loggingFeatureEnabled={{enableLogging}}',
    '{
        "type": "object",
        "properties": {
            "serviceName": {"type": "string", "description": "CXF bean name"},
            "wsdlLocation": {"type": "string", "description": "WSDL file path"},
            "serviceClass": {"type": "string", "description": "Service interface class"},
            "dataFormat": {"type": "string", "enum": ["PAYLOAD", "MESSAGE"], "default": "PAYLOAD"},
            "enableLogging": {"type": "boolean", "default": true},
            "signatureProperties": {"type": "string", "default": "keystore.properties"},
            "encryptionProperties": {"type": "string", "default": "truststore.properties"},
            "signatureUser": {"type": "string", "description": "Alias for signing"}
        },
        "required": ["serviceName", "wsdlLocation", "serviceClass", "signatureUser"]
    }',
    'CXF SOAP endpoint with WS-Security 1.1 (X.509 encryption and signing)'
);

-- HTTP REST Endpoint Template
INSERT INTO endpoint_template (name, uri_pattern, config_schema, description) VALUES (
    'http-rest',
    'platform-http:{{path}}?httpMethodRestrict={{methods}}',
    '{
        "type": "object",
        "properties": {
            "path": {"type": "string", "pattern": "^/[a-zA-Z0-9/_-]+$", "description": "REST path"},
            "methods": {"type": "string", "default": "GET,POST", "description": "Allowed HTTP methods"}
        },
        "required": ["path"]
    }',
    'HTTP REST endpoint using Vert.x platform-http'
);

-- ActiveMQ Queue Endpoint Template
INSERT INTO endpoint_template (name, uri_pattern, config_schema, description) VALUES (
    'activemq-queue',
    'activemq:queue:{{queueName}}?concurrentConsumers={{consumers}}&maxConcurrentConsumers={{maxConsumers}}',
    '{
        "type": "object",
        "properties": {
            "queueName": {"type": "string", "description": "Queue name"},
            "consumers": {"type": "integer", "default": 5},
            "maxConsumers": {"type": "integer", "default": 20}
        },
        "required": ["queueName"]
    }',
    'ActiveMQ queue endpoint for cross-domain communication'
);

-- SEDA Endpoint Template (for async logging)
INSERT INTO endpoint_template (name, uri_pattern, config_schema, description) VALUES (
    'seda-async',
    'seda:{{channelName}}?concurrentConsumers={{consumers}}&blockWhenFull={{blockWhenFull}}',
    '{
        "type": "object",
        "properties": {
            "channelName": {"type": "string", "description": "SEDA channel name"},
            "consumers": {"type": "integer", "default": 5},
            "blockWhenFull": {"type": "boolean", "default": false}
        },
        "required": ["channelName"]
    }',
    'SEDA endpoint for asynchronous in-memory processing'
);
```

### 10.2 Component Templates

```sql
-- WS-Security In Interceptor
INSERT INTO component_template (name, component_class, config_schema, description) VALUES (
    'wss4j-in-interceptor',
    'org.apache.cxf.ws.security.wss4j.WSS4JInInterceptor',
    '{
        "type": "object",
        "properties": {
            "action": {"type": "string", "default": "Signature Encrypt Timestamp"},
            "signaturePropFile": {"type": "string", "default": "keystore.properties"},
            "decryptionPropFile": {"type": "string", "default": "keystore.properties"},
            "passwordCallbackClass": {"type": "string", "default": "ir.iais.bita.security.PasswordCallbackHandler"}
        }
    }',
    'WSS4J inbound interceptor for WS-Security validation'
);

-- WS-Security Out Interceptor
INSERT INTO component_template (name, component_class, config_schema, description) VALUES (
    'wss4j-out-interceptor',
    'org.apache.cxf.ws.security.wss4j.WSS4JOutInterceptor',
    '{
        "type": "object",
        "properties": {
            "action": {"type": "string", "default": "Signature Encrypt Timestamp"},
            "user": {"type": "string", "description": "Signature user alias"},
            "signaturePropFile": {"type": "string", "default": "keystore.properties"},
            "encryptionPropFile": {"type": "string", "default": "truststore.properties"},
            "encryptionUser": {"type": "string", "default": "useReqSigCert"},
            "passwordCallbackClass": {"type": "string", "default": "ir.iais.bita.security.PasswordCallbackHandler"}
        },
        "required": ["user"]
    }',
    'WSS4J outbound interceptor for WS-Security signing and encryption'
);

-- Log Processor Component
INSERT INTO component_template (name, component_class, config_schema, description) VALUES (
    'timing-log-processor',
    'ir.iais.bita.processor.TimingLogProcessor',
    '{
        "type": "object",
        "properties": {
            "stepName": {"type": "string", "description": "Name of the processing step"},
            "logLevel": {"type": "string", "enum": ["DEBUG", "INFO", "WARN"], "default": "INFO"}
        },
        "required": ["stepName"]
    }',
    'Processor for logging step timing to SEDA channel'
);
```

### 10.3 Route Templates

```sql
-- WS-Security SOAP Route Template
INSERT INTO route_template (name, route_definition, config_schema, description) VALUES (
    'wssecurity-soap-route',
    '{
        "from": "{{fromEndpoint}}",
        "steps": [
            {"type": "bean", "ref": "checkAccessProcessor", "id": "checkAccess"},
            {"type": "bean", "ref": "prepareHeadersProcessor", "id": "prepareHeaders"},
            {
                "type": "choice",
                "when": [
                    {
                        "condition": "{{isLocalDomain}}",
                        "steps": [
                            {"type": "bean", "ref": "callingServiceProcessor", "id": "callService"}
                        ]
                    }
                ],
                "otherwise": {
                    "steps": [
                        {"type": "to", "uri": "{{remoteQueueEndpoint}}", "id": "toRemoteQueue"}
                    ]
                }
            },
            {"type": "bean", "ref": "prepareResultProcessor", "id": "prepareResult"},
            {"type": "wireTap", "uri": "seda:logChannel", "id": "logWireTap"}
        ]
    }',
    '{
        "type": "object",
        "properties": {
            "fromEndpoint": {"type": "string", "description": "CXF endpoint reference"},
            "isLocalDomain": {"type": "boolean", "description": "Is service in local domain"},
            "remoteQueueEndpoint": {"type": "string", "description": "ActiveMQ endpoint for remote"}
        },
        "required": ["fromEndpoint"]
    }',
    'Standard route for WS-Security SOAP services with local/remote routing'
);

-- Internal Logging Route Template
INSERT INTO route_template (name, route_definition, config_schema, description) VALUES (
    'internal-log-route',
    '{
        "from": "seda:logChannel",
        "steps": [
            {"type": "aggregate", "correlationExpression": "{{aggregationKey}}", "completionSize": 100, "completionTimeout": 5000},
            {"type": "marshal", "dataFormat": "json"},
            {"type": "to", "uri": "{{targetEndpoint}}"}
        ]
    }',
    '{
        "type": "object",
        "properties": {
            "aggregationKey": {"type": "string", "default": "${header.requestId}"},
            "targetEndpoint": {"type": "string", "description": "Target for logs (elasticsearch, kafka, etc.)"}
        },
        "required": ["targetEndpoint"]
    }',
    'Internal route for processing and forwarding logs'
);
```

---

## 11. Indexes Summary

```sql
-- Performance-critical indexes
CREATE INDEX idx_org_credential_lookup ON organization_credential(organization_id, credential_type, is_active)
    WHERE is_active = true;

CREATE INDEX idx_service_access_lookup ON service_access(organization_id, status)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_route_active ON route(is_active, service_id)
    WHERE is_active = true;

CREATE INDEX idx_endpoint_active ON endpoint(is_active)
    WHERE is_active = true;
```

---

## 12. Migration Notes

### From Legacy System

1. **Organizations**: Direct migration with national_id as key
2. **Services**: Map ServiceCollection → service_collection, Service → service
3. **Access**: Map AccessOrganizationToServiceCollection → service_access
4. **Routes**: Need to convert existing routes to new template system

### Data Migration Script Pattern

```sql
-- Example: Migrate organizations
INSERT INTO organization (national_id, name, title, is_active, created_at)
SELECT national_id, name, title, 
       CASE WHEN status = 'ACTIVE' THEN true ELSE false END,
       created_at
FROM legacy_esm.organization;

-- Example: Migrate credentials (IP addresses)
INSERT INTO organization_credential (organization_id, credential_type, credential_data, is_active)
SELECT o.id, 'IP_ADDRESS', 
       jsonb_build_object('addresses', string_to_array(legacy.ip_address, ','))::text,
       true
FROM organization o
JOIN legacy_esm.organization legacy ON o.national_id = legacy.national_id
WHERE legacy.ip_address IS NOT NULL;
```

---

**Document Version**: 1.0  
**Last Updated**: January 28, 2026
