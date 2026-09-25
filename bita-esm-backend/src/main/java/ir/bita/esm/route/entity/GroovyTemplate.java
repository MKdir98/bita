package ir.bita.esm.route.entity;

import ir.bita.common.domain.VariableType;
import ir.bita.esm.shared.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "groovy_template", indexes = {
        @Index(name = "idx_groovy_template_name", columnList = "name")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Audited
public class GroovyTemplate extends SoftDeletableEntity {

    @Column(name = "name", unique = true, nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "script_text", columnDefinition = "TEXT", nullable = false)
    private String scriptText;

    /** Auto-scanned variable declarations from scriptText. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variables", columnDefinition = "jsonb")
    @Builder.Default
    private List<VariableMetadata> variables = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariableMetadata {
        private String name;
        private String label;
        private String description;
        private VariableType type;
        private boolean required;
        private String defaultValue;
        /** K8s Ingress path — only used when type == PORT. */
        private String path;
    }
}
