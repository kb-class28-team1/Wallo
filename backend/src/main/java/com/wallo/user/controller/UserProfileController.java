package com.wallo.user.controller;

import com.wallo.auth.CurrentUserProvider;
import com.wallo.common.response.CommonResponse;
import com.wallo.user.dto.NicknameDto;
import com.wallo.user.service.UserProfileService;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
