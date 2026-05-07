package ir.bita.esm.route.entity;

import io.hypersistence.utils.hibernate.type.array.ListArrayType;
import ir.bita.esm.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;

/**
 * Endpoint instance entity.
 * Created from an EndpointTemplate with specific configuration.
 */
@Entity
@Table(name = "endpoint", indexes = {
        @Index(name = "idx_endpoint_template", columnList = "template_id"),
        @Index(name = "idx_endpoint_name", columnList = "name")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Audited
public class Endpoint extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private EndpointTemplate template;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    /**
     * The resolved URI for this endpoint.
     */
    @Column(name = "uri", nullable = false, length = 1000)
    private String uri;

    /**
     * Resolved Camel YAML for this endpoint (after placeholder substitution).
     */
    @Column(name = "resolved_camel_yaml", columnDefinition = "TEXT")
    private String resolvedCamelYaml;

    /**
     * IDs of attachments (JAR, etc.) for this endpoint instance.
     */
    @Type(ListArrayType.class)
    @Column(name = "attachment_ids", columnDefinition = "bigint[]")
    @Builder.Default
    private List<Long> attachmentIds = new java.util.ArrayList<>();

    /**
     * Configuration values for this endpoint.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", columnDefinition = "jsonb")
    private Map<String, Object> config;

    /**
     * Default rate limit (requests per minute).
     */
    @Column(name = "default_rate_limit")
    private Integer defaultRateLimit;
}
