package com.wallo.user.controller;

import com.wallo.auth.CurrentUserProvider;
import com.wallo.common.response.CommonResponse;
import com.wallo.user.dto.NicknameDto;
import com.wallo.user.dto.PasswordDto;
import com.wallo.user.dto.ProfileImageDto;
import com.wallo.user.dto.UserProfileDto;
import com.wallo.user.service.UserProfileService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users/profile")
public class UserProfileController {

    private final UserProfileService userProfileService;
    private final CurrentUserProvider currentUserProvider;

    public UserProfileController(
            UserProfileService userProfileService,
            CurrentUserProvider currentUserProvider
    ) {
        this.userProfileService = userProfileService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public CommonResponse<UserProfileDto.Response> getProfile() {
        return CommonResponse.success(
                userProfileService.getProfile(currentUserProvider.getCurrentUserId())
        );
    }

    @PatchMapping("/nickname")
    public CommonResponse<NicknameDto.Response> updateNickname(
            @RequestBody(required = false) NicknameDto.UpdateRequest request
    ) {
        return CommonResponse.success(
                userProfileService.updateNickname(
                        currentUserProvider.getCurrentUserId(),
                        request
                )
        );
    }

    @PatchMapping("/password")
    public CommonResponse<Void> changePassword(
            @RequestBody(required = false) PasswordDto.ChangeRequest request
    ) {
        userProfileService.changePassword(
                currentUserProvider.getCurrentUserId(),
                request
        );
        return CommonResponse.success(null);
    }

    @PatchMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResponse<ProfileImageDto.Response> updateProfileImage(
            @RequestParam("image") MultipartFile image
    ) {
        return CommonResponse.success(
                userProfileService.updateProfileImage(
                        currentUserProvider.getCurrentUserId(),
                        image
                )
        );
    }

    @DeleteMapping("/image")
    public CommonResponse<ProfileImageDto.Response> resetProfileImage() {
        return CommonResponse.success(
                userProfileService.resetProfileImage(currentUserProvider.getCurrentUserId())
        );
    }
}
