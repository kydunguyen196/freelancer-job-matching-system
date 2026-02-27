package com.skillbridge.user_service.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.skillbridge.user_service.domain.UserProfile;
import com.skillbridge.user_service.domain.UserRole;
import com.skillbridge.user_service.dto.ProfileResponse;
import com.skillbridge.user_service.dto.UpdateProfileRequest;
import com.skillbridge.user_service.repository.UserProfileRepository;
import com.skillbridge.user_service.security.JwtUserPrincipal;

@Service
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;

    public UserProfileService(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
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

    private void applyClientUpdate(UserProfile profile, UpdateProfileRequest request) {
        if (request.skills() != null || request.hourlyRate() != null || request.overview() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CLIENT profile only supports companyName");
        }
        if (request.companyName() != null) {
            String companyName = normalizeText(request.companyName());
            if (companyName == null || companyName.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "companyName must not be blank");
            }
            profile.setCompanyName(companyName);
        }
    }

    private void applyFreelancerUpdate(UserProfile profile, UpdateProfileRequest request) {
        if (request.companyName() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FREELANCER profile does not support companyName");
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
    }

    private UserProfile createDefaultProfile(JwtUserPrincipal principal) {
        UserProfile profile = new UserProfile();
        profile.setAuthUserId(principal.userId());
        profile.setEmail(principal.email());
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

    private ProfileResponse toResponse(UserProfile profile) {
        return new ProfileResponse(
                profile.getAuthUserId(),
                profile.getEmail(),
                profile.getRole().name(),
                List.copyOf(profile.getSkills()),
                profile.getHourlyRate(),
                profile.getOverview(),
                profile.getCompanyName()
        );
    }
}
