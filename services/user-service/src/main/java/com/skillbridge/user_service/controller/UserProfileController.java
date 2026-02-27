package com.skillbridge.user_service.controller;

import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.skillbridge.user_service.dto.ProfileResponse;
import com.skillbridge.user_service.dto.UpdateProfileRequest;
import com.skillbridge.user_service.security.JwtUserPrincipal;
import com.skillbridge.user_service.service.UserProfileService;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/users")
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping("/me")
    public ProfileResponse getMyProfile(Authentication authentication) {
        return userProfileService.getMyProfile(extractPrincipal(authentication));
    }

    @PutMapping("/me")
    public ProfileResponse upsertMyProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            Authentication authentication
    ) {
        return userProfileService.upsertMyProfile(extractPrincipal(authentication), request);
    }

    private JwtUserPrincipal extractPrincipal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtUserPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal;
    }
}
