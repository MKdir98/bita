package ir.bita.esm.route.entity;

import ir.bita.esm.service.entity.ServiceEntity;
import ir.bita.esm.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;

/**
 * One immutable minor version of a service's configuration (e.g. 1.6, 1.7 of service v1).
 * A major version (v1, v2) is a separate service with its own address; minor versions live
 * on the same address and any of them can be made active again — that is the rollback.
 * {@link ServiceGroovyConfig} always holds a copy of the active one, which is what ESB loads.
 */
@Entity
@Table(name = "service_config_version",
        uniqueConstraints = @UniqueConstraint(name = "uk_scv_service_minor", columnNames = {"service_id", "minor"}),
        indexes = @Index(name = "idx_scv_service", columnList = "service_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceConfigVersion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false, updatable = false)
    private ServiceEntity service;

    /** Minor number within the service's major version; the label is {@code <major>.<minor>}. */
    @Column(name = "minor", nullable = false, updatable = false)
    private int minor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "groovy_template_id", nullable = false, updatable = false)
    private GroovyTemplate groovyTemplate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variable_values", columnDefinition = "jsonb", updatable = false)
    @Builder.Default
    private Map<String, Object> variableValues = new HashMap<>();

    @Column(name = "assembled_script", columnDefinition = "TEXT", updatable = false)
    private String assembledScript;

    @Column(name = "active", nullable = false)
    private boolean active;

    /** Why this version exists (e.g. "rollback to 1.6", or the change the user asked for). */
    @Column(name = "note", length = 500, updatable = false)
    private String note;

    public String label() {
        String major = service == null || service.getServiceVersion() == null
                ? "1" : service.getServiceVersion().replaceAll("\\D", "");
        return (major.isEmpty() ? "1" : major) + "." + minor;
    }
}
