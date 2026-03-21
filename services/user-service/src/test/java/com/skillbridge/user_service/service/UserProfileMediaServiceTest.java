package com.skillbridge.user_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.skillbridge.user_service.domain.UserProfile;
import com.skillbridge.user_service.domain.UserRole;
import com.skillbridge.user_service.repository.UserProfileRepository;
import com.skillbridge.user_service.security.JwtUserPrincipal;

@ExtendWith(MockitoExtension.class)
class UserProfileMediaServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    private UserProfileService userProfileService;

    @BeforeEach
    void setUp() {
        userProfileService = new UserProfileService(
                userProfileRepository,
                3,
                "image/png,image/jpeg"
        );
    }

    @Test
    void uploadAvatar_shouldRejectUnsupportedContentType() {
        UserProfile profile = buildProfile(1L, UserRole.FREELANCER);
        when(userProfileRepository.findByAuthUserId(1L)).thenReturn(Optional.of(profile));

        MockMultipartFile file = new MockMultipartFile("file", "avatar.txt", "text/plain", "bad".getBytes());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> userProfileService.uploadAvatar(new JwtUserPrincipal(1L, "freelancer@test.com", "FREELANCER"), file)
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void uploadCompanyLogo_shouldRejectFreelancerRole() {
        UserProfile profile = buildProfile(2L, UserRole.FREELANCER);
        when(userProfileRepository.findByAuthUserId(2L)).thenReturn(Optional.of(profile));

        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", "img".getBytes());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> userProfileService.uploadCompanyLogo(new JwtUserPrincipal(2L, "freelancer@test.com", "FREELANCER"), file)
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    private UserProfile buildProfile(Long userId, UserRole role) {
        UserProfile profile = new UserProfile();
        profile.setAuthUserId(userId);
        profile.setRole(role);
        profile.setEmail("user@test.com");
        return profile;
    }
}
