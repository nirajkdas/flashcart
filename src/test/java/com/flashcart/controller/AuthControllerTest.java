package com.flashcart.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashcart.dto.request.LoginRequest;
import com.flashcart.dto.request.RegisterRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private static final String USERNAME = "testuser_" + System.currentTimeMillis();
    private static final String EMAIL    = USERNAME + "@test.com";
    private static final String PASSWORD = "Test@1234";

    @Test
    @Order(1)
    @DisplayName("POST /api/auth/register → 201 with JWT")
    void register_success() throws Exception {
        RegisterRequest req = RegisterRequest.builder()
                .username(USERNAME).email(EMAIL)
                .password(PASSWORD).fullName("Test User").build();

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.username").value(USERNAME));
    }

    @Test
    @Order(2)
    @DisplayName("POST /api/auth/register with duplicate username → 409")
    void register_duplicateUsername() throws Exception {
        RegisterRequest req = RegisterRequest.builder()
                .username(USERNAME).email("other@test.com")
                .password(PASSWORD).fullName("Other User").build();

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(3)
    @DisplayName("POST /api/auth/login with valid credentials → 200 with JWT")
    void login_success() throws Exception {
        LoginRequest req = new LoginRequest(USERNAME, PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.role").value("CUSTOMER"));
    }

    @Test
    @Order(4)
    @DisplayName("POST /api/auth/login with wrong password → 401")
    void login_badPassword() throws Exception {
        LoginRequest req = new LoginRequest(USERNAME, "WrongPass1");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/register with weak password → 400 validation error")
    void register_weakPassword() throws Exception {
        RegisterRequest req = RegisterRequest.builder()
                .username("newuser123").email("new@test.com")
                .password("weak").fullName("New User").build();

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.password").exists());
    }
}
