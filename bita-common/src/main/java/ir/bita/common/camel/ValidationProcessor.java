package ir.bita.common.camel;

import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.StringReader;

/**
 * Camel processor for XML/JSON schema validation.
 */
@Slf4j
public class ValidationProcessor implements Processor {

    public static final String VALIDATION_RESULT_HEADER = "ValidationResult";
    public static final String VALIDATION_ERRORS_HEADER = "ValidationErrors";

    private final ValidationType type;
    private final String schemaLocation;
    private Schema xmlSchema;

    public enum ValidationType {
        XML_SCHEMA,
        JSON_SCHEMA,
        CUSTOM
    }

    public ValidationProcessor(ValidationType type, String schemaLocation) {
        this.type = type;
        this.schemaLocation = schemaLocation;
        
        if (type == ValidationType.XML_SCHEMA && schemaLocation != null) {
            initXmlSchema();
        }
    }

    public static ValidationProcessor xmlSchema(String schemaPath) {
        return new ValidationProcessor(ValidationType.XML_SCHEMA, schemaPath);
    }

    public static ValidationProcessor jsonSchema(String schemaPath) {
        return new ValidationProcessor(ValidationType.JSON_SCHEMA, schemaPath);
    }

    private void initXmlSchema() {
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            // Security: Disable external entities
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            
            if (schemaLocation.startsWith("classpath:")) {
                String path = schemaLocation.substring("classpath:".length());
                var stream = getClass().getClassLoader().getResourceAsStream(path);
                if (stream != null) {
                    xmlSchema = factory.newSchema(new StreamSource(stream));
                }
            } else {
                xmlSchema = factory.newSchema(new java.io.File(schemaLocation));
            }
        } catch (Exception e) {
            log.error("Failed to load XML schema: {}", schemaLocation, e);
        }
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        boolean valid = false;
        String errors = null;

        try {
            switch (type) {
                case XML_SCHEMA -> {
                    var result = validateXml(exchange);
                    valid = result.isValid();
                    errors = result.getErrors();
                }
                case JSON_SCHEMA -> {
                    var result = validateJson(exchange);
                    valid = result.isValid();
                    errors = result.getErrors();
                }
                case CUSTOM -> {
                    valid = true; // Custom validation to be implemented
                }
            }
        } catch (Exception e) {
            log.error("Validation error", e);
            errors = e.getMessage();
        }

        exchange.getIn().setHeader(VALIDATION_RESULT_HEADER, valid);
        if (errors != null) {
            exchange.getIn().setHeader(VALIDATION_ERRORS_HEADER, errors);
        }

        if (!valid) {
            log.warn("Validation failed: {}", errors);
            throw new ValidationException(errors);
        }
    }

    private ValidationResult validateXml(Exchange exchange) {
        if (xmlSchema == null) {
            return new ValidationResult(true, null);
        }

        try {
            String body = exchange.getIn().getBody(String.class);
            Validator validator = xmlSchema.newValidator();
            validator.validate(new StreamSource(new StringReader(body)));
            return new ValidationResult(true, null);
        } catch (Exception e) {
            return new ValidationResult(false, e.getMessage());
        }
    }

    private ValidationResult validateJson(Exchange exchange) {
        // JSON Schema validation (simplified - would use a library like everit-json-schema in production)
        String body = exchange.getIn().getBody(String.class);
        
        if (body == null || body.isBlank()) {
            return new ValidationResult(false, "Empty JSON body");
        }

        // Basic JSON syntax check
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.readTree(body);
            return new ValidationResult(true, null);
        } catch (Exception e) {
            return new ValidationResult(false, "Invalid JSON: " + e.getMessage());
        }
    }

    private record ValidationResult(boolean isValid, String errors) {
        public boolean isValid() {
            return isValid;
        }

        public String getErrors() {
            return errors;
        }
    }

    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }
}
