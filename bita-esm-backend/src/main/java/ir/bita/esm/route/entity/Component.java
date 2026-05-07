package ir.bita.esm.route.entity;

import io.hypersistence.utils.hibernate.type.array.ListArrayType;
import ir.bita.common.domain.ComponentType;
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
 * Component instance entity.
 * Created from a ComponentTemplate with specific configuration.
 */
@Entity
@Table(name = "component", indexes = {
        @Index(name = "idx_component_template", columnList = "template_id"),
        @Index(name = "idx_component_route", columnList = "route_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Audited
public class Component extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private ComponentTemplate template;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "component_type", nullable = false, length = 20)
    private ComponentType componentType;

    /**
     * Order of this component in the route processing chain.
     */
    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    /**
     * Fully qualified class name of the processor or bean.
     */
    @Column(name = "class_name", nullable = false)
    private String className;

    /**
     * IDs of attachments (JAR, etc.) for this component instance.
     */
    @Type(ListArrayType.class)
    @Column(name = "attachment_ids", columnDefinition = "bigint[]")
    @Builder.Default
    private List<Long> attachmentIds = new java.util.ArrayList<>();

    /**
     * Configuration values for this component.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", columnDefinition = "jsonb")
    private Map<String, Object> config;
}
