package ir.bita.esb.route;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CamelEngineRouteStartupTest {

    private CamelContext camelContext;
    private ProducerTemplate producerTemplate;

    @BeforeEach
    void setUp() throws Exception {
        camelContext = new DefaultCamelContext();
        producerTemplate = camelContext.createProducerTemplate();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (producerTemplate != null) {
            producerTemplate.stop();
        }
        if (camelContext != null) {
            camelContext.stop();
        }
    }

    @Test
    void shouldStartCamelEngineAndInvokeRouteWithEndpointsAndComponents() throws Exception {
        // Input is shaped like ESM internal sync payload (/internal/v1/sync/routes).
        String esmSyncPayload = """
                {
                  "id": 1001,
                  "name": "payment-main-route",
                  "serviceId": 10,
                  "active": true,
                  "fromEndpoint": {
                    "id": 501,
                    "name": "payment-main-in",
                    "uri": "direct:payment-main-in",
                    "defaultRateLimit": 100,
                    "config": {}
                  },
                  "toEndpoint": {
                    "id": 502,
                    "name": "payment-main-out",
                    "uri": "direct:payment-main-out",
                    "defaultRateLimit": 100,
                    "config": {}
                  },
                  "components": [
                    {
                      "id": 701,
                      "name": "log-component",
                      "componentType": "LOG",
                      "className": "ir.bita.common.processor.TimingLogProcessor",
                      "orderIndex": 1,
                      "config": {
                        "message": "processing payment body=${body}"
                      }
                    },
                    {
                      "id": 702,
                      "name": "filter-component",
                      "componentType": "FILTER",
                      "className": "ir.bita.common.processor.CheckAccessProcessor",
                      "orderIndex": 2,
                      "config": {
                        "predicate": "${body} contains 'pay'"
                      }
                    }
                  ],
                  "files": []
                }
                """;

        RouteDefinition routeDefinition = toRouteDefinition(esmSyncPayload);

        DynamicRouteBuilder dynamicRouteBuilder = new DynamicRouteBuilder(camelContext);
        camelContext.addRoutes(dynamicRouteBuilder.buildRoute(routeDefinition, "route-1001"));

        // Collect final output for assertion
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:payment-main-out").to("mock:result");
            }
        });

        camelContext.start();

        MockEndpoint result = camelContext.getEndpoint("mock:result", MockEndpoint.class);
        result.expectedMessageCount(1);
        result.expectedBodiesReceived("pay-order-123");

        // Programmatic invocation through the generated direct bridge route
        Object response = producerTemplate.requestBody("direct:route-1001", "pay-order-123");

        result.assertIsSatisfied();
        assertTrue(response == null || "pay-order-123".equals(response.toString()));
    }

    private RouteDefinition toRouteDefinition(String esmSyncPayload) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        RouteSyncPayload payload = objectMapper.readValue(esmSyncPayload, RouteSyncPayload.class);

        RouteDefinition.RouteDefinitionBuilder builder = RouteDefinition.builder()
                .routeId(payload.id)
                .name(payload.name)
                .active(payload.active);

        if (payload.fromEndpoint != null && payload.fromEndpoint.uri != null && payload.fromEndpoint.uri.startsWith("direct:")) {
            builder.inputEndpointType("DIRECT")
                    .inputConfig(Map.of("name", payload.fromEndpoint.uri.substring("direct:".length())));
        }

        if (payload.toEndpoint != null && payload.toEndpoint.uri != null && payload.toEndpoint.uri.startsWith("direct:")) {
            builder.outputEndpointType("DIRECT")
                    .outputConfig(Map.of("name", payload.toEndpoint.uri.substring("direct:".length())));
        }

        List<ComponentDefinition> components = new ArrayList<>();
        if (payload.components != null) {
            for (ComponentSyncPayload c : payload.components) {
                components.add(ComponentDefinition.builder()
                        .componentId(c.id)
                        .name(c.name)
                        .type(c.componentType)
                        .order(c.orderIndex)
                        .config(c.config != null ? c.config : Map.of())
                        .build());
            }
        }
        builder.components(components);

        return builder.build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class RouteSyncPayload {
        public Long id;
        public String name;
        public Long serviceId;
        public boolean active;
        public EndpointSyncPayload fromEndpoint;
        public EndpointSyncPayload toEndpoint;
        public List<ComponentSyncPayload> components;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class EndpointSyncPayload {
        public Long id;
        public String name;
        public String uri;
        public Integer defaultRateLimit;
        public Map<String, Object> config;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ComponentSyncPayload {
        public Long id;
        public String name;
        public String componentType;
        public String className;
        public int orderIndex;
        public Map<String, Object> config;
    }
}
