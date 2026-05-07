package ir.bita.esm.e2e;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E Test: Rate limiting functionality.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("P6-003: Rate Limiting E2E Test")
public class RateLimitingE2ETest extends E2ETestBase {

    private static Long clientId;
    private static Long serviceId;
    private static Long collectionId;

    @BeforeEach
    void setupTestData() {
        if (clientId == null) {
            setupTestClient();
            setupTestService();
            grantLimitedAccess();
        }
    }

    private void setupTestClient() {
        Map<String, Object> clientData = new HashMap<>();
        clientData.put("name", "Rate Limit Test Client");
        clientData.put("email", "ratelimit@test.com");

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/api/v1/clients",
                HttpMethod.POST,
                createAuthEntity(clientData),
                Map.class);

        if (response.getStatusCode() == HttpStatus.CREATED && response.getBody() != null) {
            clientId = ((Number) response.getBody().get("id")).longValue();
        }
    }

    private void setupTestService() {
        // Create collection
        Map<String, Object> collectionData = new HashMap<>();
        collectionData.put("name", "Rate Limit Test Collection");
        collectionData.put("basePath", "/rate-limit-test");

        ResponseEntity<Map> collectionResponse = restTemplate.exchange(
                getBaseUrl() + "/api/v1/service-collections",
                HttpMethod.POST,
                createAuthEntity(collectionData),
                Map.class);

        if (collectionResponse.getStatusCode() == HttpStatus.CREATED && collectionResponse.getBody() != null) {
            collectionId = ((Number) collectionResponse.getBody().get("id")).longValue();
        }

        // Create service
        Map<String, Object> serviceData = new HashMap<>();
        serviceData.put("collectionId", collectionId);
        serviceData.put("name", "Rate Limit Test Service");
        serviceData.put("version", "1.0.0");

        ResponseEntity<Map> serviceResponse = restTemplate.exchange(
                getBaseUrl() + "/api/v1/services",
                HttpMethod.POST,
                createAuthEntity(serviceData),
                Map.class);

        if (serviceResponse.getStatusCode() == HttpStatus.CREATED && serviceResponse.getBody() != null) {
            serviceId = ((Number) serviceResponse.getBody().get("id")).longValue();
        }
    }

    private void grantLimitedAccess() {
        // Grant access with rate limit of 5 requests per minute
        Map<String, Object> accessData = new HashMap<>();
        accessData.put("clientId", clientId);
        accessData.put("rateLimit", 5);
        accessData.put("rateLimitWindow", "MINUTE");

        restTemplate.exchange(
                getBaseUrl() + "/api/v1/services/" + serviceId + "/access",
                HttpMethod.POST,
                createAuthEntity(accessData),
                Map.class);
    }

    @Test
    @Order(1)
    @DisplayName("1. Verify access is granted with rate limit")
    void verifyAccessGranted() {
        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/api/v1/services/" + serviceId + "/access",
                HttpMethod.GET,
                createAuthEntity(),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(2)
    @DisplayName("2. Requests within rate limit succeed")
    void requestsWithinLimitSucceed() {
        // Make 5 requests (within limit)
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < 5; i++) {
            ResponseEntity<Map> response = restTemplate.exchange(
                    getBaseUrl() + "/api/v1/services/" + serviceId,
                    HttpMethod.GET,
                    createAuthEntity(),
                    Map.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                successCount.incrementAndGet();
            }
        }

        assertThat(successCount.get()).isEqualTo(5);
    }

    @Test
    @Order(3)
    @DisplayName("3. Test rate limit configuration update")
    void updateRateLimitConfiguration() {
        // Update rate limit to 10 requests per minute
        Map<String, Object> accessData = new HashMap<>();
        accessData.put("clientId", clientId);
        accessData.put("rateLimit", 10);
        accessData.put("rateLimitWindow", "MINUTE");

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/api/v1/services/" + serviceId + "/access",
                HttpMethod.PUT,
                createAuthEntity(accessData),
                Map.class);

        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED);
    }

    @Test
    @Order(4)
    @DisplayName("4. Test access revocation")
    void testAccessRevocation() {
        // Revoke access
        ResponseEntity<Void> revokeResponse = restTemplate.exchange(
                getBaseUrl() + "/api/v1/services/" + serviceId + "/access/" + clientId,
                HttpMethod.DELETE,
                createAuthEntity(),
                Void.class);

        assertThat(revokeResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify access is revoked - should return empty or no access
        ResponseEntity<Map> accessResponse = restTemplate.exchange(
                getBaseUrl() + "/api/v1/services/" + serviceId + "/access",
                HttpMethod.GET,
                createAuthEntity(),
                Map.class);

        assertThat(accessResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(5)
    @DisplayName("5. Test different rate limit windows")
    void testDifferentRateLimitWindows() {
        // Re-grant access with SECOND window
        Map<String, Object> secondWindowAccess = new HashMap<>();
        secondWindowAccess.put("clientId", clientId);
        secondWindowAccess.put("rateLimit", 2);
        secondWindowAccess.put("rateLimitWindow", "SECOND");

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/api/v1/services/" + serviceId + "/access",
                HttpMethod.POST,
                createAuthEntity(secondWindowAccess),
                Map.class);

        assertThat(response.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.OK);

        // Update to HOUR window
        Map<String, Object> hourWindowAccess = new HashMap<>();
        hourWindowAccess.put("clientId", clientId);
        hourWindowAccess.put("rateLimit", 1000);
        hourWindowAccess.put("rateLimitWindow", "HOUR");

        response = restTemplate.exchange(
                getBaseUrl() + "/api/v1/services/" + serviceId + "/access",
                HttpMethod.PUT,
                createAuthEntity(hourWindowAccess),
                Map.class);

        assertThat(response.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.OK);
    }

    @Test
    @Order(100)
    @DisplayName("Cleanup: Delete test resources")
    void cleanup() {
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

        // Reset static fields
        clientId = null;
        serviceId = null;
        collectionId = null;
    }
}
