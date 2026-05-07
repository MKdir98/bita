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
 * Route Template entity.
 * Defines reusable route patterns with pre-configured endpoints and components.
 */
@Entity
@Table(name = "route_template", indexes = {
        @Index(name = "idx_route_template_name", columnList = "name")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Audited
public class RouteTemplate extends SoftDeletableEntity {

    @Column(name = "name", unique = true, nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    /**
     * Reference to the from endpoint template.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_endpoint_template_id")
    private EndpointTemplate fromEndpointTemplate;

    /**
     * Reference to the to endpoint template.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_endpoint_template_id")
    private EndpointTemplate toEndpointTemplate;

    /**
     * Camel YAML definition for this route template.
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
     * JSON Schema defining the overall route configuration.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_schema", columnDefinition = "jsonb")
    private Map<String, Object> configSchema;

    /**
     * Pre-configured component order as JSON.
     * Example: [{"templateId": 1, "config": {...}}, {"templateId": 2}]
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "component_config", columnDefinition = "jsonb")
    private Map<String, Object> componentConfig;

    /**
     * Category for grouping templates.
     */
    @Column(name = "category", length = 50)
    private String category;
}
