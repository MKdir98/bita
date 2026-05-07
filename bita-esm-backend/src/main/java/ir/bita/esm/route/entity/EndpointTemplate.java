package ir.bita.esm.route.entity;

import io.hypersistence.utils.hibernate.type.array.ListArrayType;
import ir.bita.esm.shared.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;

/**
 * Endpoint Template entity.
 * Defines reusable endpoint patterns with configurable parameters.
 */
@Entity
@Table(name = "endpoint_template", indexes = {
        @Index(name = "idx_endpoint_template_name", columnList = "name")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Audited
public class EndpointTemplate extends SoftDeletableEntity {

    @Column(name = "name", unique = true, nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    /**
     * Camel YAML definition with placeholders (beans + from).
     * Replaces uri_pattern. Example minimal: - from: { uri: "{{resolved_uri}}", steps: [] }
     */
    @Column(name = "camel_yaml", columnDefinition = "TEXT")
    private String camelYaml;

    /**
     * IDs of attachments (JAR, etc.) for this template.
     */
    @Type(ListArrayType.class)
    @Column(name = "attachment_ids", columnDefinition = "bigint[]")
    @Builder.Default
    private List<Long> attachmentIds = new java.util.ArrayList<>();

    /**
     * JSON Schema defining the configuration parameters.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_schema", columnDefinition = "jsonb")
    private Map<String, Object> configSchema;

    /**
     * Default rate limit for endpoints created from this template.
     */
    @Column(name = "default_rate_limit")
    private Integer defaultRateLimit;

    /**
     * Category for grouping templates (e.g., "soap", "rest", "file").
     */
    @Column(name = "category", length = 50)
    private String category;
}
