package ir.bita.common.domain;

/**
 * Enum representing the types of components used in Camel routes.
 */
public enum ComponentType {

    /**
     * A Camel processor that transforms or processes messages.
     * Examples: CheckAccessProcessor, PrepareHeadersProcessor, etc.
     */
    PROCESSOR("Processor", "Camel processor for message transformation"),

    /**
     * A Spring bean that provides functionality to routes.
     * Examples: Service beans, validators, etc.
     */
    BEAN("Bean", "Spring bean component");

    private final String displayName;
    private final String description;

    ComponentType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
