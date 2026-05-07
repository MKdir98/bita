package ir.bita.common.camel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
import java.util.function.Function;

/**
 * Camel processor for data transformation.
 */
@Slf4j
public class TransformProcessor implements Processor {

    private final TransformationType type;
    private final Object transformConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public enum TransformationType {
        XSLT,
        JSON_PATCH,
        JSON_MAPPING,
        CUSTOM
    }

    private TransformProcessor(TransformationType type, Object config) {
        this.type = type;
        this.transformConfig = config;
    }

    /**
     * Create XSLT transformer.
     */
    public static TransformProcessor xslt(String xsltPath) {
        return new TransformProcessor(TransformationType.XSLT, xsltPath);
    }

    /**
     * Create JSON field mapper.
     */
    public static TransformProcessor jsonMapping(Map<String, String> fieldMapping) {
        return new TransformProcessor(TransformationType.JSON_MAPPING, fieldMapping);
    }

    /**
     * Create custom transformer with lambda.
     */
    public static TransformProcessor custom(Function<String, String> transformer) {
        return new TransformProcessor(TransformationType.CUSTOM, transformer);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String body = exchange.getIn().getBody(String.class);
        String result;

        switch (type) {
            case XSLT -> result = transformXslt(body);
            case JSON_MAPPING -> result = transformJsonMapping(body);
            case CUSTOM -> result = transformCustom(body);
            default -> result = body;
        }

        exchange.getIn().setBody(result);
    }

    private String transformXslt(String body) throws Exception {
        String xsltPath = (String) transformConfig;
        
        TransformerFactory factory = TransformerFactory.newInstance();
        // Security settings
        factory.setFeature("http://javax.xml.XMLConstants/feature/secure-processing", true);
        
        StreamSource xsltSource;
        if (xsltPath.startsWith("classpath:")) {
            String path = xsltPath.substring("classpath:".length());
            var stream = getClass().getClassLoader().getResourceAsStream(path);
            if (stream == null) {
                throw new RuntimeException("XSLT not found: " + xsltPath);
            }
            xsltSource = new StreamSource(stream);
        } else {
            xsltSource = new StreamSource(new java.io.File(xsltPath));
        }

        Transformer transformer = factory.newTransformer(xsltSource);
        StringWriter writer = new StringWriter();
        transformer.transform(new StreamSource(new StringReader(body)), new StreamResult(writer));
        
        return writer.toString();
    }

    @SuppressWarnings("unchecked")
    private String transformJsonMapping(String body) throws Exception {
        Map<String, String> mapping = (Map<String, String>) transformConfig;
        
        JsonNode sourceNode = objectMapper.readTree(body);
        ObjectNode targetNode = objectMapper.createObjectNode();

        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            String targetField = entry.getKey();
            String sourcePath = entry.getValue();
            
            JsonNode value = navigateToNode(sourceNode, sourcePath);
            if (value != null) {
                setNodeValue(targetNode, targetField, value);
            }
        }

        return objectMapper.writeValueAsString(targetNode);
    }

    private JsonNode navigateToNode(JsonNode root, String path) {
        String[] parts = path.split("\\.");
        JsonNode current = root;
        
        for (String part : parts) {
            if (current == null) return null;
            
            // Handle array index notation like items[0]
            if (part.contains("[")) {
                int bracketStart = part.indexOf('[');
                String fieldName = part.substring(0, bracketStart);
                int index = Integer.parseInt(part.substring(bracketStart + 1, part.indexOf(']')));
                
                current = current.get(fieldName);
                if (current != null && current.isArray()) {
                    current = current.get(index);
                } else {
                    return null;
                }
            } else {
                current = current.get(part);
            }
        }
        
        return current;
    }

    private void setNodeValue(ObjectNode target, String path, JsonNode value) {
        String[] parts = path.split("\\.");
        ObjectNode current = target;
        
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            if (!current.has(part)) {
                current.putObject(part);
            }
            current = (ObjectNode) current.get(part);
        }
        
        String lastPart = parts[parts.length - 1];
        current.set(lastPart, value);
    }

    @SuppressWarnings("unchecked")
    private String transformCustom(String body) {
        Function<String, String> transformer = (Function<String, String>) transformConfig;
        return transformer.apply(body);
    }

    /**
     * Builder for creating complex transformations.
     */
    public static class Builder {
        private TransformationType type;
        private Object config;

        public Builder xslt(String path) {
            this.type = TransformationType.XSLT;
            this.config = path;
            return this;
        }

        public Builder jsonMapping(Map<String, String> mapping) {
            this.type = TransformationType.JSON_MAPPING;
            this.config = mapping;
            return this;
        }

        public TransformProcessor build() {
            return new TransformProcessor(type, config);
        }
    }
}
