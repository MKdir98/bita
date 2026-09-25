# Groovy Template Architecture — Implementation Plan

## Context

BITA ESB is a gateway platform. ESM backend manages service definitions. ESB pods
run in Kubernetes, one pod per service, and proxy traffic to backend providers.

This document captures all architectural decisions made in a design session and their
implementation status. An implementation agent should pick up from here.

---

## Architecture Decisions (all finalized)

### 1. Routing Model — Groovy-per-Service

**Decision:** Drop DB-driven Route/Endpoint/Component entity hierarchy. Replace with one
Groovy script per service stored in DB.

- Each service has one `GroovyTemplate` selection + filled variable values.
- The Groovy script handles all routing for that service (can expose multiple ports).
- Simple or complex routes (REST proxy, SOAP/WS-Security) all live in Groovy.

**Rationale:** DB-driven `DynamicRouteBuilder` cannot handle complex cases (WS-Security,
custom processors, WSDL transformation). Groovy is already proven in tests.

---

### 2. GroovyTemplate Entity

**Decision:** New entity in ESM backend.

Fields:
- `name` — unique, human-readable
- `description`
- `scriptText` — the Groovy script body (with `${varName}` placeholders)
- Auto-scanned variables: ESM scans `${varName}` patterns from `scriptText`

Variable metadata (per variable, filled by template author or user at service creation):
- `name` — matches `${name}` in script
- `label` — human-readable label for UI form
- `description` — optional
- `type` — `STRING`, `SECRET`, `INT`, `BOOLEAN`, `PORT`
- `required` — boolean
- `defaultValue` — optional

`PORT` type is special: also requires a `path` field (K8s Ingress path for that port).

---

### 3. ComponentTemplate Entity

**Decision:** New entity in ESM backend.

Fields:
- `name` — **unique slug** (e.g. `elastic-logger`, `ws-security-client`)
- `description`
- `groovyCode` — Groovy class implementing fixed interface (see below)
- Auto-scanned variables from `groovyCode`

**Component interface contract:**
```groovy
class MyComponent {
    void configure(Map params) { /* apply params */ }
    // any other methods the Groovy route script calls
}
```

ESB calls `configure(params)` with the filled variable map, then registers the instance
in the Camel registry under the component's unique name.

**Referencing in GroovyTemplate:** use `#componentName` (e.g. `#elastic-logger`).
Typing `#` in the template editor shows autocomplete of existing ComponentTemplate names.

---

### 4. Variable Substitution Strategy

**Decision:** ALL variables injected as Groovy runtime bindings by ESB — NOT string-substituted.

- ESM stores variable values as a `Map<String, Object>` alongside the assembled script.
- ESB fetches both and evaluates the script with `new Binding(variableMap)`.
- This avoids injection vulnerabilities from string interpolation into code.
- `accessService` is also a runtime binding (injected by ESB, not from DB).

Standard ESB-injected bindings (always present, not user-configured):
- `accessService` — `RouteAccessService` instance (keystores, access control, rate limit)
- `vertxInstance` — `io.vertx.core.Vertx` instance

User-configured bindings come from filled `${varName}` variable values.

---

### 5. Script Assembly (ESM side)

**Decision:** ESM assembles the final Groovy script. ESB receives one ready-to-evaluate blob.

Assembly steps (done by ESM at service creation or phase change):
1. For each `#componentName` found in `GroovyTemplate.scriptText`:
   - Fetch the matching `ComponentTemplate.groovyCode`
   - Inline the component class definition at the top of the script
