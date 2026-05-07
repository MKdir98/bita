package ir.bita.esm.audit.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Custom revision entity for audit trail.
 */
@Entity
@Table(name = "audit_revisions")
@RevisionEntity(AuditRevisionListener.class)
@Getter
@Setter
public class AuditRevisionEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @RevisionNumber
    private Long id;

    @RevisionTimestamp
    @Column(name = "revision_timestamp")
    private long timestamp;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    public LocalDateTime getRevisionDate() {
        return LocalDateTime.ofEpochSecond(timestamp / 1000, 0, java.time.ZoneOffset.UTC);
    }
}
