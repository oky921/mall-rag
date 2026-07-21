package com.example.ragdemo.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class StoreAddressServiceTest {

    @Mock
    private StoreAddressRepository repository;

    @Mock
    private CurrentStoreUser currentUser;

    private StoreAddressService service;
    private StoreApiModels.SaveAddressRequest request;

    @BeforeEach
    void setUp() {
        service = new StoreAddressService(repository, currentUser);
        request = new StoreApiModels.SaveAddressRequest("张三", "13800000000", "上海市",
                "上海市", "浦东新区", "世纪大道1号", "200120", false);
        when(currentUser.userId()).thenReturn(1L);
    }

    @Test
    void firstAddressBecomesDefault() {
        when(repository.existsByUserId(1L)).thenReturn(false);
        when(repository.findByUserIdOrderByDefaultAddressDescUpdatedAtDesc(1L)).thenReturn(List.of());
        when(repository.save(any(StoreAddress.class))).thenAnswer(invocation -> {
            StoreAddress address = invocation.getArgument(0);
            ReflectionTestUtils.setField(address, "id", 11L);
            return address;
        });

        StoreApiModels.AddressResponse response = service.create(request);

        assertThat(response.defaultAddress()).isTrue();
        assertThat(response.fullAddress()).isEqualTo("上海市 上海市 浦东新区 世纪大道1号");
    }

    @Test
    void cannotReadAnotherUsersAddress() {
        when(repository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ownedAddress(99L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void makingDefaultClearsPreviousDefault() {
        StoreAddress first = new StoreAddress(1L, request, true);
        StoreAddress second = new StoreAddress(1L, request, false);
        ReflectionTestUtils.setField(first, "id", 11L);
        ReflectionTestUtils.setField(second, "id", 12L);
        when(repository.findByIdAndUserId(12L, 1L)).thenReturn(Optional.of(second));
        when(repository.findByUserIdOrderByDefaultAddressDescUpdatedAtDesc(1L))
                .thenReturn(List.of(first, second));

        service.makeDefault(12L);

        assertThat(first.getDefaultAddress()).isFalse();
        assertThat(second.getDefaultAddress()).isTrue();
    }
}
