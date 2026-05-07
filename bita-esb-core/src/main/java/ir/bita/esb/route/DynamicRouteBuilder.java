package ir.bita.esb.route;

import ir.bita.esb.cxf.WsSecurityCxfConfigurerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.component.cxf.CxfEndpointConfigurer;
import org.apache.camel.builder.RouteBuilder;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Builds Camel routes dynamically from RouteDefinition.
 */
@Slf4j
@RequiredArgsConstructor
public class DynamicRouteBuilder {

    private final CamelContext camelContext;

    /**
     * Build a RouteBuilder from RouteDefinition.
     */
    public RouteBuilder buildRoute(RouteDefinition routeDef, String routeId) {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Build input URI
                String inputUri = buildInputUri(routeDef);
                String outputUri = buildOutputUri(routeDef);

                log.debug("Building route: {} -> {}", inputUri, outputUri);

                // Start route definition
                org.apache.camel.model.ProcessorDefinition<?> route = from(inputUri)
                        .routeId(routeId)
                        .setProperty("routeId", constant(routeDef.getRouteId()))
                        .setProperty("routeName", constant(routeDef.getName()));

                // Add components in order (Filter requires children, so we pass outputUri)
                List<ComponentDefinition> sorted = routeDef.getComponents() != null
                        ? routeDef.getComponents().stream()
                                .sorted(Comparator.comparingInt(ComponentDefinition::getOrder))
                                .toList()
                        : List.of();
                route = addComponents(route, sorted, 0, outputUri);
                if (route != null) {
                    route.to(outputUri);
                }

                // Also create a direct route for programmatic invocation
                from("direct:" + routeId)
                        .routeId(routeId + "-direct")
                        .to(inputUri);
            }
        };
    }

    private String buildInputUri(RouteDefinition routeDef) {
        if (routeDef.getInputUri() != null && !routeDef.getInputUri().isBlank()) {
            return routeDef.getInputUri();
        }

        String type = routeDef.getInputEndpointType();
        Map<String, Object> config = routeDef.getInputConfig();

        if (type == null || type.isEmpty()) {
            // Default to direct endpoint
            return "direct:input-" + routeDef.getRouteId();
        }

        return switch (type.toUpperCase()) {
            case "CXF" -> buildCxfUri(routeDef, config, true);
            case "HTTP" -> buildHttpUri(config);
            case "REST" -> buildRestUri(config);
            case "DIRECT" -> "direct:" + config.getOrDefault("name", "input-" + routeDef.getRouteId());
            default -> routeDef.getInputUri() != null ? routeDef.getInputUri() : "direct:input-" + routeDef.getRouteId();
        };
    }

    private String buildOutputUri(RouteDefinition routeDef) {
        if (routeDef.getOutputUri() != null && !routeDef.getOutputUri().isBlank()) {
            return routeDef.getOutputUri();
        }

        String type = routeDef.getOutputEndpointType();
        Map<String, Object> config = routeDef.getOutputConfig();

        if (type == null || type.isEmpty()) {
            return "direct:output-" + routeDef.getRouteId();
        }

        return switch (type.toUpperCase()) {
            case "CXF" -> buildCxfUri(routeDef, config, false);
            case "HTTP" -> buildHttpUri(config);
            case "REST" -> buildRestUri(config);
            case "LOG" -> "log:" + config.getOrDefault("loggerName", routeDef.getName());
            case "DIRECT" -> "direct:" + config.getOrDefault("name", "output-" + routeDef.getRouteId());
            default -> routeDef.getOutputUri() != null ? routeDef.getOutputUri() : "direct:output-" + routeDef.getRouteId();
        };
    }

    private String buildCxfUri(RouteDefinition routeDef, Map<String, Object> config, boolean isInput) {
        StringBuilder uri = new StringBuilder("cxf:");
        uri.append(config.getOrDefault("address", "/service"));

        uri.append("?wsdlURL=").append(config.getOrDefault("wsdlUrl", ""));
        uri.append("&serviceClass=").append(config.getOrDefault("serviceClass", ""));
        uri.append("&dataFormat=").append(config.getOrDefault("dataFormat", "PAYLOAD"));

        @SuppressWarnings("unchecked")
        Map<String, Object> wsSecurity = config != null ? (Map<String, Object>) config.get("wsSecurity") : null;
        if (wsSecurity != null && !wsSecurity.isEmpty()) {
            String beanId = "cxfConfigurer-" + routeDef.getRouteId() + "-" + (isInput ? "in" : "out");
            CxfEndpointConfigurer configurer = WsSecurityCxfConfigurerFactory.create(wsSecurity);
            camelContext.getRegistry().bind(beanId, configurer);
            uri.append("&cxfConfigurer=#").append(beanId);
        }

        return uri.toString();
    }

    private String buildHttpUri(Map<String, Object> config) {
        StringBuilder uri = new StringBuilder("http://");
        uri.append(config.getOrDefault("host", "localhost"));
        uri.append(":").append(config.getOrDefault("port", 80));
        uri.append(config.getOrDefault("path", "/"));
        
        return uri.toString();
    }

    private String buildRestUri(Map<String, Object> config) {
        return "rest:" + config.getOrDefault("method", "POST") + ":" + 
               config.getOrDefault("path", "/");
    }

    /**
     * Add components to route. For FILTER, adds remaining components + output as filter's child.
     * @return null if output was consumed by a filter, otherwise the route for adding output
     */
    @SuppressWarnings("unchecked")
    private org.apache.camel.model.ProcessorDefinition<?> addComponents(
            org.apache.camel.model.ProcessorDefinition<?> route,
            List<ComponentDefinition> components,
            int index,
            String outputUri) {
        if (index >= components.size()) {
            return route;
        }
        ComponentDefinition comp = components.get(index);
        String type = comp.getType();
        Map<String, Object> config = comp.getConfig();

        log.debug("Adding component: {} (type: {})", comp.getName(), type);

        if ("FILTER".equals(type.toUpperCase())) {
            String predicate = (String) config.get("predicate");
            if (predicate != null) {
                // Filter requires children: add remaining components + output
                var filterDef = route.filter().simple(predicate);
                var filterRoute = addComponents(filterDef, components, index + 1, outputUri);
                if (filterRoute != null) {
                    filterRoute.to(outputUri);
                }
                return null; // output already added
            }
        } else {
            var next = addComponentAndReturnNext(route, comp);
            return addComponents(next, components, index + 1, outputUri);
        }
        return addComponents(route, components, index + 1, outputUri);
    }

    @SuppressWarnings("unchecked")
    private org.apache.camel.model.ProcessorDefinition<?> addComponentAndReturnNext(
            org.apache.camel.model.ProcessorDefinition<?> route, ComponentDefinition comp) {
        String type = comp.getType();
        Map<String, Object> config = comp.getConfig();

        log.debug("Adding component: {} (type: {})", comp.getName(), type);

        return switch (type.toUpperCase()) {
            case "TRANSFORM" -> {
                String expression = (String) config.get("expression");
                String language = (String) config.getOrDefault("language", "simple");
                if (expression != null) {
                    yield route.transform().language(language, expression);
                }
                yield route;
            }
            case "VALIDATE" -> {
                String schema = (String) config.get("schema");
                if (schema != null) {
                    yield route.to("validator:" + schema);
                }
                yield route;
            }
            case "LOG" -> {
                String message = (String) config.getOrDefault("message", "${body}");
                yield route.log(message);
            }
            case "ENRICH" -> {
                String uri = (String) config.get("uri");
                if (uri != null) {
                    yield route.enrich(uri);
                }
                yield route;
            }
            case "FILTER" -> route; // Handled in addComponents
            case "SPLIT" -> {
                String expression = (String) config.get("expression");
                if (expression != null) {
                    yield route.split().tokenize(expression);
                }
                yield route;
            }
            case "AGGREGATE" -> {
                log.warn("Aggregate component not fully implemented");
                yield route;
            }
            case "SEDA" -> {
                String sedaUri = "seda:" + config.getOrDefault("name", "default");
                yield route.to(sedaUri);
            }
            default -> {
                log.warn("Unknown component type: {}", type);
                yield route;
            }
        };
    }
}
