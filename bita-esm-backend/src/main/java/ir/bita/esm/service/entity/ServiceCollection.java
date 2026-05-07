package ir.bita.esm.service.entity;

import ir.bita.esm.shared.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

import java.util.ArrayList;
import java.util.List;

/**
 * Service Collection entity (Aggregate Root).
 * Groups related services under a common base path.
 */
@Entity
@Table(name = "service_collection", indexes = {
        @Index(name = "idx_collection_name", columnList = "name"),
        @Index(name = "idx_collection_base_path", columnList = "base_path")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Audited
public class ServiceCollection extends SoftDeletableEntity {

    @Column(name = "name", unique = true, nullable = false, length = 100)
    private String name;

    /**
     * Base path for all services in this collection.
     * Example: "/esb/payment"
     */
    @Column(name = "base_path", unique = true, nullable = false, length = 100)
    private String basePath;

    @Column(name = "description", length = 500)
    private String description;

    @OneToMany(mappedBy = "collection", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ServiceEntity> services = new ArrayList<>();

    /**
     * Adds a service to this collection.
     */
    public void addService(ServiceEntity service) {
        services.add(service);
        service.setCollection(this);
    }

    /**
     * Removes a service from this collection.
     */
    public void removeService(ServiceEntity service) {
        services.remove(service);
        service.setCollection(null);
    }

    /**
     * Gets the full path for a service version.
     * Example: /esb/payment/1.0
     */
    public String getFullPath(String version) {
        return basePath + "/" + version;
    }
}
