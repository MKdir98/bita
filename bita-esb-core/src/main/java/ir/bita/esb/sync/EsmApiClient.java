package ir.bita.esb.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import ir.bita.esb.cache.AccessCache;
import ir.bita.esb.cache.ClientCache;
import ir.bita.esb.route.ComponentDefinition;
import ir.bita.esb.config.EsbConfig;
import ir.bita.esb.route.RouteDefinition;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
        String url = config.getEsmBaseUrl() + "/api/internal/sync/full?serviceId=" + config.getServiceId();
        
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
     * Fetch routes for this service.
     */
    public List<RouteDefinition> fetchRoutes() throws Exception {
        String url = config.getEsmBaseUrl() + "/api/internal/sync/routes?serviceId=" + config.getServiceId();
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-API-Key", config.getEsmApiKey())
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to fetch routes: " + response.statusCode());
        }

        List<RouteSyncPayload> payloads = objectMapper.readValue(
                response.body(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, RouteSyncPayload.class)
        );
        return payloads.stream().map(this::mapRouteSyncToRouteDefinition).toList();
    }

    private RouteDefinition mapRouteSyncToRouteDefinition(RouteSyncPayload payload) {
        List<ComponentDefinition> components = new ArrayList<>();
        if (payload.getComponents() != null) {
            for (ComponentSyncPayload c : payload.getComponents()) {
                components.add(ComponentDefinition.builder()
                        .componentId(c.getId())
                        .name(c.getName())
                        .type(c.getComponentType())
                        .order(c.getOrderIndex())
                        .config(c.getConfig() != null ? c.getConfig() : Map.of())
                        .build());
            }
        }

        RouteDefinition.RouteDefinitionBuilder builder = RouteDefinition.builder()
                .routeId(payload.getId())
                .name(payload.getName())
                .active(payload.isActive())
                .components(components);

        if (payload.getFromEndpoint() != null) {
            EndpointSyncPayload from = payload.getFromEndpoint();
            if (from.getConfig() != null && !from.getConfig().isEmpty()) {
                builder.inputEndpointType(inferEndpointType(from))
                        .inputConfig(from.getConfig());
            } else {
                builder.inputUri(from.getUri());
            }
        }
        if (payload.getToEndpoint() != null) {
            EndpointSyncPayload to = payload.getToEndpoint();
            if (to.getConfig() != null && !to.getConfig().isEmpty()) {
                builder.outputEndpointType(inferEndpointType(to))
                        .outputConfig(to.getConfig());
            } else {
                builder.outputUri(to.getUri());
            }
        }
        return builder.build();
    }

    private String inferEndpointType(EndpointSyncPayload ep) {
        Map<String, Object> config = ep.getConfig();
        if (config.containsKey("wsdlUrl") && config.containsKey("serviceClass")) {
            return "CXF";
        }
        if (config.containsKey("path") && config.containsKey("method")) {
            return "REST";
        }
        if (config.containsKey("host") || config.containsKey("port")) {
            return "HTTP";
        }
        if (config.containsKey("name")) {
            return "DIRECT";
        }
        return "DIRECT";
    }

    /**
     * Fetch access rules for this service.
     */
    public List<AccessCache.AccessRule> fetchAccessRules() throws Exception {
        String url = config.getEsmBaseUrl() + "/api/internal/sync/access?serviceId=" + config.getServiceId();
        
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
                objectMapper.getTypeFactory().constructCollectionType(List.class, AccessCache.AccessRule.class));
    }

    /**
     * Fetch clients by IDs.
     */
    public List<ClientCache.ClientInfo> fetchClients(List<String> clientIds) throws Exception {
        String url = config.getEsmBaseUrl() + "/api/internal/sync/clients?ids=" + String.join(",", clientIds);
        
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
                objectMapper.getTypeFactory().constructCollectionType(List.class, ClientCache.ClientInfo.class));
    }

    /**
     * Full sync data structure.
     */
    @Data
    public static class FullSyncData {
        private List<RouteDefinition> routes;
        private List<AccessCache.AccessRule> accessRules;
        private List<ClientCache.ClientInfo> clients;
    }

    @Data
    public static class RouteSyncPayload {
        private Long id;
        private String name;
        private Long serviceId;
        private boolean active;
        private EndpointSyncPayload fromEndpoint;
        private EndpointSyncPayload toEndpoint;
        private List<ComponentSyncPayload> components;
    }

    @Data
    public static class EndpointSyncPayload {
        private Long id;
        private String name;
        private String uri;
        private Integer defaultRateLimit;
        private Map<String, Object> config;
    }

    @Data
    public static class ComponentSyncPayload {
        private Long id;
        private String name;
        private String componentType;
        private String className;
        private int orderIndex;
        private Map<String, Object> config;
    }
}
