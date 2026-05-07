package ir.bita.esm.route.service;

import org.springframework.stereotype.Service;

/**
 * Policy service for LLM-friendly route/endpoint design decisions.
 */
@Service
public class RouteDesignPolicyService {

    /**
     * Resolves host for internal cluster destinations.
     * Priority:
     * 1) Existing k8s service name from service metadata
     * 2) Derived DNS name from collection + version
     */
    public String resolveInternalHost(String collectionName, String serviceVersion, String k8sServiceName) {
        if (k8sServiceName != null && !k8sServiceName.isBlank()) {
            return k8sServiceName;
        }

        if (collectionName == null || collectionName.isBlank() || serviceVersion == null || serviceVersion.isBlank()) {
            throw new IllegalArgumentException("collectionName and serviceVersion are required when k8sServiceName is not provided");
        }

        String collection = collectionName.toLowerCase().replaceAll("[^a-z0-9]", "-");
        String version = serviceVersion.replace(".", "-");
        return collection + "-" + version + "-esb-svc";
    }

    /**
     * Lightweight validation to ensure route URIs are compatible with supported Camel-style schemes.
     */
    public boolean isCamelStyleUriValid(String uri) {
        if (uri == null || uri.isBlank()) {
            return false;
        }

        // unresolved placeholders should not reach runtime endpoint instance
        if (uri.contains("{{") || uri.contains("}}")) {
            return false;
        }

        return uri.startsWith("platform-http:/")
                || uri.startsWith("http://")
                || uri.startsWith("https://")
                || uri.startsWith("cxf:")
                || uri.startsWith("direct:")
                || uri.startsWith("seda:");
    }
}
