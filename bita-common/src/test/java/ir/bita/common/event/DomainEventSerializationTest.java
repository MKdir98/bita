package ir.bita.common.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ir.bita.common.domain.CredentialType;
import ir.bita.common.domain.ServicePhase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Domain Event JSON Serialization")
class DomainEventSerializationTest {

    private static ObjectMapper objectMapper;

    @BeforeAll
    static void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("should serialize and deserialize ClientCreatedEvent")
    void shouldSerializeClientCreatedEvent() throws Exception {
        ClientCreatedEvent event = new ClientCreatedEvent(1L, "Test Client", "admin");

        String json = objectMapper.writeValueAsString(event);
        DomainEvent deserialized = objectMapper.readValue(json, DomainEvent.class);

        assertThat(deserialized).isInstanceOf(ClientCreatedEvent.class);
        ClientCreatedEvent result = (ClientCreatedEvent) deserialized;
        assertThat(result.getClientId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Test Client");
    }

    @Test
    @DisplayName("should serialize and deserialize CredentialAddedEvent")
    void shouldSerializeCredentialAddedEvent() throws Exception {
        CredentialAddedEvent event = new CredentialAddedEvent(
                100L, 1L, CredentialType.IP_ADDRESS, "192.168.*.*", "admin");

        String json = objectMapper.writeValueAsString(event);
        DomainEvent deserialized = objectMapper.readValue(json, DomainEvent.class);

        assertThat(deserialized).isInstanceOf(CredentialAddedEvent.class);
        CredentialAddedEvent result = (CredentialAddedEvent) deserialized;
        assertThat(result.getCredentialType()).isEqualTo(CredentialType.IP_ADDRESS);
    }

    @Test
    @DisplayName("should serialize and deserialize ServicePhaseChangedEvent")
    void shouldSerializeServicePhaseChangedEvent() throws Exception {
        ServicePhaseChangedEvent event = new ServicePhaseChangedEvent(
                50L, "PaymentService", ServicePhase.TEST, ServicePhase.ACTIVE, "Ready for production", "admin");

        String json = objectMapper.writeValueAsString(event);
        DomainEvent deserialized = objectMapper.readValue(json, DomainEvent.class);

        assertThat(deserialized).isInstanceOf(ServicePhaseChangedEvent.class);
        ServicePhaseChangedEvent result = (ServicePhaseChangedEvent) deserialized;
        assertThat(result.getOldPhase()).isEqualTo(ServicePhase.TEST);
        assertThat(result.getNewPhase()).isEqualTo(ServicePhase.ACTIVE);
        assertThat(result.requiresDeployment()).isTrue();
    }

    @Test
    @DisplayName("should serialize and deserialize AccessGrantedEvent")
    void shouldSerializeAccessGrantedEvent() throws Exception {
        AccessGrantedEvent event = new AccessGrantedEvent(
                200L, 1L, "Test Client", 50L, "PaymentService",
                100, LocalDateTime.now(), LocalDateTime.now().plusYears(1), "Contract signed", "admin");

        String json = objectMapper.writeValueAsString(event);
        DomainEvent deserialized = objectMapper.readValue(json, DomainEvent.class);

        assertThat(deserialized).isInstanceOf(AccessGrantedEvent.class);
        AccessGrantedEvent result = (AccessGrantedEvent) deserialized;
        assertThat(result.getCustomRateLimit()).isEqualTo(100);
    }

    @Test
    @DisplayName("should include eventType in JSON")
    void shouldIncludeEventTypeInJson() throws Exception {
        ClientCreatedEvent event = new ClientCreatedEvent(1L, "Test", "admin");

        String json = objectMapper.writeValueAsString(event);

        assertThat(json).contains("\"eventType\":\"CLIENT_CREATED\"");
    }

    @Test
    @DisplayName("should have correct topic and partition key")
    void shouldHaveCorrectTopicAndPartitionKey() {
        ClientCreatedEvent clientEvent = new ClientCreatedEvent(1L, "Test", "admin");
        RouteCreatedEvent routeEvent = new RouteCreatedEvent(10L, 50L, "Route1", "from", "to", "admin");

        assertThat(clientEvent.getTopic()).isEqualTo("bita.clients");
        assertThat(clientEvent.getPartitionKey()).isEqualTo("1");

        assertThat(routeEvent.getTopic()).isEqualTo("bita.routes");
        assertThat(routeEvent.getPartitionKey()).isEqualTo("50"); // serviceId for ordering
    }
}
