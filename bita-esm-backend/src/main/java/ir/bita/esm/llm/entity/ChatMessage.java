package ir.bita.esm.llm.entity;

import ir.bita.esm.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;

/**
 * Chat message entity.
 */
@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class ChatMessage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageRole role;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /**
     * Tool calls requested by assistant (JSON array).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tool_calls", columnDefinition = "jsonb")
    private List<Map<String, Object>> toolCalls;

    /**
     * Tool call ID this message responds to (for TOOL role).
     */
    @Column(name = "tool_call_id", length = 100)
    private String toolCallId;

    /**
     * Token count for this message.
     */
    @Column(name = "token_count")
    private Integer tokenCount;

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ToolExecution> toolExecutions;
}
