package ir.bita.esm.route.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import ir.bita.esm.route.dto.RouteDefinition;
import ir.bita.esm.route.entity.*;
import ir.bita.esm.route.repository.ComponentRepository;
import ir.bita.esm.route.repository.RouteFileRepository;
import ir.bita.esm.route.repository.RouteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for building route definitions for ESB core.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RouteBuilderService {

    private final RouteRepository routeRepository;
    private final ComponentRepository componentRepository;
    private final RouteFileRepository fileRepository;
    private final TemplateResolverService templateResolver;
    private final ObjectMapper jsonMapper;

    /**
     * Builds a complete route definition for ESB core.
     */
    @Transactional(readOnly = true)
    public RouteDefinition buildRoute(Long routeId) {
        Route route = routeRepository.findByIdAndDeletedFalse(routeId)
                .orElseThrow(() -> new IllegalArgumentException("Route not found"));

        return buildRouteDefinition(route);
    }

    /**
     * Builds route definitions for all routes in a service.
     */
    @Transactional(readOnly = true)
    public List<RouteDefinition> buildRoutesForService(Long serviceId) {
        return routeRepository.findActiveRoutesByService(serviceId).stream()
                .map(this::buildRouteDefinition)
                .collect(Collectors.toList());
    }

    /**
     * Converts route definition to YAML format.
     */
    public String toYaml(RouteDefinition definition) {
        try {
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            return yamlMapper.writeValueAsString(definition);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert route to YAML", e);
        }
    }

    /**
     * Converts route definition to JSON format.
     */
    public String toJson(RouteDefinition definition) {
        try {
            return jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(definition);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert route to JSON", e);
        }
    }

    private RouteDefinition buildRouteDefinition(Route route) {
        List<Component> components = componentRepository.findByRouteIdOrderByOrderIndexAsc(route.getId());
        List<RouteFile> files = fileRepository.findByRouteId(route.getId());

        RouteDefinition.RouteDefinitionBuilder builder = RouteDefinition.builder()
                .routeId(route.getId())
                .routeName(route.getName());

        // Add service info if available
        if (route.getService() != null) {
            builder.serviceId(route.getService().getId())
                    .serviceVersion(route.getService().getServiceVersion())
                    .servicePath(route.getService().getFullPath());
        }

        // Build from endpoint
        if (route.getFromEndpoint() != null) {
            builder.fromEndpoint(buildEndpointDef(route.getFromEndpoint()));
        }

        // Build to endpoint
        if (route.getToEndpoint() != null) {
            builder.toEndpoint(buildEndpointDef(route.getToEndpoint()));
        }

        // Build components
        builder.components(components.stream()
                .map(this::buildComponentDef)
                .collect(Collectors.toList()));

        // Build files
        builder.files(files.stream()
                .map(this::buildFileDef)
                .collect(Collectors.toList()));

        return builder.build();
    }

    private RouteDefinition.EndpointDef buildEndpointDef(Endpoint endpoint) {
        return RouteDefinition.EndpointDef.builder()
                .id(endpoint.getId())
                .name(endpoint.getName())
                .uri(endpoint.getUri())
                .rateLimit(endpoint.getDefaultRateLimit())
                .config(endpoint.getConfig())
                .build();
    }

    private RouteDefinition.ComponentDef buildComponentDef(Component component) {
        return RouteDefinition.ComponentDef.builder()
                .id(component.getId())
                .name(component.getName())
                .componentType(component.getComponentType().name())
                .className(component.getClassName())
                .orderIndex(component.getOrderIndex())
                .config(component.getConfig())
                .build();
    }

    private RouteDefinition.FileDef buildFileDef(RouteFile file) {
        RouteDefinition.FileDef.FileDefBuilder builder = RouteDefinition.FileDef.builder()
                .id(file.getId())
                .filename(file.getFilename())
                .fileType(file.getFileType().name())
                .storagePath(file.getStoragePath())
                .contentHash(file.getContentHash());

        // Include inline content for small files (WSDL, XSD, Properties)
        if (file.getContent() != null) {
            builder.content(file.getContent());
        }

        return builder.build();
    }
}
