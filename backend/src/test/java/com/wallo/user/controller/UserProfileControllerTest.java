package com.wallo.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wallo.auth.CurrentUserProvider;
import com.wallo.auth.UnauthenticatedException;
import com.wallo.common.exception.GlobalExceptionHandler;
import com.wallo.user.dto.NicknameDto;
import com.wallo.user.dto.ProfileImageDto;
import com.wallo.user.dto.UserProfileDto;
import com.wallo.user.exception.UserExceptionHandler;
import com.wallo.user.service.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class UserProfileControllerTest {

    private UserProfileService userProfileService;
    private CurrentUserProvider currentUserProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userProfileService = mock(UserProfileService.class);
        currentUserProvider = mock(CurrentUserProvider.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new UserProfileController(userProfileService, currentUserProvider))
                .setControllerAdvice(new UserExceptionHandler(), new GlobalExceptionHandler())
                .build();

        when(currentUserProvider.getCurrentUserId()).thenReturn(7L);
    }

    @Test
    void returnsProfileForCurrentUser() throws Exception {
        when(userProfileService.getProfile(7L)).thenReturn(
                new UserProfileDto.Response(
                        7L,
                        "김혜진",
                        "저축왕 펭귄",
                        "user@wallo.test",
                        "/api/profile-images/profile.png"
                )
        );

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/users/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.name").value("김혜진"))
                .andExpect(jsonPath("$.data.nickname").value("저축왕 펭귄"))
                .andExpect(jsonPath("$.data.email").value("user@wallo.test"))
                .andExpect(jsonPath("$.data.profileImageUrl")
                        .value("/api/profile-images/profile.png"));

        verify(userProfileService).getProfile(7L);
    }

    @Test
    void updatesNicknameForCurrentUser() throws Exception {
        when(userProfileService.updateNickname(eq(7L), any(NicknameDto.UpdateRequest.class)))
                .thenReturn(new NicknameDto.Response("새 닉네임"));

        mockMvc.perform(patch("/api/users/profile/nickname")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"새 닉네임\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nickname").value("새 닉네임"));

        verify(userProfileService).updateNickname(eq(7L), any(NicknameDto.UpdateRequest.class));
    }

    @Test
    void changesPasswordForCurrentUser() throws Exception {
        doNothing().when(userProfileService)
                .changePassword(eq(7L), any(com.wallo.user.dto.PasswordDto.ChangeRequest.class));

        mockMvc.perform(patch("/api/users/profile/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "current123!",
                                  "newPassword": "newPassword123!",
                                  "newPasswordConfirm": "newPassword123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(userProfileService).changePassword(
                eq(7L),
                any(com.wallo.user.dto.PasswordDto.ChangeRequest.class));
    }

    @Test
    void updatesProfileImageForCurrentUser() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "profile.png",
                MediaType.IMAGE_PNG_VALUE,
                new byte[]{1, 2, 3}
        );
        when(userProfileService.updateProfileImage(eq(7L), any()))
                .thenReturn(new ProfileImageDto.Response("/api/profile-images/profile.png"));

        mockMvc.perform(multipart("/api/users/profile/image")
                        .file(image)
                        .with(request -> {
                            request.setMethod("PATCH");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.profileImageUrl")
                        .value("/api/profile-images/profile.png"));

        verify(userProfileService).updateProfileImage(eq(7L), any());
    }

    @Test
    void resetsProfileImageForCurrentUser() throws Exception {
        when(userProfileService.resetProfileImage(7L))
                .thenReturn(new ProfileImageDto.Response("/images/profiles/default-profile.svg"));

        mockMvc.perform(delete("/api/users/profile/image"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.profileImageUrl")
                        .value("/images/profiles/default-profile.svg"));

        verify(userProfileService).resetProfileImage(7L);
    }

    @Test
    void rejectsProfileUpdateWhenLoginSessionIsMissing() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenThrow(new UnauthenticatedException());

        mockMvc.perform(patch("/api/users/profile/nickname")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"새 닉네임\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_001"));
    }
}
