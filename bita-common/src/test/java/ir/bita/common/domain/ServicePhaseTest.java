package ir.bita.common.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ServicePhase Enum")
class ServicePhaseTest {

    @Test
    @DisplayName("DRAFT should not be deployed")
    void draftShouldNotBeDeployed() {
        assertThat(ServicePhase.DRAFT.isDeployed()).isFalse();
    }

    @Test
    @DisplayName("TEST should be deployed")
    void testShouldBeDeployed() {
        assertThat(ServicePhase.TEST.isDeployed()).isTrue();
    }

    @Test
    @DisplayName("ACTIVE should be deployed")
    void activeShouldBeDeployed() {
        assertThat(ServicePhase.ACTIVE.isDeployed()).isTrue();
    }

    @ParameterizedTest
    @DisplayName("should validate phase transitions correctly")
    @CsvSource({
            "DRAFT, TEST, true",
            "DRAFT, ACTIVE, true",
            "DRAFT, DRAFT, false",
            "TEST, DRAFT, true",
            "TEST, ACTIVE, true",
            "TEST, TEST, false",
            "ACTIVE, DRAFT, true",
            "ACTIVE, TEST, true",
            "ACTIVE, ACTIVE, false"
    })
    void shouldValidateTransitions(ServicePhase from, ServicePhase to, boolean expected) {
        assertThat(from.canTransitionTo(to)).isEqualTo(expected);
    }

    @Test
    @DisplayName("should have correct display names")
    void shouldHaveCorrectDisplayNames() {
        assertThat(ServicePhase.DRAFT.getDisplayName()).isEqualTo("Draft");
        assertThat(ServicePhase.TEST.getDisplayName()).isEqualTo("Test");
        assertThat(ServicePhase.ACTIVE.getDisplayName()).isEqualTo("Active");
    }
}
