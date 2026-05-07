package ir.bita.esm.client.entity;

import ir.bita.esm.shared.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

import java.util.ArrayList;
import java.util.List;

/**
 * Client entity (Aggregate Root).
 * Represents an organization that can access services.
 */
@Entity
@Table(name = "client", indexes = {
        @Index(name = "idx_client_name", columnList = "name"),
        @Index(name = "idx_client_active", columnList = "is_active, is_deleted")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Audited
public class Client extends SoftDeletableEntity {

    @Column(name = "name", unique = true, nullable = false)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone", length = 11)
    private String contactPhone;

    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<Tag> tags = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Client parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Client> children = new ArrayList<>();

    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Credential> credentials = new ArrayList<>();

    /**
     * Adds a credential to this client.
     */
    public void addCredential(Credential credential) {
        credentials.add(credential);
        credential.setClient(this);
    }

    /**
     * Removes a credential from this client.
     */
    public void removeCredential(Credential credential) {
        credentials.remove(credential);
        credential.setClient(null);
    }

    /**
     * Adds a tag to this client.
     */
    public void addTag(Tag tag) {
        tags.add(tag);
        tag.setClient(this);
    }

    /**
     * Adds a tag with key and value to this client.
     */
    public void addTag(String key, String value) {
        Tag tag = Tag.builder()
                .key(key)
                .value(value)
                .client(this)
                .build();
        tags.add(tag);
    }

    /**
     * Removes a tag from this client.
     */
    public void removeTag(Tag tag) {
        tags.remove(tag);
        tag.setClient(null);
    }

    /**
     * Clears all tags from this client.
     */
    public void clearTags() {
        tags.clear();
    }

    /**
     * Gets a tag value by key.
     */
    public String getTagValue(String key) {
        return tags.stream()
                .filter(t -> t.getKey().equals(key))
                .findFirst()
                .map(Tag::getValue)
                .orElse(null);
    }
}
