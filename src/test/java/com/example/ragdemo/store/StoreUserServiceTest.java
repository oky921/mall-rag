package com.example.ragdemo.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class StoreUserServiceTest {

    @Mock
    private StoreUserRepository repository;

    @Mock
    private CurrentStoreUser currentUser;

    private BCryptPasswordEncoder encoder;
    private StoreUserService service;

    @BeforeEach
    void setUp() {
        encoder = new BCryptPasswordEncoder();
        service = new StoreUserService(repository, encoder, currentUser);
    }

    @Test
    void registersNormalizedUsernameWithBcryptPassword() {
        when(repository.existsByUsernameIgnoreCase("alice_01")).thenReturn(false);
        when(repository.saveAndFlush(any(StoreUser.class))).thenAnswer(invocation -> {
            StoreUser user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 7L);
            return user;
        });

        StoreUser user = service.register(new StoreApiModels.RegisterRequest(
                " Alice_01 ", "StrongPass8", "爱丽丝"));

        assertThat(user.getUsername()).isEqualTo("alice_01");
        assertThat(user.getPasswordHash()).isNotEqualTo("StrongPass8");
        assertThat(encoder.matches("StrongPass8", user.getPasswordHash())).isTrue();
    }

    @Test
    void rejectsDuplicateUsername() {
        when(repository.existsByUsernameIgnoreCase("alice")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new StoreApiModels.RegisterRequest(
                "alice", "StrongPass8", "爱丽丝")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void returnsOnlyCurrentUserProfile() {
        StoreUser user = new StoreUser("alice", encoder.encode("StrongPass8"), "爱丽丝");
        ReflectionTestUtils.setField(user, "id", 7L);
        when(currentUser.userId()).thenReturn(7L);
        when(repository.findById(7L)).thenReturn(Optional.of(user));

        StoreApiModels.UserResponse response = service.current();

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.username()).isEqualTo("alice");
    }
}
