package com.skillbridge.proposal_service.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "proposals",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_proposals_job_freelancer",
                        columnNames = {"job_id", "freelancer_id"}
                )
        }
)
public class Proposal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "client_id")
    private Long clientId;

    @Column(name = "freelancer_id", nullable = false)
    private Long freelancerId;

    @Column(nullable = false, length = 255)
    private String freelancerEmail;

    @Column(nullable = false, length = 4000)
    private String coverLetter;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer durationDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProposalStatus status;

    @Column(name = "reviewed_by_client_id")
    private Long reviewedByClientId;

    private Instant reviewedAt;

    @Column(name = "rejected_by_client_id")
    private Long rejectedByClientId;

    private Instant rejectedAt;

    @Column(length = 2000)
    private String feedbackMessage;

    private Instant interviewScheduledAt;

    private Instant interviewEndsAt;

    @Column(length = 512)
    private String interviewMeetingLink;

    @Column(length = 2000)
    private String interviewNotes;

    @Column(length = 255)
    private String googleEventId;

    @Column(name = "accepted_by_client_id")
    private Long acceptedByClientId;

    private Instant acceptedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = ProposalStatus.PENDING;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getJobId() {
        return jobId;
    }

    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }

    public Long getClientId() {
        return clientId;
    }

    public void setClientId(Long clientId) {
        this.clientId = clientId;
    }

    public Long getFreelancerId() {
        return freelancerId;
    }

    public void setFreelancerId(Long freelancerId) {
        this.freelancerId = freelancerId;
    }

    public String getFreelancerEmail() {
        return freelancerEmail;
    }

    public void setFreelancerEmail(String freelancerEmail) {
        this.freelancerEmail = freelancerEmail;
    }

    public String getCoverLetter() {
        return coverLetter;
    }

    public void setCoverLetter(String coverLetter) {
        this.coverLetter = coverLetter;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getDurationDays() {
        return durationDays;
    }

    public void setDurationDays(Integer durationDays) {
        this.durationDays = durationDays;
    }

    public ProposalStatus getStatus() {
        return status;
    }

    public void setStatus(ProposalStatus status) {
        this.status = status;
    }

    public Long getReviewedByClientId() {
        return reviewedByClientId;
    }

    public void setReviewedByClientId(Long reviewedByClientId) {
        this.reviewedByClientId = reviewedByClientId;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public Long getRejectedByClientId() {
        return rejectedByClientId;
    }

    public void setRejectedByClientId(Long rejectedByClientId) {
        this.rejectedByClientId = rejectedByClientId;
    }

    public Instant getRejectedAt() {
        return rejectedAt;
    }

    public void setRejectedAt(Instant rejectedAt) {
        this.rejectedAt = rejectedAt;
    }

    public String getFeedbackMessage() {
        return feedbackMessage;
    }

    public void setFeedbackMessage(String feedbackMessage) {
        this.feedbackMessage = feedbackMessage;
    }

    public Instant getInterviewScheduledAt() {
        return interviewScheduledAt;
    }

    public void setInterviewScheduledAt(Instant interviewScheduledAt) {
        this.interviewScheduledAt = interviewScheduledAt;
    }

    public Instant getInterviewEndsAt() {
        return interviewEndsAt;
    }

    public void setInterviewEndsAt(Instant interviewEndsAt) {
        this.interviewEndsAt = interviewEndsAt;
    }

    public String getInterviewMeetingLink() {
        return interviewMeetingLink;
    }

    public void setInterviewMeetingLink(String interviewMeetingLink) {
        this.interviewMeetingLink = interviewMeetingLink;
    }

    public String getInterviewNotes() {
        return interviewNotes;
    }

    public void setInterviewNotes(String interviewNotes) {
        this.interviewNotes = interviewNotes;
    }

    public String getGoogleEventId() {
        return googleEventId;
    }

    public void setGoogleEventId(String googleEventId) {
        this.googleEventId = googleEventId;
    }

    public Long getAcceptedByClientId() {
        return acceptedByClientId;
    }

    public void setAcceptedByClientId(Long acceptedByClientId) {
        this.acceptedByClientId = acceptedByClientId;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