2. Keep the GroovyTemplate script body as-is (no `${var}` substitution — that's runtime)
3. Store assembled script text in `ServiceGroovyConfig.assembledScript`

Result structure stored in DB:
```groovy
// ── inlined from ComponentTemplate: elastic-logger ──
class ElasticLogger {
    void configure(Map params) { ... }
    ...
}

// ── route from GroovyTemplate ──
new RouteBuilder() {
    void configure() {
        def logger = new ElasticLogger()
        logger.configure(params['elastic-logger'])
        getContext().registry.bind("elastic-logger", logger)

        from("...")
            .process("#elastic-logger")
            ...
    }
}
```

---

### 6. Port Mapping → Kubernetes

**Decision:** Variables of type `PORT` are mapped to K8s resources automatically.

For each `PORT` variable in a service:
- `K8sDeploymentBuilder` adds a `containerPort` entry
- `K8sServiceBuilder` exposes the port in the K8s Service
- K8s Ingress gets one rule: `path → service:port`

Port 8080 is **reserved** for Vert.x health/metrics. Never used in templates.

---

### 7. ESB Startup Sequence (updated)

```
1. EsbCoreApplication reads env vars → EsbConfig (SERVICE_ID, ESM_BASE_URL, ESM_API_KEY)
2. Vert.x starts on port 8080 (health: /health/live, /health/ready; metrics: /metrics)
3. SyncService calls ESM: GET /internal/v1/sync/services/{serviceId}/config
4. ESM returns:
     - assembledScript  (String)
     - variableValues   (Map<String, Object>)
5. ESB builds Groovy Binding: variableValues + accessService + vertxInstance
6. GroovyShell evaluates assembledScript with Binding
7. Returned RouteBuilder added to DefaultCamelContext
8. Camel starts → CXF/Vert.x route listeners bind to their ports
9. /health/ready returns 200 → K8s routes traffic
```

---

### 8. Hot-Reload (push from ESM)

**Decision:** ESM pushes reload signal to ESB pod when script or variable values change.

- ESB pod exposes: `POST /internal/reload`
- ESM calls this endpoint on:
  - Template variable value change
  - Component template code update
  - Phase change that doesn't require pod restart
- ESB on reload:
  1. Stop existing Camel routes
  2. Re-fetch assembled script + variable values from ESM
  3. Re-evaluate Groovy with new Binding
  4. Re-add routes to Camel context
  5. Start routes

---

### 9. HTTP I/O Strategy (current)

**Decision:** Option A — blocking thread pool. Vert.x owns port 8080 only (health/metrics).
CXF/Camel own their service ports with their own thread pools.

Vert.x option C (full non-blocking, Vert.x owns all I/O) is deferred — proof-of-concept
test already written: `RestToRestVertxProxyIntegrationTest`.

---

## Implementation Status

### ESM Backend (`bita-esm-backend`)

| Task | Status | Notes |
|------|--------|-------|
| Create `GroovyTemplate` JPA entity | ✅ Done | `route/entity/GroovyTemplate.java` |
| Create `ComponentTemplate` JPA entity | ✅ Done | `route/entity/ComponentTemplate.java` — `groovyCode` field added |
| Create `ServiceGroovyConfig` JPA entity | ✅ Done | `route/entity/ServiceGroovyConfig.java` |
| `GroovyTemplateRepository` | ✅ Done | |
| `ComponentTemplateRepository` | ✅ Done | |
| `ServiceGroovyConfigRepository` | ✅ Done | |
| `GroovyTemplateController` (CRUD) | ✅ Done | `/api/v1/groovy-templates` |
| `ComponentTemplateController` (CRUD) | ✅ Done | `/api/v1/component-templates` |
| `ServiceGroovyConfigController` | ✅ Done | `/api/v1/services/{id}/groovy-config` |
| Script assembly logic | ✅ Done | `ScriptAssemblyService.assemble()` — scans `#name`, inlines groovyCode |
| Variable scan logic | ✅ Done | `ScriptAssemblyService.scanVariables()` — scans `${varName}` |
| `InternalSyncController.getServiceConfig()` | ✅ Done | Returns `assembledScript` + `variableValues` |
| `SyncDataService.getServiceConfig()` | ✅ Done | Reads from `ServiceGroovyConfig` |
| Drop old Route/Endpoint/Component entities | ✅ Done | Never existed; old-model files already gone |
| `K8sDeploymentBuilder` PORT variable support | ✅ Done | Adds `containerPort` per PORT var |
| `K8sServiceBuilder` PORT variable support | ✅ Done | Adds Service port per PORT var |
| Ingress PORT → path rules | ✅ Done | `KubernetesDeployerService.addIngressPath()` |
| `ServiceConfigDto` — remove stale old-model fields | ✅ Done | Removed `groovyScript`, `providerAddress`, `providerAlias`, `providerCertPem`, `wsdlUri` |

### ESB Core (`bita-esb-core`)

| Task | Status | Notes |
|------|--------|-------|
| `SyncService.fullSync()` — fetch + evaluate assembled script | ✅ Done | Calls `fetchFullSyncData()` + `fetchServiceConfig()` |
| `SyncService` — evaluate Groovy with Binding | ✅ Done | `evaluateAndRegisterRoutes()` via `GroovyShell` |
| `SyncService` — add RouteBuilder to Camel | ✅ Done | `camelContext.addRoutes(rb)` |
| Hot-reload endpoint `POST /internal/reload` | ✅ Done | `HttpServerVerticle` router |
| `DefaultRouteAccessService` wire-up | ✅ Done | Backed by `AccessCache` + `ClientCache` |
| `EsmApiClient.fetchServiceConfig()` | ✅ Done | Calls `/internal/v1/sync/services/{id}/config` |
| Fix `EsmApiClient` URL mismatches | ✅ Done | All 3 endpoints now use `/internal/v1/sync/*` |
| Fix `EsmApiClient.FullSyncData` type mismatch | ✅ Done | `ClientData`/`AccessData` mirror ESM DTOs; `SyncService` maps to cache types |
| Drop `DynamicRouteBuilder` | ✅ Done | Never existed in this codebase |

### Tests

| Task | Status | Notes |
|------|--------|-------|
| `EchoWsSecurityIntegrationTest` (SOAP/WS-Security) | ✅ Done | 3 tests passing |
| `RestToRestVertxProxyIntegrationTest` (REST/Vert.x) | ✅ Done | 4 tests, proof-of-concept for Vert.x option C |
| Integration test for GroovyTemplate assembly | ❌ TODO | Test ESM assembly + ESB evaluation end-to-end |

---

## Files to Delete

```
bita-esm-backend/.../route/entity/Route.java
bita-esm-backend/.../route/entity/Endpoint.java
bita-esm-backend/.../route/entity/Component.java
bita-esm-backend/.../route/entity/RouteTemplate.java
bita-esm-backend/.../route/entity/EndpointTemplate.java
bita-esm-backend/.../route/handler/CreateRouteHandler.java
bita-esm-backend/.../route/handler/UpdateRouteHandler.java
bita-esm-backend/.../route/handler/DeleteRouteHandler.java
bita-esm-backend/.../route/controller/RouteController.java
bita-esm-backend/.../route/controller/RouteTemplateController.java
bita-esm-backend/.../route/controller/EndpointTemplateController.java
bita-esb-core/.../route/DynamicRouteBuilder.java
bita-esb-core/.../route/RouteDefinition.java
bita-esb-core/.../route/ComponentDefinition.java
```

Note: `bita-esm-backend/.../route/entity/ComponentTemplate.java` may already exist —
repurpose it rather than delete (rename/rework to match new schema).

---

## Key Interfaces & Patterns

### `RouteAccessService` (already exists, do not change)
```java
// bita-esb-core/src/main/java/ir/bita/esb/access/RouteAccessService.java
String getBitaKeyStore();
String getBitaPassword();
String getClientTrustStore();
String getProviderTrustStore();
String getProviderAlias();
String getGatewayAddress();
boolean hasAccess(String clientId);
boolean checkRateLimit(String clientId);
String getClientIdByApiKey(String key);
String getClientIdByIp(String ip);
```

### ESM Internal API — endpoint to update
```
GET /internal/v1/sync/services/{serviceId}/config
Response:
{
  "assembledScript": "class ElasticLogger { ... }\nnew RouteBuilder() { ... }",
  "variableValues": {
    "pvAddress": "http://provider:8080/service",
    "gwPort": 8081,
    "pvWsdlUri": "http://provider:8080/service?wsdl"
  }
}
```

### Groovy evaluation pattern (ESB — how SyncService should work)
```java
Binding binding = new Binding(variableValues);
binding.setVariable("accessService", accessService);
binding.setVariable("vertxInstance", vertx);
RouteBuilder rb = (RouteBuilder) new GroovyShell(
    Thread.currentThread().getContextClassLoader(), binding
).evaluate(assembledScript);
camelContext.addRoutes(rb);
```

### Existing proof-of-concept tests to reference
- `EchoWsSecurityIntegrationTest` — SOAP proxy pattern with WS-Security
- `RestToRestVertxProxyIntegrationTest` — REST proxy pattern, pure Vert.x
- `echo-gw-route.groovy` — SOAP/WS-Security Groovy template example
- `rest-proxy-gw-route.groovy` — REST/Vert.x Groovy template example
