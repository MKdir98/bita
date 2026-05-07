package ir.bita.common.domain;

/**
 * Enum for attachment file types in the unified attachment table.
 * Matches PostgreSQL attachment_file_type enum.
 */
public enum AttachmentFileType {

    JAR,
    PROPERTIES,
    XML,
    OTHER
}
