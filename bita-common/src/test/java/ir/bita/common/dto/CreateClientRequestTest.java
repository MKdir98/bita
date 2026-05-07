package ir.bita.common.dto;

import ir.bita.common.dto.client.CreateClientRequest;
import ir.bita.common.dto.client.TagDto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CreateClientRequest Validation")
class CreateClientRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("should pass validation with valid data")
    void shouldPassWithValidData() {
        CreateClientRequest request = CreateClientRequest.builder()
                .name("Test Organization")
                .description("A test organization")
                .contactEmail("test@example.com")
                .contactPhone("09123456789")
                .tags(List.of(
                        TagDto.builder().key("type").value("private").build(),
                        TagDto.builder().key("region").value("tehran").build()
                ))
                .build();

        Set<ConstraintViolation<CreateClientRequest>> violations = validator.validate(request);
        
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("should pass validation without tags")
    void shouldPassWithoutTags() {
        CreateClientRequest request = CreateClientRequest.builder()
                .name("Test Organization")
                .description("A test organization")
                .build();

        Set<ConstraintViolation<CreateClientRequest>> violations = validator.validate(request);
        
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("should fail validation when name is empty")
    void shouldFailWhenNameIsEmpty() {
        CreateClientRequest request = CreateClientRequest.builder()
                .name("")
                .build();

        Set<ConstraintViolation<CreateClientRequest>> violations = validator.validate(request);
        
        assertThat(violations)
                .isNotEmpty()
                .anyMatch(v -> v.getPropertyPath().toString().equals("name"));
    }

    @Test
    @DisplayName("should fail validation when email is invalid")
    void shouldFailWhenEmailInvalid() {
        CreateClientRequest request = CreateClientRequest.builder()
                .name("Test Organization")
                .contactEmail("invalid-email")
                .build();

        Set<ConstraintViolation<CreateClientRequest>> violations = validator.validate(request);
        
        assertThat(violations)
                .isNotEmpty()
                .anyMatch(v -> v.getPropertyPath().toString().equals("contactEmail"));
    }

    @Test
    @DisplayName("should fail validation when phone is not in Iranian format")
    void shouldFailWhenPhoneNotIranianFormat() {
        CreateClientRequest request = CreateClientRequest.builder()
                .name("Test Organization")
                .contactPhone("1234567890") // Not starting with 09
                .build();

        Set<ConstraintViolation<CreateClientRequest>> violations = validator.validate(request);
        
        assertThat(violations)
                .isNotEmpty()
                .anyMatch(v -> v.getPropertyPath().toString().equals("contactPhone"));
    }

    @Test
    @DisplayName("should accept valid Iranian phone number")
    void shouldAcceptValidIranianPhone() {
        CreateClientRequest request = CreateClientRequest.builder()
                .name("Test Organization")
                .contactPhone("09123456789")
                .build();

        Set<ConstraintViolation<CreateClientRequest>> violations = validator.validate(request);
        
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("should fail validation when tag key is empty")
    void shouldFailWhenTagKeyIsEmpty() {
        CreateClientRequest request = CreateClientRequest.builder()
                .name("Test Organization")
                .tags(List.of(
                        TagDto.builder().key("").value("test").build()
                ))
                .build();

        Set<ConstraintViolation<CreateClientRequest>> violations = validator.validate(request);
        
        assertThat(violations)
                .isNotEmpty()
                .anyMatch(v -> v.getPropertyPath().toString().contains("key"));
    }

    @Test
    @DisplayName("should fail validation when tag value is empty")
    void shouldFailWhenTagValueIsEmpty() {
        CreateClientRequest request = CreateClientRequest.builder()
                .name("Test Organization")
                .tags(List.of(
                        TagDto.builder().key("type").value("").build()
                ))
                .build();

        Set<ConstraintViolation<CreateClientRequest>> violations = validator.validate(request);
        
        assertThat(violations)
                .isNotEmpty()
                .anyMatch(v -> v.getPropertyPath().toString().contains("value"));
    }
}
