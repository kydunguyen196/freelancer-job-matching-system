package com.skillbridge.user_service.controller;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import com.skillbridge.user_service.dto.ProfileMediaResponse;
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

    @PostMapping("/me/cv")
    public ProfileResponse uploadMyResume(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        return userProfileService.uploadMyResume(extractPrincipal(authentication), file);
    }

    @GetMapping("/me/media")
    public ProfileMediaResponse getMyMedia(Authentication authentication) {
        return userProfileService.getMyMedia(extractPrincipal(authentication));
    }

    @PostMapping("/me/avatar")
    public ProfileMediaResponse uploadAvatar(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        return userProfileService.uploadAvatar(extractPrincipal(authentication), file);
    }

    @DeleteMapping("/me/avatar")
    public ProfileMediaResponse deleteAvatar(Authentication authentication) {
        return userProfileService.deleteAvatar(extractPrincipal(authentication));
    }

    @GetMapping("/me/media/avatar/download")
    public ResponseEntity<ByteArrayResource> downloadAvatar(Authentication authentication) {
        UserProfileService.DownloadedMedia media = userProfileService.downloadAvatar(extractPrincipal(authentication));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + sanitizeFilename(media.fileName()) + "\"")
                .body(new ByteArrayResource(media.content()));
    }

    @PostMapping("/me/company-logo")
    public ProfileMediaResponse uploadCompanyLogo(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        return userProfileService.uploadCompanyLogo(extractPrincipal(authentication), file);
    }

    @DeleteMapping("/me/company-logo")
    public ProfileMediaResponse deleteCompanyLogo(Authentication authentication) {
        return userProfileService.deleteCompanyLogo(extractPrincipal(authentication));
    }

    @GetMapping("/me/media/company-logo/download")
    public ResponseEntity<ByteArrayResource> downloadCompanyLogo(Authentication authentication) {
        UserProfileService.DownloadedMedia media = userProfileService.downloadCompanyLogo(extractPrincipal(authentication));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + sanitizeFilename(media.fileName()) + "\"")
                .body(new ByteArrayResource(media.content()));
    }

    private JwtUserPrincipal extractPrincipal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtUserPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal;
    }

    private String sanitizeFilename(String fileName) {
        if (fileName == null) {
            return "download.bin";
        }
        return fileName.replace("\"", "_").replace("\r", "_").replace("\n", "_");
    }
}
