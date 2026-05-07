package ir.bita.esm.route.entity;

import ir.bita.common.domain.FileType;
import ir.bita.esm.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

/**
 * Route File entity for files attached to routes (WSDL, XSD, etc.).
 */
@Entity
@Table(name = "route_file", indexes = {
        @Index(name = "idx_route_file_route", columnList = "route_id"),
        @Index(name = "idx_route_file_type", columnList = "file_type")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Audited
public class RouteFile extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @Column(name = "filename", nullable = false)
    private String filename;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 20)
    private FileType fileType;

    /**
     * Size of the file in bytes.
     */
    @Column(name = "file_size")
    private Long fileSize;

    /**
     * MIME type of the file.
     */
    @Column(name = "mime_type", length = 100)
    private String mimeType;

    /**
     * Storage path or URL for the file.
     */
    @Column(name = "storage_path", nullable = false)
    private String storagePath;

    /**
     * SHA-256 hash of the file content.
     */
    @Column(name = "content_hash", length = 64)
    private String contentHash;

    /**
     * Original file content (for small files like WSDL, XSD).
     * Large files should use storage_path instead.
     */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;
}
