package ir.bita.esm.llm.repository;

import ir.bita.esm.llm.entity.ToolExecution;
import ir.bita.esm.llm.entity.ToolExecutionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ToolExecutionRepository extends JpaRepository<ToolExecution, Long> {

    Optional<ToolExecution> findByToolCallId(String toolCallId);

    List<ToolExecution> findByMessageIdAndStatus(Long messageId, ToolExecutionStatus status);

    @Query("SELECT te FROM ToolExecution te WHERE te.message.session.id = :sessionId AND te.status = :status")
    List<ToolExecution> findBySessionIdAndStatus(@Param("sessionId") Long sessionId, @Param("status") ToolExecutionStatus status);

    boolean existsByMessageSessionIdAndStatus(Long sessionId, ToolExecutionStatus status);
}
