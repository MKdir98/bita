package ir.bita.esm.route.entity;

import ir.bita.common.domain.AttachmentFileType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Attachment entity for files attached to templates and instances (JAR, PROPERTIES, XML, etc.).
 * Unified table for all attachment types.
 */
@Entity
@Table(name = "attachment", indexes = {
        @Index(name = "idx_attachment_file_type", columnList = "file_type")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false)
    private AttachmentFileType fileType = AttachmentFileType.JAR;

    /**
     * Path to the file on the filesystem (e.g. /data/bita/attachments/{uuid}.jar).
     */
    @Column(name = "file_path", nullable = false, length = 1000)
    private String filePath;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
