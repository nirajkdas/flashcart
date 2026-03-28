package com.flashcart.service.impl;

import com.flashcart.dto.request.LoginRequest;
import com.flashcart.dto.request.RegisterRequest;
import com.flashcart.dto.response.AuthResponse;
import com.flashcart.dto.response.UserResponse;
import com.flashcart.entity.User;
import com.flashcart.exception.ConflictException;
import com.flashcart.exception.ResourceNotFoundException;
import com.flashcart.repository.UserRepository;
import com.flashcart.security.jwt.JwtUtils;
import com.flashcart.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authManager;
    private final JwtUtils jwtUtils;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByUsername(req.getUsername()))
            throw new ConflictException("Username already taken: " + req.getUsername());
        if (userRepository.existsByEmail(req.getEmail()))
            throw new ConflictException("Email already registered: " + req.getEmail());

        User user = User.builder()
                .username(req.getUsername())
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .fullName(req.getFullName())
                .role(User.Role.CUSTOMER)
                .build();

        user = userRepository.save(user);

        UserDetails details = org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername()).password(user.getPassword())
                .authorities("ROLE_" + user.getRole().name()).build();

        return AuthResponse.builder()
                .token(jwtUtils.generateToken(details))
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }

    @Override
    public AuthResponse login(LoginRequest req) {
        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));

        UserDetails details = (UserDetails) auth.getPrincipal();
        User user = userRepository.findByUsername(details.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User", 0L));

        return AuthResponse.builder()
                .token(jwtUtils.generateToken(details))
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        return toResponse(user);
    }

    private UserResponse toResponse(User u) {
        return UserResponse.builder()
                .id(u.getId()).username(u.getUsername()).email(u.getEmail())
                .fullName(u.getFullName()).role(u.getRole().name())
                .isActive(u.getIsActive()).createdAt(u.getCreatedAt())
                .build();
    }
}
