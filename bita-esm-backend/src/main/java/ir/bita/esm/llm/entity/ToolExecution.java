package ir.bita.esm.llm.entity;

import ir.bita.esm.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Tool execution tracking entity.
 */
@Entity
@Table(name = "tool_executions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class ToolExecution extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private ChatMessage message;

    @Column(name = "tool_call_id", nullable = false, length = 100)
    private String toolCallId;

    @Column(name = "tool_name", nullable = false, length = 100)
    private String toolName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "arguments", columnDefinition = "jsonb")
    private Map<String, Object> arguments;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ToolExecutionStatus status = ToolExecutionStatus.PENDING;

    @Column(name = "requires_confirmation")
    @Builder.Default
    private boolean requiresConfirmation = false;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result", columnDefinition = "jsonb")
    private Map<String, Object> result;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    public void confirm() {
        this.status = ToolExecutionStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    public void reject() {
        this.status = ToolExecutionStatus.REJECTED;
    }

    public void markExecuted(Map<String, Object> result) {
        this.status = ToolExecutionStatus.EXECUTED;
        this.executedAt = LocalDateTime.now();
        this.result = result;
    }

    public void markFailed(String errorMessage) {
        this.status = ToolExecutionStatus.FAILED;
        this.executedAt = LocalDateTime.now();
        this.errorMessage = errorMessage;
    }
}
