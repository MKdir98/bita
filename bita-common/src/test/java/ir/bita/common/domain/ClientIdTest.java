package ir.bita.common.domain;

import ir.bita.common.exception.InvalidIdException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ClientId Value Object")
class ClientIdTest {

    @Nested
    @DisplayName("Creation from Long")
    class CreationFromLong {

        @Test
        @DisplayName("should create ClientId with valid positive value")
        void shouldCreateWithValidValue() {
            ClientId id = ClientId.of(123L);
            
            assertThat(id.getValue()).isEqualTo(123L);
            assertThat(id.toString()).isEqualTo("123");
        }

        @Test
        @DisplayName("should throw exception for null value")
        void shouldThrowForNullValue() {
            assertThatThrownBy(() -> ClientId.of((Long) null))
                    .isInstanceOf(InvalidIdException.class)
                    .hasMessageContaining("ClientId")
                    .hasMessageContaining("null");
        }

        @Test
        @DisplayName("should throw exception for zero value")
        void shouldThrowForZeroValue() {
            assertThatThrownBy(() -> ClientId.of(0L))
                    .isInstanceOf(InvalidIdException.class)
                    .hasMessageContaining("positive");
        }

        @Test
        @DisplayName("should throw exception for negative value")
        void shouldThrowForNegativeValue() {
            assertThatThrownBy(() -> ClientId.of(-5L))
                    .isInstanceOf(InvalidIdException.class)
                    .hasMessageContaining("positive");
        }
    }

    @Nested
    @DisplayName("Creation from Integer")
    class CreationFromInteger {

        @Test
        @DisplayName("should create ClientId with valid positive value")
        void shouldCreateWithValidValue() {
            ClientId id = ClientId.of(42);
            
            assertThat(id.getValue()).isEqualTo(42L);
        }

        @Test
        @DisplayName("should throw exception for null Integer")
        void shouldThrowForNullInteger() {
            assertThatThrownBy(() -> ClientId.of((Integer) null))
                    .isInstanceOf(InvalidIdException.class);
        }
    }

    @Nested
    @DisplayName("Creation from String")
    class CreationFromString {

        @Test
        @DisplayName("should create ClientId from valid string")
        void shouldCreateFromValidString() {
            ClientId id = ClientId.fromString("999");
            
            assertThat(id.getValue()).isEqualTo(999L);
        }

        @Test
        @DisplayName("should trim whitespace from string")
        void shouldTrimWhitespace() {
            ClientId id = ClientId.fromString("  456  ");
            
            assertThat(id.getValue()).isEqualTo(456L);
        }

        @Test
        @DisplayName("should throw exception for non-numeric string")
        void shouldThrowForNonNumericString() {
            assertThatThrownBy(() -> ClientId.fromString("abc"))
                    .isInstanceOf(InvalidIdException.class)
                    .hasMessageContaining("valid number");
        }

        @Test
        @DisplayName("should throw exception for blank string")
        void shouldThrowForBlankString() {
            assertThatThrownBy(() -> ClientId.fromString("   "))
                    .isInstanceOf(InvalidIdException.class)
                    .hasMessageContaining("blank");
        }
    }

    @Nested
    @DisplayName("Equality and HashCode")
    class EqualityAndHashCode {

        @Test
        @DisplayName("should be equal for same value")
        void shouldBeEqualForSameValue() {
            ClientId id1 = ClientId.of(100L);
            ClientId id2 = ClientId.of(100L);
            
            assertThat(id1).isEqualTo(id2);
            assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
        }

        @Test
        @DisplayName("should not be equal for different values")
        void shouldNotBeEqualForDifferentValues() {
            ClientId id1 = ClientId.of(100L);
            ClientId id2 = ClientId.of(200L);
            
            assertThat(id1).isNotEqualTo(id2);
        }

        @Test
        @DisplayName("should not be equal to null")
        void shouldNotBeEqualToNull() {
            ClientId id = ClientId.of(100L);
            
            assertThat(id).isNotEqualTo(null);
        }

        @Test
        @DisplayName("should not be equal to different type")
        void shouldNotBeEqualToDifferentType() {
            ClientId id = ClientId.of(100L);
            
            assertThat(id).isNotEqualTo("100");
        }
    }

    @Nested
    @DisplayName("Comparison")
    class Comparison {

        @Test
        @DisplayName("should compare correctly")
        void shouldCompareCorrectly() {
            ClientId id1 = ClientId.of(100L);
            ClientId id2 = ClientId.of(200L);
            ClientId id3 = ClientId.of(100L);
            
            assertThat(id1.compareTo(id2)).isLessThan(0);
            assertThat(id2.compareTo(id1)).isGreaterThan(0);
            assertThat(id1.compareTo(id3)).isEqualTo(0);
        }
    }
}
