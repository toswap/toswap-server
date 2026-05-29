package com.toswap.toswap.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "practice_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PracticeSession extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_session_id")
    private ExamSession examSession;

    @Column(length = 500)
    private String audioPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PracticeSessionStatus status = PracticeSessionStatus.PENDING;

    public void updateStatus(PracticeSessionStatus status) {
        this.status = status;
    }

    public void updateAudioPath(String audioPath) {
        this.audioPath = audioPath;
    }
}
