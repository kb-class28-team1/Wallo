package com.wallo.auth.service;

import com.wallo.auth.dto.request.LoginRequest;
import com.wallo.auth.dto.request.SignupRequest;
import com.wallo.auth.dto.response.AuthUserResponse;

public interface AuthService {

    AuthUserResponse signup(SignupRequest request);

    AuthUserResponse login(LoginRequest request);

    AuthUserResponse getCurrentUser(Long userId);
}
