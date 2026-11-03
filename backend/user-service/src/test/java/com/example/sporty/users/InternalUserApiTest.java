package com.example.sporty.users;

import com.example.sporty.features.commons.config.SecurityConfig;
import com.example.sporty.features.commons.handler.GlobalExceptionHandler;
import com.example.sporty.features.users.controller.InternalUserController;
import com.example.sporty.features.users.controller.InternalUserExceptionHandler;
import com.example.sporty.features.users.domain.entity.UserEntity;
import com.example.sporty.features.users.repository.UserRepository;
import com.example.sporty.features.profiles.repository.ProfileRepository;
import com.example.sporty.features.users.service.InternalUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InternalUserController.class)
@Import({
        SecurityConfig.class,
        InternalUserService.class,
        InternalUserExceptionHandler.class,
        GlobalExceptionHandler.class
})
class InternalUserApiTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    UserRepository userRepository;

    @MockitoBean
    ProfileRepository profileRepository;

    @Test
    void existingUserWithoutProfileReturns500() throws Exception {
        var user = UserEntity.builder().id(2L).build();

        when(userRepository.findAllById(List.of(2L)))
                .thenReturn(List.of(user));
        when(profileRepository.findAllByUser_IdIn(List.of(2L)))
                .thenReturn(List.of());

        mvc.perform(get("/internal/users/profiles")
                        .param("userIds", "2"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message")
                        .value("회원 프로필 데이터 무결성 오류입니다."));
    }

    @Test
    void nonexistentUsersAreExcluded() throws Exception {
        when(userRepository.findAllById(List.of(999L)))
                .thenReturn(List.of());
        when(profileRepository.findAllByUser_IdIn(List.of(999L)))
                .thenReturn(List.of());

        mvc.perform(get("/internal/users/profiles")
                        .param("userIds", "999"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void emptyIdsReturnEmptyListWithoutDatabaseQueries() throws Exception {
        mvc.perform(get("/internal/users/profiles")
                        .param("userIds", ""))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verifyNoInteractions(userRepository, profileRepository);
    }

    @Test
    void nonexistentUserStatusReturns404() throws Exception {
        when(userRepository.findById(999L))
                .thenReturn(Optional.empty());

        mvc.perform(get("/internal/users/999/status"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }
}