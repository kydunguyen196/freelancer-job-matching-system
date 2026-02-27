package com.skillbridge.user_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.skillbridge.user_service.domain.UserProfile;
import com.skillbridge.user_service.domain.UserRole;
import com.skillbridge.user_service.dto.ProfileResponse;
import com.skillbridge.user_service.dto.UpdateProfileRequest;
import com.skillbridge.user_service.repository.UserProfileRepository;
import com.skillbridge.user_service.security.JwtUserPrincipal;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @InjectMocks
    private UserProfileService userProfileService;

    @Test
    void getMyProfileShouldCreateDefaultProfileWhenMissing() {
        JwtUserPrincipal principal = new JwtUserPrincipal(12L, "freelancer@example.com", "FREELANCER");
        when(userProfileRepository.findByAuthUserId(12L)).thenReturn(Optional.empty());
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(invocation -> {
            UserProfile profile = invocation.getArgument(0);
            profile.setId(1L);
            return profile;
        });

        ProfileResponse response = userProfileService.getMyProfile(principal);

        assertThat(response.userId()).isEqualTo(12L);
        assertThat(response.email()).isEqualTo("freelancer@example.com");
        assertThat(response.role()).isEqualTo("FREELANCER");
        assertThat(response.skills()).isEmpty();
    }

    @Test
    void upsertMyProfileShouldRejectClientPayloadContainingFreelancerFields() {
        JwtUserPrincipal principal = new JwtUserPrincipal(100L, "client@example.com", "CLIENT");
        UserProfile existingProfile = profile(100L, "client@example.com", UserRole.CLIENT);
        when(userProfileRepository.findByAuthUserId(100L)).thenReturn(Optional.of(existingProfile));

        UpdateProfileRequest invalidRequest = new UpdateProfileRequest(
                List.of("java"),
                BigDecimal.valueOf(50),
                "overview",
                null
        );

        assertThatThrownBy(() -> userProfileService.upsertMyProfile(principal, invalidRequest))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void upsertMyProfileShouldNormalizeFreelancerData() {
        JwtUserPrincipal principal = new JwtUserPrincipal(7L, "dev@example.com", "FREELANCER");
        UserProfile existingProfile = profile(7L, "dev@example.com", UserRole.FREELANCER);
        when(userProfileRepository.findByAuthUserId(7L)).thenReturn(Optional.of(existingProfile));
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateProfileRequest request = new UpdateProfileRequest(
                List.of(" Java ", "Spring", "java", " "),
                BigDecimal.valueOf(35),
                "  Backend developer  ",
                null
        );

        ProfileResponse response = userProfileService.upsertMyProfile(principal, request);

        ArgumentCaptor<UserProfile> profileCaptor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).save(profileCaptor.capture());
        UserProfile saved = profileCaptor.getValue();
        assertThat(saved.getSkills()).containsExactly("Java", "Spring", "java");
        assertThat(saved.getHourlyRate()).isEqualByComparingTo("35");
        assertThat(saved.getOverview()).isEqualTo("Backend developer");

        assertThat(response.skills()).containsExactly("Java", "Spring", "java");
        assertThat(response.hourlyRate()).isEqualByComparingTo("35");
        assertThat(response.overview()).isEqualTo("Backend developer");
    }

    @Test
    void getMyProfileShouldRejectRoleMismatch() {
        JwtUserPrincipal principal = new JwtUserPrincipal(44L, "client@example.com", "CLIENT");
        UserProfile existingProfile = profile(44L, "client@example.com", UserRole.FREELANCER);
        when(userProfileRepository.findByAuthUserId(44L)).thenReturn(Optional.of(existingProfile));

        assertThatThrownBy(() -> userProfileService.getMyProfile(principal))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    private UserProfile profile(Long authUserId, String email, UserRole role) {
        UserProfile profile = new UserProfile();
        profile.setAuthUserId(authUserId);
        profile.setEmail(email);
        profile.setRole(role);
        return profile;
    }
}
