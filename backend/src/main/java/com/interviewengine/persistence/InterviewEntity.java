package com.interviewengine.persistence;

import com.interviewengine.domain.InterviewStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA-сущность интервью. Хранит сырой транскрипт и результат оценки в JSON-полях —
 * структуры этих полей соответствуют доменным records {@code Transcript} и
 * {@code EvaluationResult}, сериализуются на уровне сервиса.
 *
 * <p>Доменная модель сознательно не загрязняется аннотациями JPA (§11 CLAUDE.md) —
 * эта entity живёт в отдельном слое {@code persistence}.
 */
@Entity
@Table(name = "interview")
public class InterviewEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InterviewStatus status;

    @Column(name = "transcript_json", nullable = false, columnDefinition = "text")
    private String transcriptJson;

    @Column(name = "result_json", columnDefinition = "text")
    private String resultJson;

    /**
     * Кэш оценок по каждому индикатору: {@code Map<indicatorId, IndicatorEvaluation>}.
     * Позволяет повторному /evaluate пропускать LLM-вызов для уже оценённых индикаторов.
     */
    @Column(name = "eval_cache_json", columnDefinition = "text")
    private String evalCacheJson;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    public InterviewStatus getStatus() { return status; }
    public void setStatus(InterviewStatus status) { this.status = status; }

    public String getTranscriptJson() { return transcriptJson; }
    public void setTranscriptJson(String transcriptJson) { this.transcriptJson = transcriptJson; }

    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }

    public String getEvalCacheJson() { return evalCacheJson; }
    public void setEvalCacheJson(String evalCacheJson) { this.evalCacheJson = evalCacheJson; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
