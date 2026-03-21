package com.skillbridge.user_service.service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.skillbridge.user_service.domain.UserProfile;
import com.skillbridge.user_service.domain.UserRole;
import com.skillbridge.user_service.dto.ProfileResponse;
import com.skillbridge.user_service.dto.ProfileMediaAssetResponse;
import com.skillbridge.user_service.dto.ProfileMediaResponse;
import com.skillbridge.user_service.dto.UpdateProfileRequest;
import com.skillbridge.user_service.repository.UserProfileRepository;
import com.skillbridge.user_service.security.JwtUserPrincipal;

@Service
public class UserProfileService {

    private static final long MAX_RESUME_SIZE_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_RESUME_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/x-msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9+()\\-\\s]{7,20}$");

    private final UserProfileRepository userProfileRepository;
    private final long maxProfileMediaSizeBytes;
    private final Set<String> allowedProfileMediaContentTypes;

    public UserProfileService(
            UserProfileRepository userProfileRepository,
            @Value("${app.media.max-file-size-mb:3}") int maxMediaFileSizeMb,
            @Value("${app.media.allowed-content-types:image/jpeg,image/png,image/webp,image/gif}") String allowedContentTypes
    ) {
        this.userProfileRepository = userProfileRepository;
        this.maxProfileMediaSizeBytes = Math.max(1, maxMediaFileSizeMb) * 1024L * 1024L;
        this.allowedProfileMediaContentTypes = parseAllowedMediaTypes(allowedContentTypes);
    }

    @Transactional
    public ProfileResponse getMyProfile(JwtUserPrincipal principal) {
        UserProfile profile = userProfileRepository.findByAuthUserId(principal.userId())
                .orElseGet(() -> userProfileRepository.save(createDefaultProfile(principal)));
        ensureRoleConsistency(profile, principal);
        profile.setEmail(principal.email());
        return toResponse(profile);
    }

    @Transactional
    public ProfileResponse upsertMyProfile(JwtUserPrincipal principal, UpdateProfileRequest request) {
        UserProfile profile = userProfileRepository.findByAuthUserId(principal.userId())
                .orElseGet(() -> createDefaultProfile(principal));
        ensureRoleConsistency(profile, principal);
        profile.setEmail(principal.email());

        if (profile.getRole() == UserRole.CLIENT) {
            applyClientUpdate(profile, request);
        } else if (profile.getRole() == UserRole.FREELANCER) {
            applyFreelancerUpdate(profile, request);
        }

        UserProfile savedProfile = userProfileRepository.save(profile);
        return toResponse(savedProfile);
    }

    @Transactional
    public ProfileResponse uploadMyResume(JwtUserPrincipal principal, MultipartFile file) {
        UserProfile profile = userProfileRepository.findByAuthUserId(principal.userId())
                .orElseGet(() -> createDefaultProfile(principal));
        ensureRoleConsistency(profile, principal);
        profile.setEmail(principal.email());

        if (profile.getRole() != UserRole.FREELANCER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only FREELANCER accounts can upload resume");
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resume file is required");
        }
        if (file.getSize() > MAX_RESUME_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resume file must be 5MB or smaller");
        }

        String fileName = normalizeFileName(file.getOriginalFilename());
        if (fileName == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resume file name is invalid");
        }

        String contentType = normalizeText(file.getContentType());
        if (contentType == null) {
            contentType = "application/octet-stream";
        }
        if (!isAllowedResumeType(contentType, fileName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resume must be a PDF or Word document");
        }

        try {
            profile.setResumeData(file.getBytes());
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not process resume file");
        }
        profile.setResumeFileName(fileName);
        profile.setResumeContentType(contentType);

        UserProfile savedProfile = userProfileRepository.save(profile);
        return toResponse(savedProfile);
    }

    @Transactional
    public ProfileMediaResponse getMyMedia(JwtUserPrincipal principal) {
        UserProfile profile = userProfileRepository.findByAuthUserId(principal.userId())
                .orElseGet(() -> userProfileRepository.save(createDefaultProfile(principal)));
        ensureRoleConsistency(profile, principal);
        profile.setEmail(principal.email());
        return toMediaResponse(profile);
    }

    @Transactional
    public ProfileMediaResponse uploadAvatar(JwtUserPrincipal principal, MultipartFile file) {
        UserProfile profile = userProfileRepository.findByAuthUserId(principal.userId())
                .orElseGet(() -> createDefaultProfile(principal));
        ensureRoleConsistency(profile, principal);
        profile.setEmail(principal.email());

        MediaUploadPayload payload = normalizeProfileMedia(file, "avatar");
        profile.setAvatarFileName(payload.fileName());
        profile.setAvatarContentType(payload.contentType());
        profile.setAvatarData(payload.bytes());
        profile.setAvatarUploadedAt(Instant.now());

        return toMediaResponse(userProfileRepository.save(profile));
    }

    @Transactional
    public ProfileMediaResponse deleteAvatar(JwtUserPrincipal principal) {
        UserProfile profile = userProfileRepository.findByAuthUserId(principal.userId())
                .orElseGet(() -> createDefaultProfile(principal));
        ensureRoleConsistency(profile, principal);
        profile.setEmail(principal.email());

        profile.setAvatarFileName(null);
        profile.setAvatarContentType(null);
        profile.setAvatarData(null);
        profile.setAvatarUploadedAt(null);
        return toMediaResponse(userProfileRepository.save(profile));
    }

    @Transactional
    public ProfileMediaResponse uploadCompanyLogo(JwtUserPrincipal principal, MultipartFile file) {
        UserProfile profile = userProfileRepository.findByAuthUserId(principal.userId())
                .orElseGet(() -> createDefaultProfile(principal));
        ensureRoleConsistency(profile, principal);
        profile.setEmail(principal.email());

        if (profile.getRole() != UserRole.CLIENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only CLIENT accounts can upload company logo");
        }

        MediaUploadPayload payload = normalizeProfileMedia(file, "company logo");
        profile.setCompanyLogoFileName(payload.fileName());
        profile.setCompanyLogoContentType(payload.contentType());
        profile.setCompanyLogoData(payload.bytes());
        profile.setCompanyLogoUploadedAt(Instant.now());

        return toMediaResponse(userProfileRepository.save(profile));
    }

    @Transactional
    public ProfileMediaResponse deleteCompanyLogo(JwtUserPrincipal principal) {
        UserProfile profile = userProfileRepository.findByAuthUserId(principal.userId())
                .orElseGet(() -> createDefaultProfile(principal));
        ensureRoleConsistency(profile, principal);
        profile.setEmail(principal.email());

        if (profile.getRole() != UserRole.CLIENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only CLIENT accounts can delete company logo");
        }

        profile.setCompanyLogoFileName(null);
        profile.setCompanyLogoContentType(null);
        profile.setCompanyLogoData(null);
        profile.setCompanyLogoUploadedAt(null);
        return toMediaResponse(userProfileRepository.save(profile));
    }

    @Transactional(readOnly = true)
    public DownloadedMedia downloadAvatar(JwtUserPrincipal principal) {
        UserProfile profile = userProfileRepository.findByAuthUserId(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));
        ensureRoleConsistency(profile, principal);
        if (profile.getAvatarData() == null || profile.getAvatarFileName() == null || profile.getAvatarContentType() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Avatar not found");
        }
        return new DownloadedMedia(profile.getAvatarFileName(), profile.getAvatarContentType(), profile.getAvatarData());
    }

    @Transactional(readOnly = true)
    public DownloadedMedia downloadCompanyLogo(JwtUserPrincipal principal) {
        UserProfile profile = userProfileRepository.findByAuthUserId(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));
        ensureRoleConsistency(profile, principal);
        if (profile.getRole() != UserRole.CLIENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only CLIENT accounts can access company logo");
        }
        if (profile.getCompanyLogoData() == null || profile.getCompanyLogoFileName() == null || profile.getCompanyLogoContentType() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Company logo not found");
        }
        return new DownloadedMedia(profile.getCompanyLogoFileName(), profile.getCompanyLogoContentType(), profile.getCompanyLogoData());
    }

    private void applyClientUpdate(UserProfile profile, UpdateProfileRequest request) {
        if (request.skills() != null || request.hourlyRate() != null || request.overview() != null || request.address() != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "CLIENT profile does not support skills, hourlyRate, overview, or address"
            );
        }
        if (request.companyName() != null) {
            profile.setCompanyName(normalizeText(request.companyName()));
        }
        if (request.companyAddress() != null) {
            profile.setCompanyAddress(normalizeText(request.companyAddress()));
        }
        if (request.contactEmail() != null) {
            profile.setContactEmail(normalizeEmail(request.contactEmail()));
        }
        if (request.phoneNumber() != null) {
            profile.setPhoneNumber(normalizePhoneNumber(request.phoneNumber()));
        }
    }

    private void applyFreelancerUpdate(UserProfile profile, UpdateProfileRequest request) {
        if (request.companyName() != null || request.companyAddress() != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "FREELANCER profile does not support companyName or companyAddress"
            );
        }
        if (request.skills() != null) {
            profile.setSkills(normalizeSkills(request.skills()));
        }
        if (request.hourlyRate() != null) {
            profile.setHourlyRate(request.hourlyRate());
        }
        if (request.overview() != null) {
            profile.setOverview(normalizeText(request.overview()));
        }
        if (request.contactEmail() != null) {
            profile.setContactEmail(normalizeEmail(request.contactEmail()));
        }
        if (request.phoneNumber() != null) {
            profile.setPhoneNumber(normalizePhoneNumber(request.phoneNumber()));
        }
        if (request.address() != null) {
            profile.setAddress(normalizeText(request.address()));
        }
    }

    private UserProfile createDefaultProfile(JwtUserPrincipal principal) {
        UserProfile profile = new UserProfile();
        profile.setAuthUserId(principal.userId());
        profile.setEmail(principal.email());
        profile.setContactEmail(principal.email());
        profile.setRole(parseRole(principal.role()));
        profile.setSkills(new ArrayList<>());
        return profile;
    }

    private void ensureRoleConsistency(UserProfile profile, JwtUserPrincipal principal) {
        UserRole tokenRole = parseRole(principal.role());
        if (profile.getRole() != tokenRole) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Token role does not match profile role");
        }
    }

    private UserRole parseRole(String roleText) {
        try {
            return UserRole.valueOf(roleText.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unsupported role in token");
        }
    }

    private List<String> normalizeSkills(List<String> skills) {
        return skills.stream()
                .map(this::normalizeText)
                .filter(Objects::nonNull)
                .filter(skill -> !skill.isBlank())
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private String normalizeText(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeEmail(String email) {
        String normalized = normalizeText(email);
        if (normalized == null) {
            return null;
        }
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "contactEmail must be a valid email address");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizePhoneNumber(String phoneNumber) {
        String normalized = normalizeText(phoneNumber);
        if (normalized == null) {
            return null;
        }
        if (!PHONE_PATTERN.matcher(normalized).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "phoneNumber has invalid format");
        }
        return normalized;
    }

    private String normalizeFileName(String originalFileName) {
        String normalized = normalizeText(originalFileName);
        if (normalized == null) {
            return null;
        }
        String trimmedPath = normalized.replace('\\', '/');
        String baseName = trimmedPath.substring(trimmedPath.lastIndexOf('/') + 1).trim();
        if (baseName.isEmpty()) {
            return null;
        }
        return baseName.replaceAll("[\\r\\n\"]", "_");
    }

    private boolean isAllowedResumeType(String contentType, String fileName) {
        if (ALLOWED_RESUME_CONTENT_TYPES.contains(contentType)) {
            return true;
        }
        return "application/octet-stream".equals(contentType) && hasAllowedResumeExtension(fileName);
    }

    private boolean hasAllowedResumeExtension(String fileName) {
        String normalized = fileName.toLowerCase(Locale.ROOT);
        return normalized.endsWith(".pdf") || normalized.endsWith(".doc") || normalized.endsWith(".docx");
    }

    private MediaUploadPayload normalizeProfileMedia(MultipartFile file, String mediaName) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mediaName + " file is required");
        }
        if (file.getSize() > maxProfileMediaSizeBytes) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    mediaName + " file exceeds allowed size of " + (maxProfileMediaSizeBytes / (1024 * 1024)) + "MB"
            );
        }

        String fileName = normalizeFileName(file.getOriginalFilename());
        if (fileName == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mediaName + " file name is invalid");
        }

        String contentType = normalizeText(file.getContentType());
        if (contentType == null) {
            contentType = "application/octet-stream";
        }
        if (!allowedProfileMediaContentTypes.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mediaName + " content type is not supported");
        }

        try {
            return new MediaUploadPayload(fileName, contentType, file.getBytes());
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not process " + mediaName + " file");
        }
    }

    private Set<String> parseAllowedMediaTypes(String rawAllowedTypes) {
        if (rawAllowedTypes == null || rawAllowedTypes.isBlank()) {
            return Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
        }
        return java.util.Arrays.stream(rawAllowedTypes.split(","))
                .map(type -> type == null ? null : type.trim().toLowerCase(Locale.ROOT))
                .filter(type -> type != null && !type.isBlank())
                .collect(Collectors.toSet());
    }

    private ProfileMediaResponse toMediaResponse(UserProfile profile) {
        return new ProfileMediaResponse(
                profile.getAuthUserId(),
                toMediaAsset("AVATAR", profile.getAvatarFileName(), profile.getAvatarContentType(), profile.getAvatarData(), profile.getAvatarUploadedAt(), "/users/me/media/avatar/download"),
                toMediaAsset("COMPANY_LOGO", profile.getCompanyLogoFileName(), profile.getCompanyLogoContentType(), profile.getCompanyLogoData(), profile.getCompanyLogoUploadedAt(), "/users/me/media/company-logo/download")
        );
    }

    private ProfileMediaAssetResponse toMediaAsset(
            String type,
            String fileName,
            String contentType,
            byte[] data,
            Instant uploadedAt,
            String downloadUrl
    ) {
        if (fileName == null || contentType == null || data == null) {
            return null;
        }
        return new ProfileMediaAssetResponse(type, fileName, contentType, data.length, uploadedAt, downloadUrl);
    }

    private ProfileResponse toResponse(UserProfile profile) {
        return new ProfileResponse(
                profile.getAuthUserId(),
                profile.getEmail(),
                profile.getRole().name(),
                List.copyOf(profile.getSkills()),
                profile.getHourlyRate(),
                profile.getOverview(),
                profile.getCompanyName(),
                profile.getContactEmail(),
                profile.getPhoneNumber(),
                profile.getAddress(),
                profile.getCompanyAddress(),
                profile.getResumeFileName(),
                profile.getAvatarFileName() == null ? null : "/users/me/media/avatar/download",
                profile.getCompanyLogoFileName() == null ? null : "/users/me/media/company-logo/download"
        );
    }

    public record DownloadedMedia(
            String fileName,
            String contentType,
            byte[] content
    ) {
    }

    private record MediaUploadPayload(
            String fileName,
            String contentType,
            byte[] bytes
    ) {
    }
}
