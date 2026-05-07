package ir.bita.esb.route;

import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;

import java.util.Map;

/**
 * Factory for creating specific Camel route types.
 */
@Slf4j
public class CamelRouteFactory {

    /**
     * Create a SOAP/CXF route with WS-Security.
     */
    public static RouteBuilder createSoapRoute(CamelContext context, RouteDefinition routeDef, String routeId) {
        return new RouteBuilder() {
            @Override
            public void configure() {
                Map<String, Object> inputConfig = routeDef.getInputConfig();
                
                String wsdlUrl = (String) inputConfig.getOrDefault("wsdlUrl", "");
                String servicePath = (String) inputConfig.getOrDefault("servicePath", "/service");
                String serviceClass = (String) inputConfig.getOrDefault("serviceClass", "");
                
                // Input CXF endpoint
                String inputUri = String.format(
                    "cxf:%s?wsdlURL=%s&serviceClass=%s&dataFormat=PAYLOAD",
                    servicePath, wsdlUrl, serviceClass
                );

                // Build route
                from(inputUri)
                        .routeId(routeId)
                        .log("Received SOAP request: ${body}")
                        .process(exchange -> {
                            // Pre-processing if needed
                        })
                        .to(buildOutputUri(routeDef))
                        .log("SOAP response: ${body}");
            }
        };
    }

    /**
     * Create a REST route.
     */
    public static RouteBuilder createRestRoute(CamelContext context, RouteDefinition routeDef, String routeId) {
        return new RouteBuilder() {
            @Override
            public void configure() {
                Map<String, Object> inputConfig = routeDef.getInputConfig();
                
                String method = (String) inputConfig.getOrDefault("method", "POST");
                String path = (String) inputConfig.getOrDefault("path", "/");

                // REST endpoint
                rest(path)
                        .id(routeId)
                        .produces("application/json")
                        .consumes("application/json")
                        .to("direct:" + routeId + "-process");

                // Processing route
                from("direct:" + routeId + "-process")
                        .routeId(routeId + "-process")
                        .log("REST request received: ${body}")
                        .to(buildOutputUri(routeDef))
                        .log("REST response: ${body}");
            }
        };
    }

    /**
     * Create a passthrough route.
     */
    public static RouteBuilder createPassthroughRoute(CamelContext context, RouteDefinition routeDef, String routeId) {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:" + routeId)
                        .routeId(routeId)
                        .to(buildOutputUri(routeDef));
            }
        };
    }

    private static String buildOutputUri(RouteDefinition routeDef) {
        if (routeDef.getOutputUri() != null) {
            return routeDef.getOutputUri();
        }

        Map<String, Object> outputConfig = routeDef.getOutputConfig();
        if (outputConfig == null || outputConfig.isEmpty()) {
            return "log:output";
        }

        String type = routeDef.getOutputEndpointType();
        
        return switch (type != null ? type.toUpperCase() : "") {
            case "HTTP" -> {
                String host = (String) outputConfig.getOrDefault("host", "localhost");
                int port = (int) outputConfig.getOrDefault("port", 80);
                String path = (String) outputConfig.getOrDefault("path", "/");
                yield String.format("http://%s:%d%s", host, port, path);
            }
            case "CXF" -> {
                String address = (String) outputConfig.getOrDefault("address", "/");
                String wsdl = (String) outputConfig.getOrDefault("wsdlUrl", "");
                yield String.format("cxf:%s?wsdlURL=%s&dataFormat=PAYLOAD", address, wsdl);
            }
            default -> "log:output";
        };
    }
}
