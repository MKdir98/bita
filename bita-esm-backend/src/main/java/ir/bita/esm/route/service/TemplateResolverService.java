package ir.bita.esm.route.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for resolving template placeholders.
 */
@Service
@Slf4j
public class TemplateResolverService {

    /**
     * Pattern for matching placeholders like {{variableName}}.
     */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*(\\w+)\\s*\\}\\}");

    /**
     * Extracts the URI from camel_yaml and resolves placeholders.
     * Supports minimal YAML format: - from: { uri: "...", steps: [] }
     *
     * @param camelYaml the Camel YAML with placeholders in uri
     * @param config the configuration values
     * @return the resolved URI, or null if not found
     */
    public String extractAndResolveUriFromCamelYaml(String camelYaml, Map<String, Object> config) {
        if (camelYaml == null || config == null) {
            return null;
        }
        String uri = extractUriFromCamelYaml(camelYaml);
        return uri != null ? resolveUri(uri, config) : null;
    }

    private static final Pattern URI_IN_YAML = Pattern.compile("uri:\\s*[\"']([^\"']*)[\"']");

    /**
     * Extracts the URI string from minimal camel_yaml format.
     * Matches: uri: "value" or uri: 'value'
     */
    private String extractUriFromCamelYaml(String camelYaml) {
        Matcher m = URI_IN_YAML.matcher(camelYaml);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Resolves placeholders in a URI pattern.
     *
     * @param uriPattern the URI pattern with placeholders (e.g., "cxf:bean:{{serviceName}}")
     * @param config the configuration values
     * @return the resolved URI
     */
    public String resolveUri(String uriPattern, Map<String, Object> config) {
        if (uriPattern == null || config == null) {
            return uriPattern;
        }

        StringBuffer result = new StringBuffer();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(uriPattern);

        while (matcher.find()) {
            String placeholder = matcher.group(1);
            Object value = config.get(placeholder);

            if (value == null) {
                log.warn("Missing value for placeholder: {}", placeholder);
                matcher.appendReplacement(result, Matcher.quoteReplacement("{{" + placeholder + "}}"));
            } else {
                matcher.appendReplacement(result, Matcher.quoteReplacement(value.toString()));
            }
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Validates configuration against a JSON schema.
     *
     * @param config the configuration to validate
     * @param schema the JSON schema
     * @throws IllegalArgumentException if validation fails
     */
    public void validateConfig(Map<String, Object> config, Map<String, Object> schema) {
        if (schema == null || schema.isEmpty()) {
            return; // No schema, nothing to validate
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        if (properties == null) {
            return;
        }

        // Check required fields
        @SuppressWarnings("unchecked")
        java.util.List<String> required = (java.util.List<String>) schema.get("required");
        if (required != null) {
            for (String field : required) {
                if (!config.containsKey(field) || config.get(field) == null) {
                    throw new IllegalArgumentException("Missing required field: " + field);
                }
            }
        }

        // Validate types
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            String fieldName = entry.getKey();
            Object value = entry.getValue();

            @SuppressWarnings("unchecked")
            Map<String, Object> fieldSchema = (Map<String, Object>) properties.get(fieldName);
            if (fieldSchema != null) {
                validateFieldType(fieldName, value, fieldSchema);
            }
        }
    }

    private void validateFieldType(String fieldName, Object value, Map<String, Object> fieldSchema) {
        String expectedType = (String) fieldSchema.get("type");
        if (expectedType == null) {
            return;
        }

        boolean valid = switch (expectedType) {
            case "string" -> value instanceof String;
            case "integer" -> value instanceof Integer || value instanceof Long;
            case "number" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "array" -> value instanceof java.util.List;
            case "object" -> value instanceof Map;
            default -> true;
        };

        if (!valid) {
            throw new IllegalArgumentException(
                    String.format("Field '%s' expected type '%s' but got '%s'",
                            fieldName, expectedType, value.getClass().getSimpleName()));
        }

        // Validate enum values
        @SuppressWarnings("unchecked")
        java.util.List<Object> enumValues = (java.util.List<Object>) fieldSchema.get("enum");
        if (enumValues != null && !enumValues.contains(value)) {
            throw new IllegalArgumentException(
                    String.format("Field '%s' value '%s' not in allowed values: %s",
                            fieldName, value, enumValues));
        }

        // Validate min/max for numbers
        if (value instanceof Number number) {
            Number minimum = (Number) fieldSchema.get("minimum");
            Number maximum = (Number) fieldSchema.get("maximum");

            if (minimum != null && number.doubleValue() < minimum.doubleValue()) {
                throw new IllegalArgumentException(
                        String.format("Field '%s' value %s is below minimum %s",
                                fieldName, value, minimum));
            }
            if (maximum != null && number.doubleValue() > maximum.doubleValue()) {
                throw new IllegalArgumentException(
                        String.format("Field '%s' value %s exceeds maximum %s",
                                fieldName, value, maximum));
            }
        }

        // Validate string length
        if (value instanceof String str) {
            Integer minLength = (Integer) fieldSchema.get("minLength");
            Integer maxLength = (Integer) fieldSchema.get("maxLength");

            if (minLength != null && str.length() < minLength) {
                throw new IllegalArgumentException(
                        String.format("Field '%s' length %d is below minimum %d",
                                fieldName, str.length(), minLength));
            }
            if (maxLength != null && str.length() > maxLength) {
                throw new IllegalArgumentException(
                        String.format("Field '%s' length %d exceeds maximum %d",
                                fieldName, str.length(), maxLength));
            }
        }
    }

    /**
     * Applies default values from schema to config.
     *
     * @param config the configuration
     * @param schema the JSON schema with defaults
     * @return config with defaults applied
     */
    public Map<String, Object> applyDefaults(Map<String, Object> config, Map<String, Object> schema) {
        if (schema == null || config == null) {
            return config;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        if (properties == null) {
            return config;
        }

        java.util.Map<String, Object> result = new java.util.HashMap<>(config);

        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String fieldName = entry.getKey();
            @SuppressWarnings("unchecked")
            Map<String, Object> fieldSchema = (Map<String, Object>) entry.getValue();

            if (!result.containsKey(fieldName) && fieldSchema.containsKey("default")) {
                result.put(fieldName, fieldSchema.get("default"));
            }
        }

        return result;
    }
}
