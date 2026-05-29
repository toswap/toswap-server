package com.toswap.toswap.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "exam_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ExamSession extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ExamSessionStatus status = ExamSessionStatus.IN_PROGRESS;

    private Short predictedScore;

    private Short predictedLevel;

    private LocalDateTime completedAt;

    public void startEvaluating() {
        this.status = ExamSessionStatus.EVALUATING;
    }

    public void complete(Short predictedScore, Short predictedLevel) {
        this.status = ExamSessionStatus.COMPLETED;
        this.predictedScore = predictedScore;
        this.predictedLevel = predictedLevel;
        this.completedAt = LocalDateTime.now();
    }

    public void abandon() {
        this.status = ExamSessionStatus.ABANDONED;
        this.completedAt = LocalDateTime.now();
    }
}
