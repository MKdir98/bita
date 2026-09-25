package ir.bita.esb.sync;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import ir.bita.esb.config.EsbConfig;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * REST client for ESM Backend internal sync API.
 */
@Slf4j
public class EsmApiClient {

    private final EsbConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public EsmApiClient(EsbConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
    }

    /**
     * Fetch all data for this service (full sync).
     */
    public FullSyncData fetchFullSyncData() throws Exception {
        String url = config.getEsmBaseUrl() + "/internal/v1/sync/full";
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-API-Key", config.getEsmApiKey())
                .header("Accept", "application/json")
                .GET()
                .build();

        log.info("Fetching full sync data from ESM for service {}", config.getServiceId());
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to fetch sync data: " + response.statusCode() + " - " + response.body());
        }

        return objectMapper.readValue(response.body(), FullSyncData.class);
    }

    /**
     * Fetch access rules for this service.
     */
    public List<AccessData> fetchAccessRules() throws Exception {
        String url = config.getEsmBaseUrl() + "/internal/v1/sync/access";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-API-Key", config.getEsmApiKey())
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to fetch access rules: " + response.statusCode());
        }

        return objectMapper.readValue(response.body(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, AccessData.class));
    }

    /**
     * Fetch all clients.
     */
    public List<ClientData> fetchClients() throws Exception {
        String url = config.getEsmBaseUrl() + "/internal/v1/sync/clients";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-API-Key", config.getEsmApiKey())
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to fetch clients: " + response.statusCode());
        }

        return objectMapper.readValue(response.body(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, ClientData.class));
    }

    /**
     * Fetches the full self-configuration for this ESB pod from ESM.
     * Called once at startup to construct {@link ir.bita.esb.access.DefaultRouteAccessService}.
     */
    public ServiceConfigPayload fetchServiceConfig() throws Exception {
        String url = config.getEsmBaseUrl() + "/internal/v1/sync/services/" + config.getServiceId() + "/config";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-API-Key", config.getEsmApiKey())
                .header("Accept", "application/json")
                .GET()
                .build();

        log.info("Fetching service config from ESM for service {}", config.getServiceId());

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to fetch service config: HTTP " + response.statusCode() + " – " + response.body());
        }

        return objectMapper.readValue(response.body(), ServiceConfigPayload.class);
    }

    @Data
    public static class ServiceConfigPayload {
        private Long serviceId;
        private String name;
        /** Assembled Groovy script with component code inlined — evaluated via GroovyShell. */
        private String assembledScript;
        /** Runtime variable values injected as Groovy bindings. */
        private java.util.Map<String, Object> variableValues;
        private java.util.List<String> authorizedClientCertPems;
    }

    /** Mirrors ESM's FullSyncDataDto — keep field names in sync with the ESM response JSON. */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FullSyncData {
        private List<ClientData> clients;
        private List<AccessData> accessRules;
    }

    /** Mirrors ESM's ClientSyncDto. */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ClientData {
        private Long id;
        private String name;
        private boolean active;
        private List<CredentialData> credentials;
    }

    /** Mirrors ESM's ClientSyncDto.CredentialSyncDto. */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CredentialData {
        /** Maps to CredentialType enum name (e.g. API_KEY, X509_CERTIFICATE, IP_ADDRESS). */
        private String credentialType;
        private String credentialValue;
        private boolean active;
    }

    /** Mirrors ESM's AccessSyncDto. */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AccessData {
        private Long clientId;
        private Long serviceId;
        private Integer customRateLimit;
        private java.time.LocalDateTime validUntil;
        private boolean currentlyValid;
    }
}
