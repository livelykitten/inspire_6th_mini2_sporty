package com.example.sporty.users;

import com.example.sporty.features.commons.config.SecurityConfig;
import com.example.sporty.features.commons.exception.users.PasswordMismatchException;
import com.example.sporty.features.commons.exception.users.UserAlreadyWithdrawnException;
import com.example.sporty.features.commons.exception.users.WithdrawalAlreadyPendingException;
import com.example.sporty.features.commons.handler.GlobalExceptionHandler;
import com.example.sporty.features.users.controller.UserController;
import com.example.sporty.features.users.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class
})
class WithdrawalResponseTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    UserService userService;

    @Test
    void acceptedRequestReturns202WithoutBody() throws Exception {
        mvc.perform(delete("/api/users/me")
                        .header("X-User-Id", "2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"Test1234!"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));

        verify(userService).withdrawal("Test1234!");
    }

    @Test
    void pendingRequestReturns409() throws Exception {
        doThrow(new WithdrawalAlreadyPendingException())
                .when(userService).withdrawal("Test1234!");

        mvc.perform(delete("/api/users/me")
                        .header("X-User-Id", "2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"Test1234!"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("WITHDRAWAL_ALREADY_PENDING"))
                .andExpect(jsonPath("$.message")
                        .value("회원 탈퇴가 이미 진행 중입니다."));
    }

    @Test
    void withdrawnRequestReturns401() throws Exception {
        doThrow(new UserAlreadyWithdrawnException())
                .when(userService).withdrawal("Test1234!");

        mvc.perform(delete("/api/users/me")
                        .header("X-User-Id", "2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"Test1234!"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("USER_ALREADY_WITHDRAWN"))
                .andExpect(jsonPath("$.message")
                        .value("이미 탈퇴한 회원입니다."));
    }

    @Test
    void wrongPasswordReturns400() throws Exception {
        doThrow(new PasswordMismatchException())
                .when(userService).withdrawal("wrong-password");

        mvc.perform(delete("/api/users/me")
                        .header("X-User-Id", "2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"wrong-password"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("PASSWORD_MISMATCH"));
    }

    @Test
    void missingAuthenticationReturns401WithoutCallingService()
            throws Exception {
        mvc.perform(delete("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"Test1234!"}
                                """))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }
}