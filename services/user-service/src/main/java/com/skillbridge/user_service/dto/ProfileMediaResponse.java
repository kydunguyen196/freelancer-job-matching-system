package com.skillbridge.user_service.dto;

public record ProfileMediaResponse(
        Long userId,
        ProfileMediaAssetResponse avatar,
        ProfileMediaAssetResponse companyLogo
) {
}
