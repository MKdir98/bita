package ir.bita.esm.route.query;

import ir.bita.esm.route.dto.*;
import ir.bita.esm.route.entity.*;
import ir.bita.esm.route.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Query service for Route domain read operations.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RouteQueryService {

    private final EndpointTemplateRepository endpointTemplateRepository;
    private final ComponentTemplateRepository componentTemplateRepository;
    private final RouteTemplateRepository routeTemplateRepository;
    private final RouteRepository routeRepository;
    private final EndpointRepository endpointRepository;
    private final ComponentRepository componentRepository;
    private final RouteFileRepository fileRepository;

    // Endpoint Template Queries
    
    public EndpointTemplateResponse getEndpointTemplate(Long id) {
        EndpointTemplate template = endpointTemplateRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new IllegalArgumentException("Endpoint template not found"));
        return mapEndpointTemplate(template);
    }

    public Page<EndpointTemplateResponse> listEndpointTemplates(Pageable pageable) {
        return endpointTemplateRepository.findByDeletedFalse(pageable).map(this::mapEndpointTemplate);
    }

    public List<EndpointTemplateResponse> listEndpointTemplatesByCategory(String category) {
        return endpointTemplateRepository.findByCategoryAndDeletedFalse(category).stream()
                .map(this::mapEndpointTemplate).collect(Collectors.toList());
    }

    // Component Template Queries
    
    public ComponentTemplateResponse getComponentTemplate(Long id) {
        ComponentTemplate template = componentTemplateRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new IllegalArgumentException("Component template not found"));
        return mapComponentTemplate(template);
    }

    public Page<ComponentTemplateResponse> listComponentTemplates(Pageable pageable) {
        return componentTemplateRepository.findByDeletedFalse(pageable).map(this::mapComponentTemplate);
    }

    // Route Template Queries
    
    public RouteTemplateResponse getRouteTemplate(Long id) {
        RouteTemplate template = routeTemplateRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new IllegalArgumentException("Route template not found"));
        return mapRouteTemplate(template);
    }

    public Page<RouteTemplateResponse> listRouteTemplates(Pageable pageable) {
        return routeTemplateRepository.findByDeletedFalse(pageable).map(this::mapRouteTemplate);
    }

    // Route Queries
    
    public RouteResponse getRoute(Long id) {
        Route route = routeRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new IllegalArgumentException("Route not found"));
        return mapRoute(route);
    }

    public Page<RouteResponse> listRoutes(Pageable pageable) {
        return routeRepository.findByDeletedFalse(pageable).map(this::mapRoute);
    }

    public List<RouteResponse> listRoutesByService(Long serviceId) {
        return routeRepository.findByServiceIdAndDeletedFalse(serviceId).stream()
                .map(this::mapRoute).collect(Collectors.toList());
    }

    // Endpoint Queries
    
    public Page<Endpoint> listEndpoints(Pageable pageable) {
        return endpointRepository.findAll(pageable);
    }

    // Component Queries
    
    public Page<Component> listComponents(Pageable pageable) {
        return componentRepository.findAll(pageable);
    }

    // Mappers
    
    private EndpointTemplateResponse mapEndpointTemplate(EndpointTemplate t) {
        return EndpointTemplateResponse.builder()
                .id(t.getId()).name(t.getName()).description(t.getDescription())
                .camelYaml(t.getCamelYaml())
                .attachmentIds(t.getAttachmentIds() != null ? new java.util.ArrayList<>(t.getAttachmentIds()) : List.of())
                .configSchema(t.getConfigSchema())
                .defaultRateLimit(t.getDefaultRateLimit()).category(t.getCategory())
                .active(t.isActive()).createdAt(t.getCreatedAt()).updatedAt(t.getUpdatedAt())
                .build();
    }

    private ComponentTemplateResponse mapComponentTemplate(ComponentTemplate t) {
        return ComponentTemplateResponse.builder()
                .id(t.getId()).name(t.getName()).description(t.getDescription())
                .componentType(t.getComponentType()).className(t.getClassName())
                .configSchema(t.getConfigSchema()).category(t.getCategory())
                .active(t.isActive()).createdAt(t.getCreatedAt()).updatedAt(t.getUpdatedAt())
                .build();
    }

    private RouteTemplateResponse mapRouteTemplate(RouteTemplate t) {
        RouteTemplateResponse.RouteTemplateResponseBuilder b = RouteTemplateResponse.builder()
                .id(t.getId()).name(t.getName()).description(t.getDescription())
                .configSchema(t.getConfigSchema()).componentConfig(t.getComponentConfig())
                .category(t.getCategory()).active(t.isActive())
                .createdAt(t.getCreatedAt()).updatedAt(t.getUpdatedAt());
        if (t.getFromEndpointTemplate() != null) {
            b.fromEndpointTemplateId(t.getFromEndpointTemplate().getId())
             .fromEndpointTemplateName(t.getFromEndpointTemplate().getName());
        }
        if (t.getToEndpointTemplate() != null) {
            b.toEndpointTemplateId(t.getToEndpointTemplate().getId())
             .toEndpointTemplateName(t.getToEndpointTemplate().getName());
        }
        return b.build();
    }

    private RouteResponse mapRoute(Route route) {
        List<Component> components = componentRepository.findByRouteIdOrderByOrderIndexAsc(route.getId());
        List<RouteFile> files = fileRepository.findByRouteId(route.getId());

        RouteResponse.RouteResponseBuilder b = RouteResponse.builder()
                .id(route.getId()).name(route.getName()).description(route.getDescription())
                .active(route.isActive()).createdAt(route.getCreatedAt()).updatedAt(route.getUpdatedAt());

        if (route.getService() != null) {
            b.serviceId(route.getService().getId())
             .serviceName(route.getService().getName())
             .serviceVersion(route.getService().getServiceVersion());
        }
        if (route.getTemplate() != null) {
            b.templateId(route.getTemplate().getId()).templateName(route.getTemplate().getName());
        }
        if (route.getFromEndpoint() != null) {
            b.fromEndpoint(mapEndpoint(route.getFromEndpoint()));
        }
        if (route.getToEndpoint() != null) {
            b.toEndpoint(mapEndpoint(route.getToEndpoint()));
        }
        b.components(components.stream().map(this::mapComponent).collect(Collectors.toList()));
        b.files(files.stream().map(this::mapFile).collect(Collectors.toList()));

        return b.build();
    }

    private RouteResponse.EndpointResponse mapEndpoint(Endpoint e) {
        return RouteResponse.EndpointResponse.builder()
                .id(e.getId()).name(e.getName()).description(e.getDescription())
                .uri(e.getUri()).defaultRateLimit(e.getDefaultRateLimit()).config(e.getConfig())
                .build();
    }

    private RouteResponse.ComponentResponse mapComponent(Component c) {
        return RouteResponse.ComponentResponse.builder()
                .id(c.getId()).name(c.getName()).description(c.getDescription())
                .componentType(c.getComponentType().name()).className(c.getClassName())
                .orderIndex(c.getOrderIndex()).config(c.getConfig())
                .build();
    }

    private RouteResponse.FileResponse mapFile(RouteFile f) {
        return RouteResponse.FileResponse.builder()
                .id(f.getId()).filename(f.getFilename()).fileType(f.getFileType())
                .fileSize(f.getFileSize()).mimeType(f.getMimeType()).createdAt(f.getCreatedAt())
                .build();
    }
}
