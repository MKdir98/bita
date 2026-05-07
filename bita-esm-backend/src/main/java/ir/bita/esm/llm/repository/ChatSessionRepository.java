package ir.bita.esm.llm.repository;

import ir.bita.esm.llm.entity.ChatSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    Page<ChatSession> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<ChatSession> findByUserIdAndActiveTrueOrderByCreatedAtDesc(Long userId);

    Optional<ChatSession> findByIdAndUserId(Long id, Long userId);

    @Query("SELECT cs FROM ChatSession cs LEFT JOIN FETCH cs.messages WHERE cs.id = :id")
    Optional<ChatSession> findByIdWithMessages(@Param("id") Long id);

    long countByUserIdAndActiveTrue(Long userId);
}
