package ir.bita.esm.client.entity;

import ir.bita.esm.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

/**
 * Tag entity for storing key-value pairs associated with clients.
 * Examples: type=private, national_id=002109
 */
@Entity
@Table(name = "client_tag", indexes = {
        @Index(name = "idx_tag_client_key", columnList = "client_id, tag_key"),
        @Index(name = "idx_tag_key", columnList = "tag_key")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Audited
public class Tag extends BaseEntity {

    @Column(name = "tag_key", nullable = false, length = 100)
    private String key;

    @Column(name = "tag_value", nullable = false, length = 500)
    private String value;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;
}
