package com.toswap.toswap.repository;

import com.toswap.toswap.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    Optional<Feedback> findByPracticeSessionId(Long sessionId);
}
