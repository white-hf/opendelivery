package com.hf.easydelivery.integration.outbox.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "callback_attempt")
public class CallbackAttemptEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "outbox_event_id", nullable = false)
    private long outboxEventId;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Column(name = "request_url", nullable = false, length = 1000)
    private String requestUrl;

    @Column(name = "request_sha256", nullable = false, length = 64)
    private String requestSha256;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_body_excerpt", length = 2000)
    private String responseBodyExcerpt;

    @Column(name = "outcome", nullable = false, length = 24)
    private String outcome;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public long getOutboxEventId() { return outboxEventId; }
    public void setOutboxEventId(long outboxEventId) { this.outboxEventId = outboxEventId; }

    public int getAttemptNo() { return attemptNo; }
    public void setAttemptNo(int attemptNo) { this.attemptNo = attemptNo; }

    public String getRequestUrl() { return requestUrl; }
    public void setRequestUrl(String requestUrl) { this.requestUrl = requestUrl; }

    public String getRequestSha256() { return requestSha256; }
    public void setRequestSha256(String requestSha256) { this.requestSha256 = requestSha256; }

    public Integer getResponseStatus() { return responseStatus; }
    public void setResponseStatus(Integer responseStatus) { this.responseStatus = responseStatus; }

    public String getResponseBodyExcerpt() { return responseBodyExcerpt; }
    public void setResponseBodyExcerpt(String responseBodyExcerpt) { this.responseBodyExcerpt = responseBodyExcerpt; }

    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
