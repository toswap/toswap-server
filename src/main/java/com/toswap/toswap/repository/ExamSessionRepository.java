package com.toswap.toswap.repository;

import com.toswap.toswap.entity.ExamSession;
import com.toswap.toswap.entity.ExamSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExamSessionRepository extends JpaRepository<ExamSession, Long> {

    Optional<ExamSession> findByIdAndUserId(Long id, Long userId);

    List<ExamSession> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<ExamSession> findByUserIdAndStatus(Long userId, ExamSessionStatus status);
}
