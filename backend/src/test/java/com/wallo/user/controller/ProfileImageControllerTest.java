package com.wallo.user.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wallo.auth.CurrentUserProvider;
import com.wallo.auth.UnauthenticatedException;
import com.wallo.common.exception.GlobalExceptionHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProfileImageControllerTest {

    @TempDir
    Path storageDirectory;

    private CurrentUserProvider currentUserProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        currentUserProvider = mock(CurrentUserProvider.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ProfileImageController(
                        currentUserProvider,
                        storageDirectory.toString()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        when(currentUserProvider.getCurrentUserId()).thenReturn(7L);
    }

    @Test
    void servesImageToAuthenticatedUser() throws Exception {
        byte[] imageBytes = new byte[]{1, 2, 3, 4};
        Files.write(storageDirectory.resolve("profile.png"), imageBytes);

        byte[] responseBytes = mockMvc.perform(get("/api/profile-images/profile.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertArrayEquals(imageBytes, responseBytes);
        verify(currentUserProvider).getCurrentUserId();
    }

    @Test
    void rejectsImageRequestWithoutLoginSession() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenThrow(new UnauthenticatedException());

        mockMvc.perform(get("/api/profile-images/profile.png"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_001"));
    }

    @Test
    void returnsNotFoundWhenImageDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/profile-images/missing.png"))
                .andExpect(status().isNotFound());
    }
}
