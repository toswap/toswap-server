package com.toswap.toswap.repository;

import com.toswap.toswap.entity.PracticeSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PracticeSessionRepository extends JpaRepository<PracticeSession, Long> {

    Optional<PracticeSession> findByIdAndUserId(Long id, Long userId);

    List<PracticeSession> findByUserIdAndExamSessionIsNullOrderByCreatedAtDesc(Long userId);

    List<PracticeSession> findByExamSessionId(Long examSessionId);
}
