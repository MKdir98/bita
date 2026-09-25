package ir.bita.esm.route.entity;

import ir.bita.esm.service.entity.ServiceEntity;
import ir.bita.esm.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "service_groovy_config", indexes = {
        @Index(name = "idx_sgc_service", columnList = "service_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Audited
public class ServiceGroovyConfig extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", unique = true, nullable = false)
    private ServiceEntity service;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "groovy_template_id", nullable = false)
    private GroovyTemplate groovyTemplate;

    /** Filled variable values keyed by variable name. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variable_values", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> variableValues = new HashMap<>();

    /** Assembled script with all #componentName blocks inlined. ESB evaluates this. */
    @Column(name = "assembled_script", columnDefinition = "TEXT")
    private String assembledScript;
}
