package com.skillbridge.user_service.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "user_profiles",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_profiles_auth_user_id", columnNames = "auth_user_id")
        }
)
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "auth_user_id", nullable = false)
    private Long authUserId;

    @Column(nullable = false, length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserRole role;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_profile_skills", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "skill", length = 80, nullable = false)
    private List<String> skills = new ArrayList<>();

    @Column(precision = 12, scale = 2)
    private BigDecimal hourlyRate;

    @Column(length = 4000)
    private String overview;

    @Column(length = 255)
    private String companyName;

    @Column(length = 255)
    private String contactEmail;

    @Column(length = 32)
    private String phoneNumber;

    @Column(length = 255)
    private String address;

    @Column(length = 255)
    private String companyAddress;

    @Column(length = 255)
    private String resumeFileName;

    @Column(length = 120)
    private String resumeContentType;

    @Lob
    @Column
    private byte[] resumeData;

    @Column(length = 255)
    private String avatarFileName;

    @Column(length = 120)
    private String avatarContentType;

    @Lob
    @Column
    private byte[] avatarData;

    private Instant avatarUploadedAt;

    @Column(length = 255)
    private String companyLogoFileName;

    @Column(length = 120)
    private String companyLogoContentType;

    @Lob
    @Column
    private byte[] companyLogoData;

    private Instant companyLogoUploadedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
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

    public Long getAuthUserId() {
        return authUserId;
    }

    public void setAuthUserId(Long authUserId) {
        this.authUserId = authUserId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public List<String> getSkills() {
        return skills;
    }

    public void setSkills(List<String> skills) {
        this.skills = skills == null ? new ArrayList<>() : new ArrayList<>(skills);
    }

    public BigDecimal getHourlyRate() {
        return hourlyRate;
    }

    public void setHourlyRate(BigDecimal hourlyRate) {
        this.hourlyRate = hourlyRate;
    }

    public String getOverview() {
        return overview;
    }

    public void setOverview(String overview) {
        this.overview = overview;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCompanyAddress() {
        return companyAddress;
    }

    public void setCompanyAddress(String companyAddress) {
        this.companyAddress = companyAddress;
    }

    public String getResumeFileName() {
        return resumeFileName;
    }

    public void setResumeFileName(String resumeFileName) {
        this.resumeFileName = resumeFileName;
    }

    public String getResumeContentType() {
        return resumeContentType;
    }

    public void setResumeContentType(String resumeContentType) {
        this.resumeContentType = resumeContentType;
    }

    public byte[] getResumeData() {
        return resumeData == null ? null : resumeData.clone();
    }

    public void setResumeData(byte[] resumeData) {
        this.resumeData = resumeData == null ? null : resumeData.clone();
    }

    public String getAvatarFileName() {
        return avatarFileName;
    }

    public void setAvatarFileName(String avatarFileName) {
        this.avatarFileName = avatarFileName;
    }

    public String getAvatarContentType() {
        return avatarContentType;
    }

    public void setAvatarContentType(String avatarContentType) {
        this.avatarContentType = avatarContentType;
    }

    public byte[] getAvatarData() {
        return avatarData == null ? null : avatarData.clone();
    }

    public void setAvatarData(byte[] avatarData) {
        this.avatarData = avatarData == null ? null : avatarData.clone();
    }

    public Instant getAvatarUploadedAt() {
        return avatarUploadedAt;
    }

    public void setAvatarUploadedAt(Instant avatarUploadedAt) {
        this.avatarUploadedAt = avatarUploadedAt;
    }

    public String getCompanyLogoFileName() {
        return companyLogoFileName;
    }

    public void setCompanyLogoFileName(String companyLogoFileName) {
        this.companyLogoFileName = companyLogoFileName;
    }

    public String getCompanyLogoContentType() {
        return companyLogoContentType;
    }

    public void setCompanyLogoContentType(String companyLogoContentType) {
        this.companyLogoContentType = companyLogoContentType;
    }

    public byte[] getCompanyLogoData() {
        return companyLogoData == null ? null : companyLogoData.clone();
    }

    public void setCompanyLogoData(byte[] companyLogoData) {
        this.companyLogoData = companyLogoData == null ? null : companyLogoData.clone();
    }

    public Instant getCompanyLogoUploadedAt() {
        return companyLogoUploadedAt;
    }

    public void setCompanyLogoUploadedAt(Instant companyLogoUploadedAt) {
        this.companyLogoUploadedAt = companyLogoUploadedAt;
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
