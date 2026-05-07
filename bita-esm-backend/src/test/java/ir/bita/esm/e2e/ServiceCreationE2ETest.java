package ir.bita.esm.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E Test: Full flow from service creation to service invocation.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("P6-002: Service Creation E2E Test")
public class ServiceCreationE2ETest extends E2ETestBase {

    private static Long clientId;
    private static Long collectionId;
    private static Long serviceId;
    private static Long routeId;

    @Test
    @Order(1)
    @DisplayName("1. Create a new client")
    void createClient() {
        Map<String, Object> clientData = new HashMap<>();
        clientData.put("name", "E2E Test Client");
        clientData.put("email", "e2e@test.com");
        clientData.put("phone", "09123456789");
        
        // Tags with key-value structure
        List<Map<String, String>> tags = new ArrayList<>();
        Map<String, String> tag1 = new HashMap<>();
        tag1.put("key", "type");
        tag1.put("value", "e2e");
        tags.add(tag1);
        Map<String, String> tag2 = new HashMap<>();
        tag2.put("key", "environment");
        tag2.put("value", "test");
        tags.add(tag2);
        clientData.put("tags", tags);

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/api/v1/clients",
                HttpMethod.POST,
                createAuthEntity(clientData),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        clientId = ((Number) response.getBody().get("id")).longValue();
        assertThat(clientId).isPositive();
    }

    @Test
    @Order(2)
    @DisplayName("2. Create a service collection")
    void createServiceCollection() {
        Map<String, Object> collectionData = new HashMap<>();
        collectionData.put("name", "E2E Test Collection");
        collectionData.put("basePath", "/e2e-test");
        collectionData.put("description", "Collection for E2E testing");

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/api/v1/service-collections",
                HttpMethod.POST,
                createAuthEntity(collectionData),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        collectionId = ((Number) response.getBody().get("id")).longValue();
        assertThat(collectionId).isPositive();
    }

    @Test
    @Order(3)
    @DisplayName("3. Create a service")
    void createService() {
        Map<String, Object> serviceData = new HashMap<>();
        serviceData.put("collectionId", collectionId);
        serviceData.put("name", "E2E Test Service");
        serviceData.put("version", "1.0.0");
        serviceData.put("description", "Service for E2E testing");

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/api/v1/services",
                HttpMethod.POST,
                createAuthEntity(serviceData),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        serviceId = ((Number) response.getBody().get("id")).longValue();
        assertThat(serviceId).isPositive();
        assertThat(response.getBody().get("phase")).isEqualTo("DRAFT");
    }

    @Test
    @Order(4)
    @DisplayName("4. Create a route for the service")
    void createRoute() {
        Map<String, Object> routeData = new HashMap<>();
        routeData.put("serviceId", serviceId);
        routeData.put("name", "E2E Test Route");
        routeData.put("description", "Route for E2E testing");
        
        Map<String, Object> config = new HashMap<>();
        config.put("timeout", 30000);
        config.put("method", "POST");
        routeData.put("config", config);

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/api/v1/routes",
                HttpMethod.POST,
                createAuthEntity(routeData),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        routeId = ((Number) response.getBody().get("id")).longValue();
        assertThat(routeId).isPositive();
    }

    @Test
    @Order(5)
    @DisplayName("5. Grant access to client")
    void grantAccess() {
        Map<String, Object> accessData = new HashMap<>();
        accessData.put("clientId", clientId);
        accessData.put("rateLimit", 100);
        accessData.put("rateLimitWindow", "MINUTE");

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/api/v1/services/" + serviceId + "/access",
                HttpMethod.POST,
                createAuthEntity(accessData),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @Order(6)
    @DisplayName("6. Change service phase to TEST")
    void changePhaseToTest() {
        Map<String, Object> phaseData = new HashMap<>();
        phaseData.put("newPhase", "TEST");

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/api/v1/services/" + serviceId + "/change-phase",
                HttpMethod.POST,
                createAuthEntity(phaseData),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("phase")).isEqualTo("TEST");
    }

    @Test
    @Order(7)
    @DisplayName("7. Change service phase to ACTIVE")
    void changePhaseToActive() {
        Map<String, Object> phaseData = new HashMap<>();
        phaseData.put("newPhase", "ACTIVE");

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/api/v1/services/" + serviceId + "/change-phase",
                HttpMethod.POST,
                createAuthEntity(phaseData),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("phase")).isEqualTo("ACTIVE");
    }

    @Test
    @Order(8)
    @DisplayName("8. Verify service is deployed (has K8s names)")
    void verifyDeployment() {
        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/api/v1/services/" + serviceId,
                HttpMethod.GET,
                createAuthEntity(),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        
        // Service should have K8s deployment info after activation
        assertThat(response.getBody().get("k8sDeploymentName")).isNotNull();
        assertThat(response.getBody().get("k8sServiceName")).isNotNull();
    }

    @Test
    @Order(9)
    @DisplayName("9. View audit history")
    void viewAuditHistory() {
        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/api/v1/audit/Service/" + serviceId + "/history",
                HttpMethod.GET,
                createAuthEntity(),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    @Order(100)
    @DisplayName("Cleanup: Delete created resources")
    void cleanup() {
        // Delete route
        if (routeId != null) {
            restTemplate.exchange(
                    getBaseUrl() + "/api/v1/routes/" + routeId,
                    HttpMethod.DELETE,
                    createAuthEntity(),
                    Void.class);
        }

        // Delete service
        if (serviceId != null) {
            restTemplate.exchange(
                    getBaseUrl() + "/api/v1/services/" + serviceId,
                    HttpMethod.DELETE,
                    createAuthEntity(),
                    Void.class);
        }

        // Delete collection
        if (collectionId != null) {
            restTemplate.exchange(
                    getBaseUrl() + "/api/v1/service-collections/" + collectionId,
                    HttpMethod.DELETE,
                    createAuthEntity(),
                    Void.class);
        }

        // Delete client
        if (clientId != null) {
            restTemplate.exchange(
                    getBaseUrl() + "/api/v1/clients/" + clientId,
                    HttpMethod.DELETE,
                    createAuthEntity(),
                    Void.class);
        }
    }
}
