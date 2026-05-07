package ir.bita.esb.route;

import io.vertx.core.Vertx;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ir.bita.esb.cache.RouteCache;
import ir.bita.esb.config.EsbConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: ESB Core can bring up a service with routes and process requests.
 * Simulates the flow: RouteManager.initialize() + route execution.
 */
@DisplayName("ESB Service Startup Integration")
class EsbServiceStartupIntegrationTest {

    private Vertx vertx;
    private RouteManager routeManager;
    private RouteCache routeCache;
    private ProducerTemplate producerTemplate;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        EsbConfig config = EsbConfig.builder()
                .serviceId(10L)
                .httpPort(8080)
                .esmBaseUrl("http://test:8081")
                .esmApiKey("test-key")
                .kafkaBootstrapServers("localhost:9092")
                .kafkaGroupId("esb-test")
                .redisHost("localhost")
                .redisPort(6379)
                .redisPassword("")
                .elasticsearchUrl("http://localhost:9200")
                .build();

        routeCache = new RouteCache();
        routeManager = new RouteManager(vertx, config, routeCache);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (producerTemplate != null) {
            producerTemplate.stop();
        }
        if (routeManager != null && routeManager.isInitialized()) {
            CountDownLatch latch = new CountDownLatch(1);
            routeManager.shutdown()
                    .onSuccess(v -> latch.countDown())
                    .onFailure(err -> latch.countDown());
            latch.await(5, TimeUnit.SECONDS);
        }
        if (vertx != null) {
            vertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("Service starts with routes and processes request successfully")
    void shouldStartServiceAndProcessRequest() throws Exception {
        // Given: route definition in ESM sync format
        RouteDefinition routeDef = buildRouteFromEsmSyncFormat();

        // When: initialize routes (simulates fullSync from ESM)
        CountDownLatch initLatch = new CountDownLatch(1);
        routeManager.initialize(List.of(routeDef))
                .onSuccess(v -> initLatch.countDown())
                .onFailure(err -> initLatch.countDown());

        assertThat(initLatch.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(routeManager.isInitialized()).isTrue();
        assertThat(routeCache.size()).isEqualTo(1);

        // Get Camel context for execution test
        var camelContext = routeManager.getCamelContextForTesting();
        producerTemplate = camelContext.createProducerTemplate();

        // Add mock consumer for output (direct:payment-main-out)
        camelContext.addRoutes(new org.apache.camel.builder.RouteBuilder() {
            @Override
            public void configure() {
                from("direct:payment-main-out").to("mock:result");
            }
        });

        // When: send request through route (entry: direct:route-1001)
        Object response = producerTemplate.requestBody("direct:route-1001", "pay-order-123");

        // Then: route processed successfully
        MockEndpoint result = camelContext.getEndpoint("mock:result", MockEndpoint.class);
        result.expectedMessageCount(1);
        result.expectedBodiesReceived("pay-order-123");
        result.assertIsSatisfied();

        assertThat(response).isIn("pay-order-123", null);

        // When: send request that fails filter (body does not contain 'pay')
        result.reset();
        result.expectedMessageCount(0);
        producerTemplate.requestBody("direct:route-1001", "fail-order-123");

        // Then: filter rejects, mock receives nothing
        result.assertIsSatisfied();
    }

    /**
     * Build RouteDefinition from ESM sync payload format (fromEndpoint, toEndpoint, components).
     */
    private RouteDefinition buildRouteFromEsmSyncFormat() {
        List<ComponentDefinition> components = new ArrayList<>();
        components.add(ComponentDefinition.builder()
                .componentId(701L)
                .name("log-component")
                .type("LOG")
                .order(1)
                .config(Map.of("message", "processing payment body=${body}"))
                .build());
        components.add(ComponentDefinition.builder()
                .componentId(702L)
                .name("filter-component")
                .type("FILTER")
                .order(2)
                .config(Map.of("predicate", "${body} contains 'pay'"))
                .build());

        return RouteDefinition.builder()
                .routeId(1001L)
                .name("payment-main-route")
                .active(true)
                .inputUri("direct:payment-main-in")
                .outputUri("direct:payment-main-out")
                .components(components)
                .build();
    }
}
