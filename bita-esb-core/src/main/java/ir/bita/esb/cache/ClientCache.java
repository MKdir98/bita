package ir.bita.esb.cache;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache for clients with access to this Service.
 */
@Slf4j
public class ClientCache {

    private final Map<String, ClientInfo> clientById = new ConcurrentHashMap<>();
    private final Map<String, String> apiKeyToClientId = new ConcurrentHashMap<>();
    private final Map<String, String> ipToClientId = new ConcurrentHashMap<>();

    @Data
    @Builder
    public static class ClientInfo {
        private String clientId;
        private String name;
        private List<Credential> credentials;
    }

    @Data
    @Builder
    public static class Credential {
        private String type; // IP_ADDRESS, API_KEY, X509_CERTIFICATE
        private String value;
        private boolean active;
    }

    /**
     * Add or update a client.
     */
    public void putClient(ClientInfo client) {
        clientById.put(client.getClientId(), client);

        // Index credentials
        if (client.getCredentials() != null) {
            for (Credential cred : client.getCredentials()) {
                if (!cred.isActive()) continue;
                
                switch (cred.getType()) {
                    case "API_KEY" -> apiKeyToClientId.put(cred.getValue(), client.getClientId());
                    case "IP_ADDRESS" -> ipToClientId.put(cred.getValue(), client.getClientId());
                }
            }
        }
        
        log.debug("Client cached: {} ({})", client.getClientId(), client.getName());
    }

    /**
     * Remove a client.
     */
    public void removeClient(String clientId) {
        ClientInfo client = clientById.remove(clientId);
        
        if (client != null && client.getCredentials() != null) {
            for (Credential cred : client.getCredentials()) {
                switch (cred.getType()) {
                    case "API_KEY" -> apiKeyToClientId.remove(cred.getValue());
                    case "IP_ADDRESS" -> ipToClientId.remove(cred.getValue());
                }
            }
        }
        
        log.debug("Client removed from cache: {}", clientId);
    }

    /**
     * Get client by ID.
     */
    public ClientInfo getClient(String clientId) {
        return clientById.get(clientId);
    }

    /**
     * Get client ID by API key.
     */
    public String getClientIdByApiKey(String apiKey) {
        return apiKeyToClientId.get(apiKey);
    }

    /**
     * Get client ID by IP address.
     */
    public String getClientIdByIp(String ip) {
        return ipToClientId.get(ip);
    }

    /**
     * Clear all cached clients.
     */
    public void clear() {
        clientById.clear();
        apiKeyToClientId.clear();
        ipToClientId.clear();
        log.info("Client cache cleared");
    }

    public int size() {
        return clientById.size();
    }
}
