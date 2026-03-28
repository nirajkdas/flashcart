package com.flashcart.service;

import com.flashcart.dto.request.LoginRequest;
import com.flashcart.dto.request.RegisterRequest;
import com.flashcart.dto.response.AuthResponse;
import com.flashcart.dto.response.UserResponse;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    UserResponse getProfile(String username);
}
