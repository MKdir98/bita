package ir.bita.common.domain;

/**
 * Enum representing the types of files that can be attached to routes.
 */
public enum FileType {

    /**
     * WSDL (Web Services Description Language) file.
     * Describes SOAP service interfaces.
     */
    WSDL("WSDL", "Web Services Description Language", ".wsdl", "application/wsdl+xml"),

    /**
     * XSD (XML Schema Definition) file.
     * Defines XML document structure.
     */
    XSD("XSD", "XML Schema Definition", ".xsd", "application/xml"),

    /**
     * Properties file for configuration.
     */
    PROPERTIES("Properties", "Configuration properties file", ".properties", "text/plain"),

    /**
     * Java source file.
     * Custom processors or beans.
     */
    JAVA("Java", "Java source file", ".java", "text/x-java-source"),

    /**
     * JAR (Java Archive) file.
     * Compiled Java libraries.
     */
    JAR("JAR", "Java Archive", ".jar", "application/java-archive"),

    /**
     * Other file types not categorized above.
     */
    OTHER("Other", "Other file type", null, "application/octet-stream");

    private final String displayName;
    private final String description;
    private final String extension;
    private final String mimeType;

    FileType(String displayName, String description, String extension, String mimeType) {
        this.displayName = displayName;
        this.description = description;
        this.extension = extension;
        this.mimeType = mimeType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String getExtension() {
        return extension;
    }

    public String getMimeType() {
        return mimeType;
    }

    /**
     * Determines the FileType from a filename based on its extension.
     *
     * @param filename the name of the file
     * @return the matching FileType, or OTHER if no match
     */
    public static FileType fromFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return OTHER;
        }
        
        String lowerFilename = filename.toLowerCase();
        
        for (FileType type : values()) {
            if (type.extension != null && lowerFilename.endsWith(type.extension)) {
                return type;
            }
        }
        
        return OTHER;
    }

    /**
     * Checks if this file type is XML-based.
     */
    public boolean isXmlBased() {
        return this == WSDL || this == XSD;
    }

    /**
     * Checks if this file type is Java-related.
     */
    public boolean isJavaRelated() {
        return this == JAVA || this == JAR;
    }
}
