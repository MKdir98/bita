package ir.bita.esm.route.entity;

import io.hypersistence.utils.hibernate.type.array.ListArrayType;
import ir.bita.esm.service.entity.ServiceEntity;
import ir.bita.esm.shared.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;
import org.hibernate.envers.Audited;

import java.util.ArrayList;
import java.util.List;

/**
 * Route entity representing a Camel route.
 */
@Entity
@Table(name = "route", indexes = {
        @Index(name = "idx_route_service", columnList = "service_id"),
        @Index(name = "idx_route_name", columnList = "name"),
        @Index(name = "idx_route_active", columnList = "is_active")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Audited
public class Route extends SoftDeletableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceEntity service;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private RouteTemplate template;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    /**
     * IDs of attachments (JAR, etc.) for this route instance.
     */
    @Type(ListArrayType.class)
    @Column(name = "attachment_ids", columnDefinition = "bigint[]")
    @Builder.Default
    private List<Long> attachmentIds = new ArrayList<>();

    /**
     * The from endpoint for this route.
     */
    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "from_endpoint_id")
    private Endpoint fromEndpoint;

    /**
     * The to endpoint for this route (destination).
     */
    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "to_endpoint_id")
    private Endpoint toEndpoint;

    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    @Builder.Default
    private List<Component> components = new ArrayList<>();

    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RouteFile> files = new ArrayList<>();

    /**
     * Adds a component to this route.
     */
    public void addComponent(Component component) {
        components.add(component);
        component.setRoute(this);
    }

    /**
     * Removes a component from this route.
     */
    public void removeComponent(Component component) {
        components.remove(component);
        component.setRoute(null);
    }

    /**
     * Adds a file to this route.
     */
    public void addFile(RouteFile file) {
        files.add(file);
        file.setRoute(this);
    }

    /**
     * Removes a file from this route.
     */
    public void removeFile(RouteFile file) {
        files.remove(file);
        file.setRoute(null);
    }
}
