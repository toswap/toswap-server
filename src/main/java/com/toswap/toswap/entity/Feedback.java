package com.toswap.toswap.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(name = "feedbacks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Feedback extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false, unique = true)
    private PracticeSession practiceSession;

    @Column(columnDefinition = "TEXT")
    private String transcript;

    @Column(nullable = false)
    private Short scorePronunciation;

    @Column(nullable = false)
    private Short scoreIntonation;

    @Column(nullable = false)
    private Short scoreGrammar;

    @Column(nullable = false)
    private Short scoreVocabulary;

    @Column(nullable = false)
    private Short scoreFluency;

    @Column(nullable = false)
    private Short scoreContent;

    @Column(nullable = false)
    private Short scoreOverall;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> strengths;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> improvements;

    @Column(columnDefinition = "TEXT")
    private String detailedComment;
}
