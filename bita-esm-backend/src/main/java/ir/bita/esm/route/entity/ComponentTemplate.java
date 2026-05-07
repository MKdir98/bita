package ir.bita.esm.route.entity;

import io.hypersistence.utils.hibernate.type.array.ListArrayType;
import ir.bita.common.domain.ComponentType;
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
 * Component Template entity.
 * Defines reusable processor/bean patterns.
 */
@Entity
@Table(name = "component_template", indexes = {
        @Index(name = "idx_component_template_name", columnList = "name"),
        @Index(name = "idx_component_template_type", columnList = "component_type")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Audited
public class ComponentTemplate extends SoftDeletableEntity {

    @Column(name = "name", unique = true, nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "component_type", nullable = false, length = 20)
    private ComponentType componentType;

    /**
     * Fully qualified class name of the processor or bean.
     */
    @Column(name = "class_name", nullable = false)
    private String className;

    /**
     * Camel YAML definition for this component (bean definition).
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
     * Category for grouping templates.
     */
    @Column(name = "category", length = 50)
    private String category;
}
